package com.foodie.api.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodie.api.ApiException;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

class LocalStorageProviderTest {
    @TempDir
    Path directory;

    @Test
    void storesLoadsAndDeletes() throws Exception {
        LocalStorageProvider provider = new LocalStorageProvider(directory.toString());
        byte[] data = "conteúdo".getBytes();

        String path = provider.store(new ByteArrayInputStream(data), ".png");
        assertThat(path).endsWith(".png");

        Resource resource = provider.load(path);
        assertThat(resource.exists()).isTrue();
        assertThat(resource.getInputStream().readAllBytes()).isEqualTo(data);

        provider.delete(path);
        assertThatThrownBy(() -> provider.load(path)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsPathTraversal() {
        LocalStorageProvider provider = new LocalStorageProvider(directory.toString());
        assertThatThrownBy(() -> provider.load("../secret")).isInstanceOf(ApiException.class);
    }
}
