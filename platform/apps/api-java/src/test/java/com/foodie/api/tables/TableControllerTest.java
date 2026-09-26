package com.foodie.api.tables;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TableController.class)
class TableControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private TableService tables;

    @MockitoBean
    private PermissionService permissions;

    @Test
    void ownerListsTables() throws Exception {
        when(auth.requireUser("s", "restaurant")).thenReturn(new User(5, "Dono", "dono@demo.local", "restaurant", 7L));
        when(tables.list(7L)).thenReturn(List.of(Map.of("id", 2L, "number", "10", "capacity", 4, "active", true)));

        mvc.perform(get("/restaurant/tables").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].number").value("10"));
    }

    @Test
    void createsTable() throws Exception {
        when(auth.requireUser("s", "restaurant")).thenReturn(new User(5, "Dono", "dono@demo.local", "restaurant", 7L));
        when(tables.create(7L, "12", 4)).thenReturn(Map.of("id", 9L, "restaurantId", 7L, "number", "12", "capacity", 4, "active", true));

        mvc.perform(post("/restaurant/tables").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"number\":\"12\",\"capacity\":4}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.number").value("12"));
    }

    @Test
    void withoutTablesPermissionIsForbidden() throws Exception {
        when(auth.requireUser("s", "restaurant")).thenReturn(new User(5, "Dono", "dono@demo.local", "restaurant", 7L));
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(any(User.class), eq(Permissions.TABLES_MANAGE));

        mvc.perform(get("/restaurant/tables").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isForbidden());
    }
}
