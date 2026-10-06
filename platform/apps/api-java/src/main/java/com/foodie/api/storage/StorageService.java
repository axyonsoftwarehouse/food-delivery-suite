package com.foodie.api.storage;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class StorageService {
    private static final Map<String, String> EXTENSIONS = Map.of(
        "image/jpeg", ".jpg",
        "image/png", ".png",
        "image/webp", ".webp",
        "image/gif", ".gif",
        "application/pdf", ".pdf"
    );
    static final Set<String> PURPOSES = Set.of("product", "other");
    static final Set<String> PUBLIC_PURPOSES = Set.of("product");
    private static final Set<String> CATALOG_ROLES = Set.of("restaurant", "kitchen", "admin");
    static final int DAILY_UPLOAD_LIMIT = 100;

    private final FileRepository repository;
    private final Map<String, StorageProvider> providers = new HashMap<>();
    private final String driver;
    private final long maxImageBytes;
    private final long maxDocumentBytes;
    private final String publicBaseUrl;

    public StorageService(FileRepository repository,
                          List<StorageProvider> availableProviders,
                          @Value("${app.storage.driver:local}") String driver,
                          @Value("${app.storage.max-image-bytes:5242880}") long maxImageBytes,
                          @Value("${app.storage.max-document-bytes:10485760}") long maxDocumentBytes,
                          @Value("${app.storage.public-base-url:http://127.0.0.1:4001}") String publicBaseUrl) {
        this.repository = repository;
        for (StorageProvider provider : availableProviders) providers.put(provider.name(), provider);
        this.driver = driver == null ? "local" : driver.strip().toLowerCase(Locale.ROOT);
        this.maxImageBytes = maxImageBytes;
        this.maxDocumentBytes = maxDocumentBytes;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.strip().replaceAll("/+$", "");
    }

    public StoredFile upload(User uploader, String purpose, String originalName, String contentType, byte[] data) {
        if (data == null || data.length == 0) throw new ApiException(400, "Arquivo vazio");
        String normalizedType = contentType == null ? "" : contentType.split(";")[0].strip().toLowerCase(Locale.ROOT);
        String extension = EXTENSIONS.get(normalizedType);
        if (extension == null) throw new ApiException(400, "Tipo de arquivo não permitido");
        boolean image = normalizedType.startsWith("image/");
        String normalizedPurpose = purpose == null || purpose.isBlank() ? "other" : purpose.strip().toLowerCase(Locale.ROOT);
        if (!PURPOSES.contains(normalizedPurpose)) throw new ApiException(400, "Finalidade de arquivo inválida");
        if (PUBLIC_PURPOSES.contains(normalizedPurpose)) {
            // Arquivo público fica acessível a qualquer um pelo número: só imagem de catálogo, enviada por quem cuida dele.
            if (uploader == null || !CATALOG_ROLES.contains(uploader.role())) throw new ApiException(403, "Acesso não autorizado");
            if (!image) throw new ApiException(400, "Foto de produto deve ser uma imagem");
        }
        if (uploader != null && repository.countUploadedSince(uploader.id(), 24) >= DAILY_UPLOAD_LIMIT) {
            throw new ApiException(429, "Limite diário de envios atingido. Tente novamente amanhã");
        }
        long limit = image ? maxImageBytes : maxDocumentBytes;
        if (data.length > limit) {
            throw new ApiException(400, "Arquivo maior que o limite de " + (limit / (1024 * 1024)) + " MB");
        }
        StorageProvider provider = provider();
        String path;
        try {
            path = provider.store(new ByteArrayInputStream(data), extension);
        } catch (IOException error) {
            throw new ApiException(500, "Não foi possível gravar o arquivo");
        }
        long id = repository.insert(provider.name(), path, originalName, normalizedType, data.length, sha256(data),
            uploader == null ? null : uploader.id(), normalizedPurpose);
        return new StoredFile(id, url(id), normalizedType, data.length);
    }

    /** Foto de catálogo: pública (o cardápio é aberto). Qualquer outro arquivo é privado. */
    public static boolean isPublic(FileRepository.FileRecord file) {
        return PUBLIC_PURPOSES.contains(file.purpose()) && file.contentType() != null && file.contentType().startsWith("image/");
    }

    /**
     * Arquivo privado só para quem enviou ou admin. Os demais recebem 404, igual a um número inexistente,
     * para que percorrer os ids não revele quais arquivos existem.
     */
    public FileRepository.FileRecord readable(User viewer, long id) {
        FileRepository.FileRecord file = record(id);
        if (isPublic(file)) return file;
        boolean owner = viewer != null && file.uploadedBy() != null && file.uploadedBy() == viewer.id();
        boolean admin = viewer != null && "admin".equals(viewer.role());
        if (!owner && !admin) throw new ApiException(404, "Arquivo não encontrado");
        return file;
    }

    public FileRepository.FileRecord record(long id) {
        return repository.find(id).orElseThrow(() -> new ApiException(404, "Arquivo não encontrado"));
    }

    public Resource load(long id) {
        FileRepository.FileRecord file = record(id);
        return provider().load(file.path());
    }

    public void delete(User actor, long id) {
        FileRepository.FileRecord file = record(id);
        boolean owner = file.uploadedBy() != null && file.uploadedBy() == actor.id();
        if (!"admin".equals(actor.role()) && !owner) throw new ApiException(403, "Acesso não autorizado");
        provider().delete(file.path());
        repository.delete(id);
    }

    public String url(long id) {
        return publicBaseUrl + "/files/" + id;
    }

    private StorageProvider provider() {
        StorageProvider provider = providers.get(driver);
        if (provider == null) throw new ApiException(503, "Armazenamento \"" + driver + "\" não configurado");
        return provider;
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) hex.append(Character.forDigit((value >> 4) & 0xF, 16)).append(Character.forDigit(value & 0xF, 16));
            return hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 indisponível", error);
        }
    }

    public record StoredFile(long id, String url, String contentType, long byteSize) {}
}
