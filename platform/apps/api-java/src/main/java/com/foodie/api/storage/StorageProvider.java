package com.foodie.api.storage;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.Resource;

/** Abstração de armazenamento de arquivos (E03). Implementações: local e, no futuro, S3. */
public interface StorageProvider {
    String name();

    /** Grava o conteúdo e devolve o caminho/chave gerenciado pelo provedor. */
    String store(InputStream data, String extension) throws IOException;

    void delete(String path);

    Resource load(String path);
}
