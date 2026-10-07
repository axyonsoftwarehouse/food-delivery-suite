package com.foodie.api.finance;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditService;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/finance")
public class FinanceController {
    private static final Set<String> PARTIES = Set.of("admin", "restaurant");

    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final AdminAuditService audit;
    private final CommissionRepository commissions;
    private final LedgerService ledger;
    private final ExpenseRepository expenses;

    public FinanceController(AuthService auth, AdminPermissionService permissions, AdminAuditService audit,
                             CommissionRepository commissions, LedgerService ledger, ExpenseRepository expenses) {
        this.auth = auth;
        this.permissions = permissions;
        this.audit = audit;
        this.commissions = commissions;
        this.ledger = ledger;
        this.expenses = expenses;
    }

    @GetMapping("/commission")
    public Map<String, Object> commission(@CookieValue(value = "foodie_session", required = false) String token) {
        admin(token, AdminPermissions.FINANCE_VIEW);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("global", commissions.global().orElse(BigDecimal.ZERO));
        result.put("rules", commissions.rules());
        return result;
    }

    @PatchMapping("/commission")
    public Map<String, Object> setCommission(@CookieValue(value = "foodie_session", required = false) String token,
                                             @Valid @RequestBody CommissionRequest body) {
        admin(token, AdminPermissions.FINANCE_MANAGE);
        throw new ApiException(410, "Comissões por pedido foram descontinuadas; use assinaturas");
    }

    @GetMapping("/ledger")
    public Map<String, Object> ledger(@CookieValue(value = "foodie_session", required = false) String token,
                                      @RequestParam String party,
                                      @RequestParam(required = false) Long partyId,
                                      @RequestParam(required = false) String from,
                                      @RequestParam(required = false) String to,
                                      @RequestParam(required = false) @Min(1) @Max(300) Integer limit) {
        admin(token, AdminPermissions.FINANCE_VIEW);
        if (!PARTIES.contains(party)) throw new ApiException(400, "Parte inválida");
        int size = limit == null ? 100 : limit;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", ledger.statement(new Party(party, partyId), from, to, size));
        result.put("balanceCents", ledger.balance(new Party(party, partyId)));
        return result;
    }

    @GetMapping("/balances")
    public Map<String, Object> balances(@CookieValue(value = "foodie_session", required = false) String token) {
        admin(token, AdminPermissions.FINANCE_VIEW);
        List<Map<String, Object>> entries = new ArrayList<>();
        entries.add(balanceEntry("admin", null, "Plataforma"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entries", entries);
        return result;
    }

    private Map<String, Object> balanceEntry(String party, Long partyId, String name) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("party", party);
        entry.put("partyId", partyId);
        entry.put("name", name);
        entry.put("balanceCents", ledger.balance(new Party(party, partyId)));
        return entry;
    }

    @GetMapping("/expenses")
    public Map<String, Object> expenseList(@CookieValue(value = "foodie_session", required = false) String token,
                                           @RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to) {
        admin(token, AdminPermissions.FINANCE_VIEW);
        String f = blank(from);
        String t = blank(to);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", expenses.list(f, t));
        result.put("totalCents", expenses.sum(f, t));
        return result;
    }

    @PostMapping("/expenses")
    public ResponseEntity<Map<String, Object>> createExpense(@CookieValue(value = "foodie_session", required = false) String token,
                                                             @Valid @RequestBody ExpenseRequest body) {
        User actor = admin(token, AdminPermissions.FINANCE_MANAGE);
        long id = expenses.insert(body.category().strip(), body.description() == null ? "" : body.description().strip(), body.amountCents(), body.incurredAt(), actor.id());
        audit.record(actor, "create", "expense", id, "Despesa " + body.category().strip());
        return ResponseEntity.status(201).body(Map.of("id", id));
    }

    @DeleteMapping("/expenses/{id}")
    public Map<String, Boolean> deleteExpense(@CookieValue(value = "foodie_session", required = false) String token,
                                              @PathVariable @Positive long id) {
        User actor = admin(token, AdminPermissions.FINANCE_MANAGE);
        if (expenses.delete(id) == 0) throw new ApiException(404, "Despesa não encontrada");
        audit.record(actor, "delete", "expense", id, "Despesa removida");
        return Map.of("ok", true);
    }

    private User admin(String token, String permission) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, permission);
        return user;
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public record CommissionRequest(@Positive Long restaurantId,
                                    @NotNull @DecimalMin("0") @DecimalMax("50") BigDecimal percent) {}

    public record ExpenseRequest(@NotBlank @Size(min = 2, max = 60) String category,
                                 @Size(max = 255) String description,
                                 @Positive long amountCents,
                                 @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String incurredAt) {}
}
