package com.foodie.api.orders;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class OrderExpiryRunnerTest {
    private final OrderService orders = mock(OrderService.class);
    private final OrderExpiryRunner runner = new OrderExpiryRunner(orders);

    @Test
    void expiresOnEveryRound() {
        runner.run();
        runner.run();
        verify(orders, times(2)).expireStale();
    }

    @Test
    void aFailedRoundDoesNotStopTheScheduler() {
        // Exceção que escapa de um @Scheduled só é logada pelo Spring, mas aqui fica explícito e testado.
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("banco fora")).when(orders).expireStale();
        runner.run();
        verify(orders).expireStale();
    }
}
