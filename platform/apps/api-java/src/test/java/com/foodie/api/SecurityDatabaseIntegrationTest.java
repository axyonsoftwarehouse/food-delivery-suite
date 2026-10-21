package com.foodie.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.chat.ChatController;
import com.foodie.api.chat.ChatPolicy;
import com.foodie.api.notifications.NotificationService;
import com.foodie.api.payments.PaymentGateway;
import com.foodie.api.payments.PaymentGatewayRegistry;
import com.foodie.api.permissions.RoleRepository;
import com.foodie.api.subscriptions.*;
import java.sql.Statement;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

/** Usa somente o banco efêmero do compose de testes, nunca o banco de desenvolvimento. */
@EnabledIfEnvironmentVariable(named = "FOODIE_INTEGRATION_DB", matches = "1")
class SecurityDatabaseIntegrationTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private long restaurantId;
    private long packageId;
    private long subscriptionId;
    private final Instant start = Instant.parse("2026-10-16T00:00:00Z");

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:mariadb://127.0.0.1:3308/foodie_platform",
            "foodie", Objects.requireNonNull(System.getenv("DB_PASSWORD")));
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        String suffix = UUID.randomUUID().toString();
        restaurantId = insert("INSERT INTO restaurants (name, slug) VALUES (?, ?)", "Regressão", "regressao-" + suffix);
        packageId = insert("INSERT INTO subscription_packages (name, price_cents, period_days) VALUES (?, 9900, 30)", "Regressão " + suffix);
        subscriptionId = insert("INSERT INTO restaurant_subscriptions (restaurant_id, package_id, status) VALUES (?, ?, 'active')", restaurantId, packageId);
    }

    @AfterEach
    void cleanUp() {
        if (jdbc == null || restaurantId == 0) return;
        jdbc.update("DELETE FROM users WHERE restaurant_id = ?", restaurantId);
        jdbc.update("DELETE FROM restaurant_roles WHERE restaurant_id = ?", restaurantId);
        jdbc.update("DELETE FROM restaurants WHERE id = ?", restaurantId);
        if (packageId != 0) jdbc.update("DELETE FROM subscription_packages WHERE id = ?", packageId);
    }

    private long insert(String sql, Object... args) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    private void charge(String externalId) {
        new SubscriptionChargeRepository(jdbc).insert(subscriptionId, restaurantId, "pix", 9900,
            externalId, start, start.plusSeconds(30L * 86400));
        jdbc.update("UPDATE subscription_charges SET external_id = ? WHERE subscription_id = ? AND idempotency_key = ?",
            externalId, subscriptionId, externalId);
    }

    private PaymentGateway.Charge paid(String externalId) {
        return new PaymentGateway.Charge(externalId, "sub-test", 9900, "paid", "accredited", null, null, null, null);
    }

    private void simultaneousPayments(String first, String second, int expectedEntries, int expectedRefunds) throws Exception {
        charge(first);
        if (!first.equals(second)) charge(second);
        var barrier = new CyclicBarrier(2);
        var repository = new SubscriptionChargeRepository(jdbc) {
            @Override
            public Optional<Charge> findByExternalId(String provider, String externalId) {
                var initial = super.findByExternalId(provider, externalId);
                // Os dois callbacks já leram pending antes de qualquer UPDATE: reproduz a disputa real.
                assertEquals("pending", initial.orElseThrow().status());
                try { barrier.await(10, TimeUnit.SECONDS); }
                catch (Exception error) { throw new IllegalStateException(error); }
                return initial;
            }
        };
        var notifications = mock(NotificationService.class);
        var gateway = mock(PaymentGateway.class);
        var gateways = mock(PaymentGatewayRegistry.class);
        when(gateways.resolve("mercadopago")).thenReturn(gateway);
        var service = new SubscriptionBillingService(jdbc, repository, gateways,
            new SubscriptionBillingAccount(true, "fake-token", "fake-key"), notifications, 3);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> tx.execute(status -> service.applyPaid("mercadopago", first, paid(first))));
            var b = workers.submit(() -> tx.execute(status -> service.applyPaid("mercadopago", second, paid(second))));
            assertNotNull(a.get(20, TimeUnit.SECONDS));
            assertNotNull(b.get(20, TimeUnit.SECONDS));
        }
        assertEquals(expectedEntries, jdbc.queryForObject("SELECT COUNT(*) FROM subscription_transactions WHERE subscription_id = ?", Integer.class, subscriptionId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM subscription_charges WHERE subscription_id = ? AND status = 'paid'", Integer.class, subscriptionId));
        assertEquals(expectedRefunds, jdbc.queryForObject("SELECT COUNT(*) FROM subscription_charges WHERE subscription_id = ? AND status = 'refunded'", Integer.class, subscriptionId));
        verify(notifications, times(1)).notifyRestaurant(eq(restaurantId), eq("subscription"), anyString(), anyString(), isNull());
        verify(gateway, times(expectedRefunds)).refund(any(), anyString(), anyString());
    }

    @Test
    void simultaneousWebhooksForTheSameChargeWriteOnlyOneReceipt() throws Exception {
        simultaneousPayments("same-charge", "same-charge", 1, 0);
    }

    @Test
    void simultaneousChargesForTheSamePeriodRefundTheDuplicateOnlyOnce() throws Exception {
        simultaneousPayments("charge-a", "charge-b", 2, 1);
    }

    @Test
    void panelStaffCannotBecomeOwnerAndOwnerCannotBeReassignedAsStaff() {
        var roles = new RoleRepository(jdbc);
        long role = roles.create(restaurantId, "Equipe", List.of("staff.manage"));
        long owner = roles.createStaff(restaurantId, "Dono", UUID.randomUUID() + "@demo.local", "not-a-login-hash", "restaurant", null);
        long staff = roles.createStaff(restaurantId, "Equipe", UUID.randomUUID() + "@demo.local", "not-a-login-hash", "restaurant", role);
        assertEquals(0, roles.assignStaffRole(restaurantId, staff, null));
        assertEquals(role, roles.findForUser(staff).orElseThrow().id());
        assertEquals(0, roles.assignStaffRole(restaurantId, owner, role));
        assertNull(roles.staff(restaurantId).stream().filter(person -> person.id() == owner).findFirst().orElseThrow().staffRoleId());
    }

    @Test
    void chatWith205MessagesPagesAndReadsOnlyTheDeliveredRows() {
        var roles = new RoleRepository(jdbc);
        long sender = roles.createStaff(restaurantId, "Origem", UUID.randomUUID() + "@demo.local", "not-a-login-hash", "kitchen", null);
        long recipient = roles.createStaff(restaurantId, "Destino", UUID.randomUUID() + "@demo.local", "not-a-login-hash", "kitchen", null);
        var ids = new ArrayList<Long>();
        for (int i = 0; i < 205; i++) ids.add(insert("INSERT INTO chat_messages (from_user_id, to_user_id, body) VALUES (?, ?, ?)", sender, recipient, "Mensagem " + i));
        var auth = mock(AuthService.class);
        when(auth.requireUser("s")).thenReturn(new User(recipient, "Destino", "d@demo.local", "kitchen", restaurantId));
        var chat = new ChatController(auth, jdbc, mock(ChatPolicy.class), mock(com.foodie.api.admin.AdminPermissionService.class));
        var page = chat.messages("s", sender, null);
        assertEquals(200, page.size());
        assertEquals(ids.get(5).longValue(), ((Number) page.getFirst().get("id")).longValue());
        assertEquals(ids.getLast().longValue(), ((Number) page.getLast().get("id")).longValue());
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages WHERE to_user_id = ? AND read_at IS NULL", Integer.class, recipient));
        assertEquals(5, chat.messages("s", sender, ids.get(5)).size());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages WHERE to_user_id = ? AND read_at IS NULL", Integer.class, recipient));
    }

    @Test
    void roleDeletionCannotClearAStaffRoleAssignedAfterTheInitialCheck() {
        var roles = new RoleRepository(jdbc);
        long role = roles.create(restaurantId, "Concorrente", List.of("staff.manage"));
        assertFalse(roles.assigned(role));
        long staff = roles.createStaff(restaurantId, "Equipe", UUID.randomUUID() + "@demo.local", "not-a-login-hash", "restaurant", role);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> roles.delete(restaurantId, role));
        assertEquals(role, roles.findForUser(staff).orElseThrow().id());
    }

    @Test
    void passwordResetBetweenValidationAndSessionCreationRejectsTheOldPassword() throws Exception {
        long user = new RoleRepository(jdbc).createStaff(restaurantId, "Login", UUID.randomUUID() + "@demo.local", "old-hash", "kitchen", null);
        var readOld = new CountDownLatch(1);
        var resume = new CountDownLatch(1);
        var passwords = mock(com.foodie.api.auth.PasswordVerifier.class);
        when(passwords.matches(anyString(), eq("old-hash"))).thenAnswer(call -> {
            readOld.countDown(); assertTrue(resume.await(10, TimeUnit.SECONDS)); return true;
        });
        var repository = new com.foodie.api.auth.AuthRepository(jdbc) {
            @Override public boolean createSessionIfPasswordCurrent(String hash, long id, String passwordHash) {
                return Boolean.TRUE.equals(tx.execute(status -> super.createSessionIfPasswordCurrent(hash, id, passwordHash)));
            }
        };
        String email = jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, user);
        var service = new AuthService(repository, passwords);
        try (var workers = Executors.newSingleThreadExecutor()) {
            var login = workers.submit(() -> assertThrows(ApiException.class, () -> service.login(email, "old-password")));
            assertTrue(readOld.await(10, TimeUnit.SECONDS));
            try {
                tx.executeWithoutResult(status -> { repository.updatePassword(user, "new-hash"); repository.deleteSessionsForUser(user); });
            } finally { resume.countDown(); }
            assertEquals(401, login.get(15, TimeUnit.SECONDS).status());
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sessions WHERE user_id = ?", Integer.class, user));
    }

    @Test
    void concurrentOtpRequestsReserveAtMostFiveSendsPerPhone() throws Exception {
        var repository = new com.foodie.api.auth.AuthRepository(jdbc);
        String phoneKey = com.foodie.api.auth.Tokens.hash("test-phone-" + UUID.randomUUID());
        String originKey = com.foodie.api.auth.Tokens.hash("test-origin-" + UUID.randomUUID());
        var gate = new CountDownLatch(1);
        try {
            var requests = new ArrayList<Future<Boolean>>();
            try (var workers = Executors.newFixedThreadPool(20)) {
                for (int i = 0; i < 20; i++) requests.add(workers.submit(() -> {
                    gate.await();
                    try { tx.executeWithoutResult(status -> repository.reserveOtpSend(phoneKey, originKey, 5, 20)); return true; }
                    catch (ApiException limited) { assertEquals(429, limited.status()); return false; }
                }));
                gate.countDown();
                int allowed = 0;
                for (var request : requests) if (request.get(20, TimeUnit.SECONDS)) allowed++;
                assertEquals(5, allowed);
            }
            assertEquals(5, jdbc.queryForObject("SELECT failed_attempts FROM auth_login_limits WHERE subject_hash = ?", Integer.class, phoneKey));
            assertEquals(5, jdbc.queryForObject("SELECT failed_attempts FROM auth_login_limits WHERE subject_hash = ?", Integer.class, originKey));
        } finally { jdbc.update("DELETE FROM auth_login_limits WHERE subject_hash IN (?, ?)", phoneKey, originKey); }
    }

    private SubscriptionBillingService billing(PaymentGateway gateway, SubscriptionChargeRepository repository) {
        var gateways = mock(PaymentGatewayRegistry.class);
        when(gateways.resolve(any())).thenReturn(gateway);
        return new SubscriptionBillingService(jdbc, repository, gateways, new SubscriptionBillingAccount(true, "fake", "fake"),
            mock(NotificationService.class), 3, tx);
    }

    private void due() {
        jdbc.update("UPDATE restaurant_subscriptions SET ends_at = ?, next_charge_at = ?, status = 'active' WHERE id = ?",
            java.sql.Timestamp.from(start.minusSeconds(86400)), java.sql.Timestamp.from(start.minusSeconds(86400)), subscriptionId);
    }

    private PaymentGateway.Charge pending(String externalId, long reservedId) {
        return new PaymentGateway.Charge(externalId, "sub-" + reservedId, 9900, "pending", "pending", "qr", null, null, Instant.now().plusSeconds(3600));
    }

    @Test
    void twoGeneratorsShareTheSameDurableAttemptAndCreateOnlyOnce() throws Exception {
        due();
        var gateway = mock(PaymentGateway.class);
        String external = "gen-" + UUID.randomUUID();
        when(gateway.create(any(), any())).thenAnswer(call -> pending(external, ((PaymentGateway.ChargeRequest) call.getArgument(1)).orderId()));
        when(gateway.fetch(any(), eq(external))).thenReturn(pending(external, 1));
        var service = billing(gateway, new SubscriptionChargeRepository(jdbc));
        try (var workers = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var a = workers.submit(() -> { gate.await(); return service.generateForSubscription(subscriptionId); });
            var b = workers.submit(() -> { gate.await(); return service.generateForSubscription(subscriptionId); });
            gate.countDown(); a.get(20, TimeUnit.SECONDS); b.get(20, TimeUnit.SECONDS);
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM subscription_charges WHERE subscription_id = ?", Integer.class, subscriptionId));
        verify(gateway, times(1)).create(any(), any());
    }

    @Test
    void providerTimeoutRetriesTheSameAttemptAndIdempotencyKey() {
        due();
        var gateway = mock(PaymentGateway.class);
        when(gateway.create(any(), any())).thenThrow(new ApiException(502, "Timeout depois de criar"))
            .thenAnswer(call -> pending("retry-" + subscriptionId, ((PaymentGateway.ChargeRequest) call.getArgument(1)).orderId()));
        var service = billing(gateway, new SubscriptionChargeRepository(jdbc));
        assertTrue(service.generateForSubscription(subscriptionId).isEmpty());
        assertTrue(service.generateForSubscription(subscriptionId).isPresent());
        var requests = org.mockito.ArgumentCaptor.forClass(PaymentGateway.ChargeRequest.class);
        verify(gateway, times(2)).create(any(), requests.capture());
        assertEquals(requests.getAllValues().get(0).idempotencyKey(), requests.getAllValues().get(1).idempotencyKey());
        assertEquals("sub-" + requests.getAllValues().get(0).orderId(), requests.getAllValues().get(0).externalReference());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM subscription_charges WHERE subscription_id = ?", Integer.class, subscriptionId));
    }

    @Test
    void earlyWebhookRecoversAnAttemptWhoseCreateResponseWasLost() {
        due();
        var gateway = mock(PaymentGateway.class);
        when(gateway.create(any(), any())).thenThrow(new ApiException(502, "Resposta perdida"));
        var repository = new SubscriptionChargeRepository(jdbc);
        var service = billing(gateway, repository);
        service.generateForSubscription(subscriptionId);
        long reserved = jdbc.queryForObject("SELECT id FROM subscription_charges WHERE subscription_id = ?", Long.class, subscriptionId);
        String external = "early-" + reserved;
        var paid = new PaymentGateway.Charge(external, "sub-" + reserved, 9900, "paid", "accredited", null, null, null, null);
        when(gateway.fetch(any(), eq(external))).thenReturn(paid);
        var gateways = mock(PaymentGatewayRegistry.class);
        when(gateways.resolve(any())).thenReturn(gateway);
        var router = new SubscriptionWebhookRouter(repository, service, new SubscriptionBillingAccount(true, "fake", "fake"), gateways);
        var result = tx.execute(status -> router.route("mercadopago", external, null));
        assertEquals("paid", result.orElseThrow().get("status"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM subscription_transactions WHERE subscription_id = ?", Integer.class, subscriptionId));
        assertTrue(service.generateForSubscription(subscriptionId).isEmpty());
        verify(gateway, times(1)).create(any(), any());
    }

    @Test
    void reissuedChargeKeepsCycleIdentityAndLateDoublePaymentIsRefunded() {
        due();
        var gateway = mock(PaymentGateway.class);
        when(gateway.create(any(), any())).thenAnswer(call -> {
            long id = ((PaymentGateway.ChargeRequest) call.getArgument(1)).orderId(); return pending("cycle-" + id, id);
        });
        when(gateway.fetch(any(), anyString())).thenAnswer(call -> new PaymentGateway.Charge(call.getArgument(1), null, 9900, "expired", "expired", null, null, null, null));
        var repository = new SubscriptionChargeRepository(jdbc);
        var service = billing(gateway, repository);
        long first = service.generateForSubscription(subscriptionId).orElseThrow();
        service.generateForSubscription(subscriptionId); // só reemite depois de expiração confirmada no provedor
        long second = service.generateForSubscription(subscriptionId).orElseThrow();
        assertEquals(repository.findById(first).orElseThrow().periodStart(), repository.findById(second).orElseThrow().periodStart());
        tx.execute(status -> service.applyPaid("mercadopago", "cycle-" + first, paid("cycle-" + first)));
        tx.execute(status -> service.applyPaid("mercadopago", "cycle-" + second, paid("cycle-" + second)));
        verify(gateway).refund(any(), eq("cycle-" + second), anyString());
        assertEquals("refunded", repository.findById(second).orElseThrow().status());
    }

    @Test
    void paymentBetweenDelinquencyReadAndUpdateCannotSuspendTheRenewedSubscription() {
        due();
        var intercepted = spy(jdbc);
        doAnswer(call -> {
            @SuppressWarnings("unchecked") var selected = (List<Map<String, Object>>) call.callRealMethod();
            jdbc.update("UPDATE restaurant_subscriptions SET ends_at = DATE_ADD(NOW(), INTERVAL 30 DAY), status = 'active', delinquent_since = NULL WHERE id = ?", subscriptionId);
            return selected;
        }).when(intercepted).queryForList(argThat(sql -> sql != null && sql.startsWith("SELECT id, restaurant_id, delinquent_since")));
        new SubscriptionRenewalRunner(intercepted, mock(SubscriptionBillingService.class), mock(NotificationService.class), 5).run();
        assertEquals("active", jdbc.queryForObject("SELECT status FROM restaurant_subscriptions WHERE id = ?", String.class, subscriptionId));
    }
}
