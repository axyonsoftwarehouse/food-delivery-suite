package com.foodie.api.permissions;

import com.foodie.api.ApiException;
import com.foodie.api.auth.PasswordVerifier;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RoleService {
    private static final Set<String> STAFF_ROLES = Set.of("restaurant", "kitchen");

    private final RoleRepository roles;
    private final PasswordVerifier passwords;

    public RoleService(RoleRepository roles, PasswordVerifier passwords) {
        this.roles = roles;
        this.passwords = passwords;
    }

    public List<RoleRepository.Role> list(long restaurantId) {
        return roles.list(restaurantId);
    }

    public RoleRepository.Role create(long restaurantId, String name, List<String> permissions) {
        String cleanName = name == null ? "" : name.strip();
        if (cleanName.length() < 2) throw new ApiException(400, "Nome do papel inválido");
        List<String> valid = validate(permissions);
        long id = roles.create(restaurantId, cleanName, valid);
        return new RoleRepository.Role(id, restaurantId, cleanName, valid);
    }

    public RoleRepository.Role update(long restaurantId, long id, String name, List<String> permissions) {
        RoleRepository.Role current = roles.find(restaurantId, id).orElseThrow(() -> new ApiException(404, "Papel não encontrado"));
        String cleanName = name == null ? current.name() : name.strip();
        if (cleanName.length() < 2) throw new ApiException(400, "Nome do papel inválido");
        List<String> valid = permissions == null ? current.permissions() : validate(permissions);
        roles.update(restaurantId, id, cleanName, valid);
        return new RoleRepository.Role(id, restaurantId, cleanName, valid);
    }

    public void delete(long restaurantId, long id) {
        roles.find(restaurantId, id).orElseThrow(() -> new ApiException(404, "Papel não encontrado"));
        if (roles.assigned(id)) throw new ApiException(409, "Há funcionários com este papel");
        roles.delete(restaurantId, id);
    }

    public List<RoleRepository.Staff> staff(long restaurantId) {
        return roles.staff(restaurantId);
    }

    public RoleRepository.Staff createStaff(long restaurantId, String name, String email, String password, String role, Long staffRoleId) {
        if (role == null || !STAFF_ROLES.contains(role)) throw new ApiException(400, "Papel de acesso inválido");
        if (staffRoleId != null) roles.find(restaurantId, staffRoleId).orElseThrow(() -> new ApiException(400, "Papel não encontrado"));
        String normalizedEmail = email == null ? "" : email.toLowerCase(Locale.ROOT).strip();
        if (roles.emailExists(normalizedEmail)) throw new ApiException(409, "Já existe um acesso com este email");
        long id = roles.createStaff(restaurantId, name == null ? "" : name.strip(), normalizedEmail, passwords.hash(password), role, staffRoleId);
        return new RoleRepository.Staff(id, name == null ? "" : name.strip(), normalizedEmail, role, staffRoleId);
    }

    public void assignStaffRole(long restaurantId, long userId, Long staffRoleId) {
        if (staffRoleId != null) roles.find(restaurantId, staffRoleId).orElseThrow(() -> new ApiException(400, "Papel não encontrado"));
        if (roles.assignStaffRole(restaurantId, userId, staffRoleId) == 0) throw new ApiException(404, "Funcionário não encontrado");
    }

    private static List<String> validate(List<String> permissions) {
        if (permissions == null) return List.of();
        return permissions.stream().filter(Permissions::isKnown).distinct().toList();
    }
}
