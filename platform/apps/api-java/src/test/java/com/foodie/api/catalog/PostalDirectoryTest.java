package com.foodie.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** CEP → cidade, UF e bairro, com o ViaCEP primeiro e a BrasilAPI como reserva. Sem rede. */
class PostalDirectoryTest {
    private MockRestServiceServer viaCepServer;
    private MockRestServiceServer brasilApiServer;
    private PostalDirectory directory;

    @BeforeEach
    void setUp() {
        RestClient.Builder viaCep = RestClient.builder().baseUrl("https://viacep.test");
        RestClient.Builder brasilApi = RestClient.builder().baseUrl("https://brasilapi.test");
        viaCepServer = MockRestServiceServer.bindTo(viaCep).build();
        brasilApiServer = MockRestServiceServer.bindTo(brasilApi).build();
        directory = new PostalDirectory(viaCep.build(), brasilApi.build());
    }

    @Test
    void viaCepAnswersWithTheNeighborhood() {
        viaCepServer.expect(requestTo("https://viacep.test/ws/60150160/json/"))
            .andRespond(withSuccess("{\"cep\":\"60150-160\",\"bairro\":\"Aldeota\",\"localidade\":\"Fortaleza\",\"uf\":\"ce\"}", MediaType.APPLICATION_JSON));

        assertThat(directory.lookup("60150160")).isEqualTo(new PostalDirectory.Found("Fortaleza", "CE", "Aldeota"));
        brasilApiServer.verify();
    }

    @Test
    void cityWideCepComesWithoutNeighborhood() {
        viaCepServer.expect(requestTo("https://viacep.test/ws/62900000/json/"))
            .andRespond(withSuccess("{\"cep\":\"62900-000\",\"bairro\":\"\",\"localidade\":\"Russas\",\"uf\":\"CE\"}", MediaType.APPLICATION_JSON));

        assertThat(directory.lookup("62900000")).isEqualTo(new PostalDirectory.Found("Russas", "CE", ""));
    }

    @Test
    void viaCepDownFallsBackToBrasilApi() {
        viaCepServer.expect(requestTo("https://viacep.test/ws/01001000/json/")).andRespond(withServerError());
        brasilApiServer.expect(requestTo("https://brasilapi.test/api/cep/v1/01001000"))
            .andRespond(withSuccess("{\"cep\":\"01001000\",\"state\":\"SP\",\"city\":\"São Paulo\",\"neighborhood\":\"Sé\"}", MediaType.APPLICATION_JSON));

        assertThat(directory.lookup("01001000")).isEqualTo(new PostalDirectory.Found("São Paulo", "SP", "Sé"));
    }

    @Test
    void unknownCepIsNotFound() {
        viaCepServer.expect(requestTo("https://viacep.test/ws/99999999/json/"))
            .andRespond(withSuccess("{\"erro\": true}", MediaType.APPLICATION_JSON));
        brasilApiServer.expect(requestTo("https://brasilapi.test/api/cep/v1/99999999")).andRespond(withResourceNotFound());

        assertThat(directory.lookup("99999999")).isInstanceOf(PostalDirectory.NotFound.class);
    }

    @Test
    void bothServicesDownIsUnavailable() {
        viaCepServer.expect(requestTo("https://viacep.test/ws/60150160/json/")).andRespond(withServerError());
        brasilApiServer.expect(requestTo("https://brasilapi.test/api/cep/v1/60150160")).andRespond(withServerError());

        assertThat(directory.lookup("60150160")).isInstanceOf(PostalDirectory.Unavailable.class);
    }
}
