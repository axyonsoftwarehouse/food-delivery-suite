package com.foodie.api.payments.accounts;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class PaymentOAuthCallbackControllerDefaultUrlTest {
    @Test
    void blankReturnUrlFallsBackToTheLocalPanel() {
        PaymentAccountService accounts = mock(PaymentAccountService.class);
        when(accounts.completeConnection("c", "s", null)).thenReturn("conectado");
        ResponseEntity<Void> response = new PaymentOAuthCallbackController(accounts, "").callback("c", "s", null);
        assertTrue(response.getHeaders().getLocation().toString()
            .startsWith("http://127.0.0.1:3001/painel/configuracoes?"));
    }
}
