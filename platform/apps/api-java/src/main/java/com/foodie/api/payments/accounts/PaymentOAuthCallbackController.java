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
        PaymentAccountService.Callback result = accounts.receiveCallback(code, state, error);
        // O painel confirma com a sessão do dono (POST .../mercadopago/confirm); só então a conta é conectada.
        String query = "confirmar".equals(result.outcome())
            ? "mercadopago=confirmar&token=" + result.confirmToken()
            : "mercadopago=erro&motivo=" + result.outcome();
        String target = returnUrl + (returnUrl.contains("?") ? "&" : "?") + query;
        return ResponseEntity.status(302).location(URI.create(target)).build();
    }
}
