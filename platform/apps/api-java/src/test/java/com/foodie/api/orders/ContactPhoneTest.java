package com.foodie.api.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodie.api.ApiException;
import org.junit.jupiter.api.Test;

class ContactPhoneTest {
    @Test
    void keepsOnlyDigitsOfABrazilianNumberWithAreaCode() {
        assertThat(ContactPhone.normalize("(85) 99876-5432")).isEqualTo("85998765432");
        assertThat(ContactPhone.normalize("85 3222-1100")).isEqualTo("8532221100");
        assertThat(ContactPhone.normalize("  ")).isNull();
        assertThat(ContactPhone.normalize(null)).isNull();
    }

    @Test
    void refusesNumbersWithoutAreaCodeOrWithTheWrongLength() {
        for (String invalid : new String[] {"99876-5432", "0859987654", "859987654321", "abc"}) {
            assertThatThrownBy(() -> ContactPhone.normalize(invalid)).isInstanceOf(ApiException.class)
                .hasMessage("Telefone inválido: use DDD + número (10 ou 11 dígitos)");
        }
    }

    @Test
    void deliveryRequiresAPhoneAndOtherTypesIgnoreIt() {
        assertThatThrownBy(() -> ContactPhone.requireForDelivery("delivery", "")).hasMessage("Informe um telefone de contato para a entrega");
        assertThatThrownBy(() -> ContactPhone.requireForDelivery(null, null)).hasMessage("Informe um telefone de contato para a entrega");
        assertThat(ContactPhone.requireForDelivery("delivery", "(85) 99876-5432")).isEqualTo("85998765432");
        assertThat(ContactPhone.requireForDelivery("take_away", "")).isNull();
        assertThat(ContactPhone.requireForDelivery("dine_in", "85998765432")).isNull();
    }
}
