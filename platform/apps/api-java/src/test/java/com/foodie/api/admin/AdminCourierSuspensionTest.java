package com.foodie.api.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.PasswordVerifier;
import com.foodie.api.auth.User;
import com.foodie.api.catalog.PostalCoverageService;
import com.foodie.api.courier.CourierShiftService;
import com.foodie.api.routing.GeocodingService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminController.class)
class AdminCourierSuspensionTest {
    private static final User ADMIN = new User(1, "Admin", "admin@demo.local", "admin", null);

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService auth;
    @MockitoBean private JdbcTemplate jdbc;
    @MockitoBean private PasswordVerifier passwords;
    @MockitoBean private PostalCoverageService postalCoverage;
    @MockitoBean private GeocodingService geocoding;
    @MockitoBean private AdminPermissionService permissions;
    @MockitoBean private AdminAccessService access;
    @MockitoBean private AdminAuditService auditor;
    @MockitoBean private CourierShiftService shifts;

    @Test
    @SuppressWarnings("unchecked")
    void suspendingACourierClosesTheShift() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(ADMIN);
        when(jdbc.query(eq("SELECT 1 FROM users WHERE id = ? AND role = 'courier'"), any(ResultSetExtractor.class), eq(12L))).thenReturn(1);
        mvc.perform(patch("/admin/couriers/12/suspension").cookie(new Cookie("foodie_session", "s"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"suspended\":true,\"reason\":\"Fraude\"}"))
            .andExpect(status().isOk());
        verify(shifts).closeForSuspension(12L);
    }
}
