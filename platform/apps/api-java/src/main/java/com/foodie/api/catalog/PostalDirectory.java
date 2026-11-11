package com.foodie.api.catalog;

import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Descobre cidade, UF e bairro de um CEP. Consulta o ViaCEP e, se ele falhar, a BrasilAPI (ambos
 * gratuitos e sem chave). Não guarda nada: o cache fica em {@link PostalCoverageService}.
 */
@Service
public class PostalDirectory {
    /** Resultado da consulta: achou, o CEP não existe, ou nenhum dos serviços respondeu. */
    public sealed interface Lookup permits Found, NotFound, Unavailable {}
    public record Found(String city, String state, String neighborhood) implements Lookup {}
    public record NotFound() implements Lookup {}
    public record Unavailable() implements Lookup {}

    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {};

    private final RestClient viaCep;
    private final RestClient brasilApi;

    @Autowired
    public PostalDirectory(@Value("${app.postal.viacep-url:https://viacep.com.br}") String viaCepUrl,
                           @Value("${app.postal.brasilapi-url:https://brasilapi.com.br}") String brasilApiUrl) {
        this(client(viaCepUrl), client(brasilApiUrl));
    }

    PostalDirectory(RestClient viaCep, RestClient brasilApi) {
        this.viaCep = viaCep;
        this.brasilApi = brasilApi;
    }

    private static RestClient client(String baseUrl) {
        // Prazo curto: o cliente está esperando na tela do CEP.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(4));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** {@code postalCode} já normalizado, com 8 dígitos. */
    public Lookup lookup(String postalCode) {
        Lookup first = fromViaCep(postalCode);
        if (first instanceof Found) return first;
        Lookup second = fromBrasilApi(postalCode);
        if (second instanceof Found) return second;
        // Um dos dois disse que o CEP não existe e o outro não contradisse: não existe.
        if (first instanceof NotFound || second instanceof NotFound) return new NotFound();
        return new Unavailable();
    }

    private Lookup fromViaCep(String postalCode) {
        try {
            Map<String, Object> body = viaCep.get().uri("/ws/{cep}/json/", postalCode).retrieve().body(JSON);
            if (body == null) return new Unavailable();
            Object error = body.get("erro");
            if (Boolean.TRUE.equals(error) || "true".equals(String.valueOf(error))) return new NotFound();
            return found(body.get("localidade"), body.get("uf"), body.get("bairro"));
        } catch (HttpClientErrorException.BadRequest | HttpClientErrorException.NotFound error) {
            return new NotFound();
        } catch (RuntimeException error) {
            return new Unavailable();
        }
    }

    private Lookup fromBrasilApi(String postalCode) {
        try {
            Map<String, Object> body = brasilApi.get().uri("/api/cep/v1/{cep}", postalCode).retrieve().body(JSON);
            if (body == null) return new Unavailable();
            return found(body.get("city"), body.get("state"), body.get("neighborhood"));
        } catch (HttpClientErrorException.NotFound error) {
            return new NotFound();
        } catch (RuntimeException error) {
            return new Unavailable();
        }
    }

    private static Lookup found(Object city, Object state, Object neighborhood) {
        String cityText = text(city);
        String stateText = text(state).toUpperCase(java.util.Locale.ROOT);
        if (cityText.isEmpty() || !stateText.matches("[A-Z]{2}")) return new Unavailable();
        return new Found(cityText, stateText, text(neighborhood));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).replaceAll("\\s+", " ").trim();
    }
}
