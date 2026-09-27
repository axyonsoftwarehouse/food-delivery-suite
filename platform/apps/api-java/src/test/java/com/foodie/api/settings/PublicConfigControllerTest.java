package com.foodie.api.settings;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PublicConfigController.class)
class PublicConfigControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SettingsService settings;

    @Test
    void exposesPublicConfigWithoutAuth() throws Exception {
        when(settings.publicConfig()).thenReturn(Map.of(
            "business", Map.of("name", "Foodie"),
            "maintenance", Map.of("active", false, "message", "")));

        mvc.perform(get("/public/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.business.name").value("Foodie"))
            .andExpect(jsonPath("$.maintenance.active").value(false));
    }
}
