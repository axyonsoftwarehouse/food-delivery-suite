package com.foodie.api.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageServiceTest {
    @TempDir
    Path directory;

    private final FileRepository repository = mock(FileRepository.class);
    private final User uploader = new User(3, "Dono", "dono@demo.local", "restaurant", 1L);

    private StorageService service(String driver, long maxImage, long maxDocument) {
        return new StorageService(repository, List.of(new LocalStorageProvider(directory.toString())),
            driver, maxImage, maxDocument, "http://api.local/");
    }

    @Test
    void uploadsImageAndBuildsUrl() {
        when(repository.insert(any(), any(), any(), any(), anyLong(), any(), any(), any())).thenReturn(7L);
        StorageService.StoredFile stored = service("local", 1_000_000, 2_000_000)
            .upload(uploader, "product", "foto.png", "image/png", new byte[]{1, 2, 3});
        assertThat(stored.id()).isEqualTo(7L);
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.url()).isEqualTo("http://api.local/files/7");
    }

    @Test
    void rejectsDisallowedType() {
        assertThatThrownBy(() -> service("local", 1_000_000, 2_000_000)
            .upload(uploader, "product", "malware.exe", "application/x-msdownload", new byte[]{1}))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(400));
    }

    @Test
    void rejectsOversizedImage() {
        assertThatThrownBy(() -> service("local", 2, 10)
            .upload(uploader, "product", "grande.png", "image/png", new byte[]{1, 2, 3, 4}))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(400));
    }

    @Test
    void unknownDriverIsUnavailable() {
        assertThatThrownBy(() -> service("s3", 1_000_000, 2_000_000)
            .upload(uploader, "product", "foto.png", "image/png", new byte[]{1}))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(503));
    }

    @Test
    void onlyOwnerOrAdminCanDelete() {
        FileRepository.FileRecord record = new FileRepository.FileRecord(9, "local", "x.png", "x.png", "image/png", 3, "abc", 3L, "product", "2026-09-27T10:00:00Z");
        when(repository.find(9)).thenReturn(Optional.of(record));

        User other = new User(4, "Outro", "outro@demo.local", "restaurant", 1L);
        assertThatThrownBy(() -> service("local", 1_000_000, 2_000_000).delete(other, 9))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(403));
    }
}
