package com.foodie.api.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditRepository;
import com.foodie.api.auth.User;
import com.foodie.api.notifications.NotificationService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class SupportActionServiceTest {
    private final User admin = new User(1, "Ana Suporte", "ana@demo.local", "admin", null);
    private JdbcTemplate jdbc;
    private AdminAuditRepository audit;
    private NotificationService notifications;
    private SupportActionService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        audit = mock(AdminAuditRepository.class);
        notifications = mock(NotificationService.class);
        service = new SupportActionService(jdbc, audit, notifications);
    }

    @SuppressWarnings("unchecked")
    private void restaurantExists(boolean exists) {
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(7L))).thenReturn(exists ? 1 : null);
    }

    @Test
    void rejectsShortReasonWithoutTouchingAnything() {
        AtomicBoolean ran = new AtomicBoolean(false);
        assertThatThrownBy(() -> service.act(admin, 7L, "product.update", "product", 11L, "Preço", "curto", () -> { ran.set(true); return 1; }))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("status", 400);
        assertThat(ran).isFalse();
        verify(audit, never()).insertSupport(anyLong(), any(), anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void unknownRestaurantIs404() {
        restaurantExists(false);
        assertThatThrownBy(() -> service.act(admin, 7L, "product.update", "product", 11L, "Preço", "Preço digitado errado pela loja", () -> 1))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("status", 404);
    }

    @Test
    void runsChangeAuditsWithReasonAndNotifiesStore() {
        restaurantExists(true);
        Integer result = service.act(admin, 7L, "product.update", "product", 11L, "Preço do Bowl corrigido", "  Preço digitado errado pela loja  ", () -> 42);

        assertThat(result).isEqualTo(42);
        verify(audit).insertSupport(1L, "Ana Suporte", 7L, "product.update", "product", 11L, "Preço do Bowl corrigido", "Preço digitado errado pela loja");
        verify(notifications).notifyRestaurant(7L, "support_action", "Suporte Foodie: Preço do Bowl corrigido", "Preço digitado errado pela loja", null);
    }

    @Test
    void auditFailurePropagatesSoTheTransactionRollsBack() {
        restaurantExists(true);
        doThrow(new RuntimeException("db down")).when(audit).insertSupport(anyLong(), any(), anyLong(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.act(admin, 7L, "product.update", "product", 11L, "Preço", "Preço digitado errado pela loja", () -> 1))
            .hasMessage("db down");
        verify(notifications, never()).notifyRestaurant(anyLong(), any(), any(), any(), any());
    }

    @Test
    void notificationFailureDoesNotFailTheAction() {
        restaurantExists(true);
        doThrow(new RuntimeException("push down")).when(notifications).notifyRestaurant(anyLong(), any(), any(), any(), any());

        assertThat(service.act(admin, 7L, "product.update", "product", 11L, "Preço", "Preço digitado errado pela loja", () -> 5)).isEqualTo(5);
    }

    @Test
    void ordersAreRecordedWithTheirOwnReason() {
        service.recordOrderAction(admin, 7L, 99L, "cancel", "Loja fechou");
        verify(audit).insertSupport(1L, "Ana Suporte", 7L, "order.cancel", "order", 99L, "Pedido #99: cancel", "Loja fechou");
        verify(notifications).notifyRestaurant(eq(7L), eq("support_action"), eq("Suporte Foodie: Pedido #99: cancel"), eq("Loja fechou"), eq(99L));
    }

    @Test
    void orderActionWithoutReasonStoresNull() {
        service.recordOrderAction(admin, 7L, 99L, "assign", null);
        verify(audit).insertSupport(eq(1L), eq("Ana Suporte"), eq(7L), eq("order.assign"), eq("order"), eq(99L), eq("Pedido #99: assign"), isNull());
    }

    @Test
    void notifiesOnlyAfterTheChangeAndTheAudit() {
        restaurantExists(true);
        List<String> calls = new ArrayList<>();

        doAnswer(inv -> {
            calls.add("audit");
            return null;
        }).when(audit).insertSupport(anyLong(), any(), anyLong(), any(), any(), any(), any(), any());

        doAnswer(inv -> {
            calls.add("notify");
            return null;
        }).when(notifications).notifyRestaurant(anyLong(), any(), any(), any(), any());

        service.act(admin, 7L, "product.update", "product", 11L, "Preço", "Preço digitado errado pela loja", () -> {
            calls.add("change");
            return 42;
        });

        assertThat(calls).isEqualTo(List.of("change", "audit", "notify"));
    }

    @Test
    void accepts500CharacterReason() {
        String reason500 = "a".repeat(500);
        String normalized = SupportActionService.normalizeReason(reason500);
        assertThat(normalized).isEqualTo(reason500);
    }

    @Test
    void rejects501CharacterReason() {
        String reason501 = "a".repeat(501);
        assertThatThrownBy(() -> SupportActionService.normalizeReason(reason501))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("status", 400);
    }

    @Test
    void rejectsNullReason() {
        assertThatThrownBy(() -> SupportActionService.normalizeReason(null))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("status", 400);
    }

    @Test
    void rejectsBlankReason() {
        assertThatThrownBy(() -> SupportActionService.normalizeReason("            "))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("status", 400);
    }
}
