package com.foodie.api.permissions;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {
    private final RoleRepository roles;

    public PermissionService(RoleRepository roles) {
        this.roles = roles;
    }

    /** Conjunto efetivo de permissões: papel de funcionário quando houver, senão o padrão do papel base. */
    public Set<String> effective(User user) {
        if ("admin".equals(user.role())) return Permissions.all();
        return roles.findForUser(user.id())
            .map(role -> Set.copyOf(role.permissions()))
            .orElseGet(() -> Permissions.defaultsFor(user.role()));
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
