package com.foodie.api.orders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Expira os pedidos sem aceite de minuto em minuto (revisão de 08/10/2026). Antes a expiração rodava
 * dentro das leituras e dependia de alguém abrir uma lista; cada pedido continua expirando na própria
 * transação, com o estorno automático por último (OrderService.expireStale).
 */
@Service
public class OrderExpiryRunner {
    private static final Logger log = LoggerFactory.getLogger(OrderExpiryRunner.class);
    private final OrderService orders;

    public OrderExpiryRunner(OrderService orders) {
        this.orders = orders;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void run() {
        try {
            orders.expireStale();
        } catch (RuntimeException error) {
            // Falha da busca (banco fora, por exemplo): a próxima rodada tenta de novo.
            log.warn("Expiração de pedidos falhou nesta rodada: {}", error.getMessage());
        }
    }
}
