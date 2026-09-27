package com.foodie.api.storage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

@WebMvcTest(FileController.class)
class FileControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private StorageService storage;

    @Test
    void uploadRequiresLogin() throws Exception {
        when(auth.requireUser(null)).thenThrow(new ApiException(401, "Faça login para continuar"));
        mvc.perform(multipart("/files").file(new MockMultipartFile("file", "foto.png", "image/png", new byte[]{1, 2})))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadsFile() throws Exception {
        when(auth.requireUser("s")).thenReturn(new User(3, "Dono", "dono@demo.local", "restaurant", 1L));
        when(storage.upload(any(), eq("product"), eq("foto.png"), eq("image/png"), any()))
            .thenReturn(new StorageService.StoredFile(7, "http://api.local/files/7", "image/png", 2));

        mvc.perform(multipart("/files")
                .file(new MockMultipartFile("file", "foto.png", "image/png", new byte[]{1, 2}))
                .param("purpose", "product")
                .cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.url").value("http://api.local/files/7"));
    }

    @Test
    void servesFileWithoutAuth() throws Exception {
        when(storage.record(7)).thenReturn(new FileRepository.FileRecord(7, "local", "x.png", "x.png", "image/png", 2, "abc", null, "product", "2026-09-27T10:00:00Z"));
        when(storage.load(7)).thenReturn(new ByteArrayResource(new byte[]{1, 2}));

        mvc.perform(get("/files/7"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"));
    }
}
