package com.foodie.api.settings;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Configuração pública consumida pelo site/app, sem autenticação. */
@RestController
public class PublicConfigController {
    private final SettingsService settings;

    public PublicConfigController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping("/public/config")
    public Map<String, Object> config() {
        return settings.publicConfig();
    }
}
