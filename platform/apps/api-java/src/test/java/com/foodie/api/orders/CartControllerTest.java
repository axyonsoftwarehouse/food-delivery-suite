package com.foodie.api.orders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.EmailVerificationGuard;
import com.foodie.api.auth.User;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CartController.class)
class CartControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private CartService cart;

    @MockitoBean
    private EmailVerificationGuard verification;

    @Test
    void removesTheWholeLineWithVariationAddonsAndNote() throws Exception {
        User customer = new User(7, "Cliente", "cliente@demo.local", "customer", null);
        when(auth.requireUser("session", "customer")).thenReturn(customer);
        when(cart.remove(same(customer), eq(10L), eq(2L), eq(List.of(3L, 4L)), eq("sem cebola")))
            .thenReturn(new CartService.CartSnapshot(List.of(), "v1"));

        mvc.perform(delete("/cart/items/10?variationId=2&addonIds=3&addonIds=4&note=sem cebola")
                .cookie(new jakarta.servlet.http.Cookie("foodie_session", "session")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value("v1"));

        verify(cart).remove(same(customer), eq(10L), eq(2L), eq(List.of(3L, 4L)), eq("sem cebola"));
    }

    @Test
    void removesASimpleLineWithoutVariationOrAddons() throws Exception {
        User customer = new User(7, "Cliente", "cliente@demo.local", "customer", null);
        when(auth.requireUser("session", "customer")).thenReturn(customer);
        when(cart.remove(same(customer), eq(10L), eq(0L), isNull(), isNull()))
            .thenReturn(new CartService.CartSnapshot(List.of(), "v2"));

        mvc.perform(delete("/cart/items/10")
                .cookie(new jakarta.servlet.http.Cookie("foodie_session", "session")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value("v2"));

        verify(cart).remove(same(customer), eq(10L), eq(0L), isNull(), isNull());
    }

    @Test
    void changesTheNoteOfALine() throws Exception {
        User customer = new User(7, "Cliente", "cliente@demo.local", "customer", null);
        when(auth.requireUser("session", "customer")).thenReturn(customer);
        when(cart.changeNote(same(customer), eq(10L), eq(0L), isNull(), eq(""), eq("sem cebola")))
            .thenReturn(new CartService.CartSnapshot(List.of(), "v3"));

        mvc.perform(put("/cart/items/10/note")
                .cookie(new jakarta.servlet.http.Cookie("foodie_session", "session"))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"note\":\"\",\"newNote\":\"sem cebola\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value("v3"));
    }

    @Test
    void rejectsAnItemNoteLongerThanTheLimit() throws Exception {
        User customer = new User(7, "Cliente", "cliente@demo.local", "customer", null);
        when(auth.requireUser("session", "customer")).thenReturn(customer);

        mvc.perform(put("/cart/items/10/note")
                .cookie(new jakarta.servlet.http.Cookie("foodie_session", "session"))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"newNote\":\"" + "x".repeat(141) + "\"}"))
            .andExpect(status().isBadRequest());
    }
}
