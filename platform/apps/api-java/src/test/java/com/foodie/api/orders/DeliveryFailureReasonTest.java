package com.foodie.api.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodie.api.ApiException;
import org.junit.jupiter.api.Test;

class DeliveryFailureReasonTest {
    @Test
    void knowsTheFiveReasons() {
        assertThat(DeliveryFailureReason.fromCode("customer_absent").label()).isEqualTo("Cliente ausente");
        assertThat(DeliveryFailureReason.fromCode("address_not_found").label()).isEqualTo("Endereço não encontrado");
        assertThat(DeliveryFailureReason.fromCode("customer_refused").label()).isEqualTo("Cliente recusou o pedido");
        assertThat(DeliveryFailureReason.fromCode("no_answer").label()).isEqualTo("Não atende o telefone");
        assertThat(DeliveryFailureReason.fromCode("other").label()).isEqualTo("Outro");
    }

    @Test
    void unknownReasonIsRefused() {
        assertThatThrownBy(() -> DeliveryFailureReason.fromCode("sumiu"))
            .isInstanceOf(ApiException.class).hasMessage("Motivo da falha inválido");
    }

    @Test
    void otherRequiresANote() {
        assertThatThrownBy(() -> DeliveryFailureReason.compose(DeliveryFailureReason.OTHER, "  "))
            .isInstanceOf(ApiException.class).hasMessage("Descreva o motivo da falha");
        assertThat(DeliveryFailureReason.compose(DeliveryFailureReason.OTHER, "portão trancado"))
            .isEqualTo("Outro: portão trancado");
    }

    @Test
    void otherReasonsTakeAnOptionalNote() {
        assertThat(DeliveryFailureReason.compose(DeliveryFailureReason.CUSTOMER_ABSENT, null)).isEqualTo("Cliente ausente");
        assertThat(DeliveryFailureReason.compose(DeliveryFailureReason.CUSTOMER_ABSENT, " tocou 3x "))
            .isEqualTo("Cliente ausente: tocou 3x");
    }
}
