package com.foodie.api.storage;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class FileController {
    private final AuthService auth;
    private final StorageService storage;

    public FileController(AuthService auth, StorageService storage) {
        this.auth = auth;
        this.storage = storage;
    }

    @PostMapping("/files")
    public ResponseEntity<StorageService.StoredFile> upload(@CookieValue(value = "foodie_session", required = false) String token,
                                                            @RequestParam("file") MultipartFile file,
                                                            @RequestParam(value = "purpose", required = false) String purpose) {
        User user = auth.requireUser(token);
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException error) {
            throw new ApiException(400, "Não foi possível ler o arquivo enviado");
        }
        StorageService.StoredFile stored = storage.upload(user, purpose, file.getOriginalFilename(), file.getContentType(), data);
        return ResponseEntity.status(201).body(stored);
    }

    @GetMapping("/files/{id}")
    public ResponseEntity<Resource> download(@PathVariable long id) {
        FileRepository.FileRecord file = storage.record(id);
        Resource resource = storage.load(id);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.contentType()))
            .cacheControl(CacheControl.maxAge(365, java.util.concurrent.TimeUnit.DAYS).cachePublic())
            .body(resource);
    }

    @DeleteMapping("/files/{id}")
    public Map<String, Boolean> delete(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable long id) {
        User user = auth.requireUser(token);
        storage.delete(user, id);
        return Map.of("ok", true);
    }
}
