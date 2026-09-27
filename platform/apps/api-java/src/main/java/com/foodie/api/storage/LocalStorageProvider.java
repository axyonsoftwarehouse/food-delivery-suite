package com.foodie.api.storage;

import com.foodie.api.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** Armazenamento local em disco, com nomes opacos e contenção dentro do diretório raiz. */
@Component
public class LocalStorageProvider implements StorageProvider {
    private final Path root;

    public LocalStorageProvider(@Value("${app.storage.local-dir:./data/uploads}") String directory) {
        this.root = Paths.get(directory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException error) {
            throw new IllegalStateException("Não foi possível preparar o diretório de uploads", error);
        }
    }

    @Override
    public String name() {
        return "local";
    }

    @Override
    public String store(InputStream data, String extension) throws IOException {
        String filename = UUID.randomUUID().toString().replace("-", "") + extension;
        Path target = resolve(filename);
        try (InputStream input = data) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return filename;
    }

    @Override
    public void delete(String path) {
        try {
            Files.deleteIfExists(resolve(path));
        } catch (IOException ignored) {
            // remover é best-effort
        }
    }

    @Override
    public Resource load(String path) {
        Resource resource = new FileSystemResource(resolve(path));
        if (!resource.exists() || !resource.isReadable()) throw new ApiException(404, "Arquivo não encontrado");
        return resource;
    }

    private Path resolve(String path) {
        Path target = root.resolve(path).normalize();
        if (!target.startsWith(root)) throw new ApiException(404, "Arquivo não encontrado");
        return target;
    }
}
