package com.foodie.api.admin;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.rewards.RewardsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/customers")
public class CustomerController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final AdminAuditService audit;
    private final CustomerRepository customers;
    private final RewardsService rewards;

    public CustomerController(AuthService auth, AdminPermissionService permissions, AdminAuditService audit, CustomerRepository customers, RewardsService rewards) {
        this.auth = auth;
        this.permissions = permissions;
        this.audit = audit;
        this.customers = customers;
        this.rewards = rewards;
    }

    @GetMapping
    public Map<String, Object> list(@CookieValue(value = "foodie_session", required = false) String token,
                                    @RequestParam(required = false) String query,
                                    @RequestParam(required = false) Long before,
                                    @RequestParam(required = false) @Min(1) @Max(100) Integer limit) {
        admin(token);
        int size = limit == null ? 30 : limit;
        List<Map<String, Object>> rows = customers.list(query, before, size);
        boolean hasMore = rows.size() > size;
        List<Map<String, Object>> items = hasMore ? rows.subList(0, size) : rows;
        Long nextCursor = hasMore ? ((Number) items.get(items.size() - 1).get("id")).longValue() : null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("nextCursor", nextCursor);
        return result;
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@CookieValue(value = "foodie_session", required = false) String token,
                                      @PathVariable long id) {
        admin(token);
        Map<String, Object> customer = customers.find(id).orElseThrow(() -> new ApiException(404, "Cliente não encontrado"));
        Map<String, Object> result = new LinkedHashMap<>(customer);
        result.put("orders", customers.orders(id));
        result.put("addresses", customers.addresses(id));
        return result;
    }

    @PatchMapping("/{id}/suspension")
    public Map<String, Boolean> suspend(@CookieValue(value = "foodie_session", required = false) String token,
                                        @PathVariable long id,
                                        @Valid @RequestBody SuspensionRequest body) {
        User actor = admin(token);
        customers.find(id).orElseThrow(() -> new ApiException(404, "Cliente não encontrado"));
        auth.setSuspended(id, body.suspended(), body.reason());
        audit.record(actor, "update", "customer", id, body.suspended() ? "Cliente suspenso" : "Cliente reativado");
        return Map.of("ok", true);
    }

    @GetMapping("/{id}/wallet")
    public Map<String, Object> wallet(@CookieValue(value = "foodie_session", required = false) String token,
                                      @PathVariable long id) {
        admin(token);
        customers.find(id).orElseThrow(() -> new ApiException(404, "Cliente não encontrado"));
        Map<String, Object> result = new LinkedHashMap<>(rewards.wallet(id));
        result.put("items", rewards.walletStatement(id, 100));
        return result;
    }

    @PostMapping("/{id}/wallet/credit")
    public Map<String, Object> creditWallet(@CookieValue(value = "foodie_session", required = false) String token,
                                            @PathVariable long id,
                                            @Valid @RequestBody WalletAdjustment body) {
        User actor = admin(token);
        customers.find(id).orElseThrow(() -> new ApiException(404, "Cliente não encontrado"));
        long balance = rewards.credit(id, body.amountCents(), body.note());
        audit.record(actor, "update", "customer_wallet", id, "Crédito de " + body.amountCents());
        return Map.of("balanceCents", balance);
    }

    @PostMapping("/{id}/wallet/debit")
    public Map<String, Object> debitWallet(@CookieValue(value = "foodie_session", required = false) String token,
                                           @PathVariable long id,
                                           @Valid @RequestBody WalletAdjustment body) {
        User actor = admin(token);
        customers.find(id).orElseThrow(() -> new ApiException(404, "Cliente não encontrado"));
        long balance = rewards.debit(id, body.amountCents(), body.note());
        audit.record(actor, "update", "customer_wallet", id, "Débito de " + body.amountCents());
        return Map.of("balanceCents", balance);
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(@CookieValue(value = "foodie_session", required = false) String token) {
        admin(token);
        StringBuilder csv = new StringBuilder("id,nome,email,telefone,pedidos,gasto_reais,suspenso,criado_em\n");
        for (Map<String, Object> row : customers.list(null, null, 1000)) {
            csv.append(row.get("id")).append(',')
                .append(csvText(row.get("name"))).append(',')
                .append(csvText(row.get("email"))).append(',')
                .append(csvText(row.get("phone"))).append(',')
                .append(row.get("orders")).append(',')
                .append(String.format(Locale.ROOT, "%.2f", ((Number) row.get("spend_cents")).longValue() / 100.0)).append(',')
                .append(Boolean.TRUE.equals(row.get("suspended")) ? "sim" : "nao").append(',')
                .append(row.get("created_at")).append('\n');
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"clientes.csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv.toString());
    }

    private User admin(String token) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.CUSTOMERS_MANAGE);
        return user;
    }

    private static String csvText(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    public record SuspensionRequest(boolean suspended, @Size(max = 255) String reason) {}

    public record WalletAdjustment(@jakarta.validation.constraints.Positive @jakarta.validation.constraints.Max(100_000_000) long amountCents,
                                   @Size(max = 255) String note) {}
}
