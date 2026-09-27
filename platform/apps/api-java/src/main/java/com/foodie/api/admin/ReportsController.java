package com.foodie.api.admin;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/reports")
public class ReportsController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final ReportsService reports;

    public ReportsController(AuthService auth, AdminPermissionService permissions, ReportsService reports) {
        this.auth = auth;
        this.permissions = permissions;
        this.reports = reports;
    }

    @GetMapping("/earnings")
    public Map<String, Object> earnings(@CookieValue(value = "foodie_session", required = false) String token,
                                        @RequestParam(required = false) String scope,
                                        @RequestParam(required = false) String groupBy,
                                        @RequestParam(required = false) String from,
                                        @RequestParam(required = false) String to) {
        admin(token);
        return reports.earnings(scope, groupBy, from, to);
    }

    @GetMapping("/orders")
    public Map<String, Object> orders(@CookieValue(value = "foodie_session", required = false) String token,
                                      @RequestParam(required = false) String from,
                                      @RequestParam(required = false) String to) {
        admin(token);
        return reports.orders(from, to);
    }

    @GetMapping("/products")
    public List<Map<String, Object>> products(@CookieValue(value = "foodie_session", required = false) String token,
                                              @RequestParam(required = false) String from,
                                              @RequestParam(required = false) String to,
                                              @RequestParam(required = false) @Min(1) @Max(500) Integer limit) {
        admin(token);
        return reports.products(from, to, limit == null ? 50 : limit);
    }

    @GetMapping("/zones")
    public List<Map<String, Object>> zones(@CookieValue(value = "foodie_session", required = false) String token,
                                           @RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to,
                                           @RequestParam(required = false) @Min(1) @Max(500) Integer limit) {
        admin(token);
        return reports.zones(from, to, limit == null ? 50 : limit);
    }

    @GetMapping("/daily")
    public List<Map<String, Object>> daily(@CookieValue(value = "foodie_session", required = false) String token,
                                           @RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to) {
        admin(token);
        return reports.daily(from, to);
    }

    @GetMapping("/customers")
    public List<Map<String, Object>> customers(@CookieValue(value = "foodie_session", required = false) String token,
                                               @RequestParam(required = false) String from,
                                               @RequestParam(required = false) String to,
                                               @RequestParam(required = false) @Min(1) @Max(500) Integer limit) {
        admin(token);
        return reports.customers(from, to, limit == null ? 50 : limit);
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(@CookieValue(value = "foodie_session", required = false) String token,
                                         @RequestParam String report,
                                         @RequestParam(required = false) String scope,
                                         @RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to) {
        admin(token);
        String csv = "earnings".equals(report) && scope != null ? reports.earningsCsv(scope, from, to) : reports.reportCsv(report, from, to);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"relatorio-" + report + ".csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv);
    }

    private void admin(String token) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.REPORTS_VIEW);
    }
}
