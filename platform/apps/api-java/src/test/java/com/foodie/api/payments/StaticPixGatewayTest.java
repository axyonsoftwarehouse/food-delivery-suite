package com.foodie.api.payments;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StaticPixGatewayTest {
    @Test
    void buildsValidLookingBrCode() {
        String code = StaticPixGateway.brCode("chave@pix.com", "Foodie", "Fortaleza", 1050);
        assertThat(code).startsWith("000201");
        assertThat(code).contains("br.gov.bcb.pix");
        assertThat(code).contains("chave@pix.com");
        assertThat(code).contains("540510.50");
        assertThat(code).contains("5802BR");
        assertThat(code.substring(code.length() - 4)).matches("[0-9A-F]{4}");
    }

    @Test
    void uppercaseAndTruncatesMerchant() {
        String code = StaticPixGateway.brCode("k", "restaurante muito comprido demais", "c", 0);
        assertThat(code).doesNotContain("5405");
        assertThat(code).contains("RESTAURANTE MUITO COMPRID");
    }
}
