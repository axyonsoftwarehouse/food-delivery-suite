package com.foodie.api.admin;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Permissões efetivas da administração. Um administrador sem papel restrito tem acesso total;
 * um administrador com papel restrito usa exatamente as permissões do papel. Papel inativo revoga
 * o acesso.
 */
@Service
public class AdminPermissionService {
    private final AdminRoleRepository roles;

    public AdminPermissionService(AdminRoleRepository roles) {
        this.roles = roles;
    }

    public Set<String> effective(User user) {
        if (!"admin".equals(user.role())) return Set.of();
        return roles.findRoleForUser(user.id())
            .map(role -> role.active()
                ? role.permissions().stream().filter(AdminPermissions::isKnown).collect(java.util.stream.Collectors.toUnmodifiableSet())
                : Set.<String>of())
            .orElseGet(AdminPermissions::all);
    }

    public boolean has(User user, String permission) {
        return effective(user).contains(permission);
    }

    public void require(User user, String... permissions) {
        Set<String> current = effective(user);
        for (String permission : permissions) {
            if (current.contains(permission)) return;
        }
        throw new ApiException(403, "Acesso não autorizado");
    }
}
