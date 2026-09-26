package com.foodie.api.pos;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PosController.class)
class PosControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private PosService pos;

    @MockitoBean
    private PermissionService permissions;

    private static final String BODY = "{\"items\":[{\"productId\":3,\"quantity\":1}],\"paymentMethod\":\"cash\"}";

    @Test
    void restaurantCreatesCounterOrder() throws Exception {
        when(auth.requireUser("s", "restaurant")).thenReturn(new User(5, "Loja", "loja@demo.local", "restaurant", 7L));
        when(pos.createOrder(any(), any())).thenReturn(Map.of("id", 30L, "totalCents", 2500L, "changeCents", 0L, "customerName", "Consumidor balcão"));

        mvc.perform(post("/pos/orders").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(30))
            .andExpect(jsonPath("$.customerName").value("Consumidor balcão"));
    }

    @Test
    void nonRestaurantIsForbidden() throws Exception {
        when(auth.requireUser("k", "restaurant")).thenThrow(new ApiException(403, "Acesso não autorizado"));

        mvc.perform(post("/pos/orders").cookie(new jakarta.servlet.http.Cookie("foodie_session", "k"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void withoutPosPermissionIsForbidden() throws Exception {
        when(auth.requireUser("s", "restaurant")).thenReturn(new User(5, "Loja", "loja@demo.local", "restaurant", 7L));
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(any(User.class), eq(Permissions.POS_MANAGE));

        mvc.perform(post("/pos/orders").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }
}
