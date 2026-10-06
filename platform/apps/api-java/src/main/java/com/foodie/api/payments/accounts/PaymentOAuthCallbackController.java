package com.foodie.api.payments.accounts;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Retorno da autorização no Mercado Pago. Público: quem identifica a loja é o `state` (uso único, 10
 * minutos), não o cookie — o retorno chega no domínio da API e a sessão vive no domínio do painel.
 */
@RestController
public class PaymentOAuthCallbackController {
    private static final String DEFAULT_RETURN_URL = "http://127.0.0.1:3001/painel/configuracoes";
    private final PaymentAccountService accounts;
    private final String returnUrl;

    public PaymentOAuthCallbackController(PaymentAccountService accounts,
                                          @Value("${app.payments.account-return-url:http://127.0.0.1:3001/painel/configuracoes}") String returnUrl) {
        this.accounts = accounts;
        // docker-compose local repassa a variável vazia; o Spring injeta "" em vez do padrão do yml.
        this.returnUrl = returnUrl == null || returnUrl.isBlank() ? DEFAULT_RETURN_URL : returnUrl;
    }

    @GetMapping("/payments/mercadopago/oauth/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        String outcome = accounts.completeConnection(code, state, error);
        String query = "conectado".equals(outcome) ? "mercadopago=conectado" : "mercadopago=erro&motivo=" + outcome;
        String target = returnUrl + (returnUrl.contains("?") ? "&" : "?") + query;
        return ResponseEntity.status(302).location(URI.create(target)).build();
    }
}
