package com.foodie.api.orders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CouponController.class)
class CouponControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private CouponService coupons;

    @MockitoBean
    private JdbcTemplate jdbc;

    @MockitoBean
    private AdminPermissionService adminPermissions;

    @Test
    void validateReturnsDiscount() throws Exception {
        when(auth.requireUser("session", "customer")).thenReturn(new User(7, "Cliente", "cliente@demo.local", "customer", null));
        when(coupons.validate("BEMVINDO", 7L, 4000L, 7L)).thenReturn(new CouponService.Applied(1L, "BEMVINDO", 400L));

        mvc.perform(post("/coupons/validate")
                .cookie(new jakarta.servlet.http.Cookie("foodie_session", "session"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"BEMVINDO\",\"restaurantId\":7,\"subtotalCents\":4000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.discountCents").value(400))
            .andExpect(jsonPath("$.code").value("BEMVINDO"));
    }

    @Test
    void adminListsCouponsWithRestaurantName() throws Exception {
        when(auth.requireUser("session", "admin")).thenReturn(new User(1, "Admin", "admin@demo.local", "admin", null));
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("id", 3, "code", "CANTINA15", "restaurant_name", "Cantina do Bairro")));
        mvc.perform(get("/admin/coupons").cookie(new jakarta.servlet.http.Cookie("foodie_session", "session")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].restaurant_name").value("Cantina do Bairro"));
    }

    @Test
    void adminWithoutPromotionsPermissionCannotListCoupons() throws Exception {
        User admin = new User(1, "Admin", "admin@demo.local", "admin", null);
        when(auth.requireUser("session", "admin")).thenReturn(admin);
        doThrow(new ApiException(403, "Acesso não autorizado")).when(adminPermissions).require(admin, AdminPermissions.PROMOTIONS_MANAGE);
        mvc.perform(get("/admin/coupons").cookie(new jakarta.servlet.http.Cookie("foodie_session", "session")))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotCreateOrDeleteCoupons() throws Exception {
        when(auth.requireUser("session", "admin")).thenReturn(new User(1, "Admin", "admin@demo.local", "admin", null));
        mvc.perform(post("/admin/coupons").cookie(new jakarta.servlet.http.Cookie("foodie_session", "session"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"GERAL10\",\"discountType\":\"percent\",\"discountValue\":10,\"minOrderCents\":0}"))
            .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/admin/coupons/3").cookie(new jakarta.servlet.http.Cookie("foodie_session", "session")))
            .andExpect(status().is4xxClientError());
    }
}
