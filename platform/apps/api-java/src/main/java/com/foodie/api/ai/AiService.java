package com.foodie.api.ai;

import com.foodie.api.ApiException;
import com.foodie.api.settings.SettingsCatalog;
import com.foodie.api.settings.SettingsService;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Sugestão de título/descrição de prato com a OpenAI (E43). */
@Service
public class AiService {
    private final SettingsService settings;
    private final RestClient client;
    private final String model;

    public AiService(SettingsService settings,
                     @Value("${app.openai.base-url:https://api.openai.com}") String baseUrl,
                     @Value("${app.openai.model:gpt-4o-mini}") String model) {
        this.settings = settings;
        this.client = RestClient.builder().baseUrl(baseUrl).build();
        this.model = model;
    }

    @SuppressWarnings("unchecked")
    public String suggest(String name, String keywords) {
        String key = settings.text(SettingsCatalog.OPENAI_API_KEY);
        if (key.isBlank()) throw new ApiException(503, "OpenAI não configurada: defina a chave em Integrações");
        if (name == null || name.isBlank()) throw new ApiException(400, "Informe o nome do prato");
        String prompt = "Gere uma descrição curta e apetitosa (máx. 300 caracteres) para o prato \"" + name.strip() + "\""
            + (keywords == null || keywords.isBlank() ? "" : " com os atributos: " + keywords.strip())
            + ". Responda apenas com a descrição.";
        Map<String, Object> body = Map.of(
            "model", model,
            "messages", List.of(Map.of("role", "user", "content", prompt)),
            "max_tokens", 200,
            "temperature", 0.7);
        try {
            Map<String, Object> response = client.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + key)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response == null) throw new ApiException(502, "Resposta vazia da OpenAI");
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) throw new ApiException(502, "Sem sugestão gerada");
            Map<String, Object> message = (Map<String, Object>) choices.getFirst().get("message");
            Object content = message == null ? null : message.get("content");
            if (content == null) throw new ApiException(502, "Sem sugestão gerada");
            return String.valueOf(content).strip();
        } catch (RestClientResponseException error) {
            throw new ApiException(502, "Falha ao consultar a OpenAI");
        }
    }
}
