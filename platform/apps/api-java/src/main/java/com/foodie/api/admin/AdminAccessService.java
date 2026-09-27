package com.foodie.api.admin;

import com.foodie.api.ApiException;
import com.foodie.api.auth.PasswordVerifier;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** Regras de papéis e funcionários da administração (E01). */
@Service
public class AdminAccessService {
    private final AdminRoleRepository roles;
    private final PasswordVerifier passwords;

    public AdminAccessService(AdminRoleRepository roles, PasswordVerifier passwords) {
        this.roles = roles;
        this.passwords = passwords;
    }

    public List<AdminRoleRepository.Role> listRoles() {
        return roles.listRoles();
    }

    public AdminRoleRepository.Role createRole(String name, String description, List<String> permissions, Boolean active) {
        String cleanName = cleanName(name);
        if (roles.roleNameExists(cleanName)) throw new ApiException(409, "Já existe um papel com este nome");
        List<String> valid = validate(permissions);
        boolean enabled = active == null || active;
        long id = roles.createRole(cleanName, cleanDescription(description), valid, enabled);
        return new AdminRoleRepository.Role(id, cleanName, cleanDescription(description), valid, enabled);
    }

    public AdminRoleRepository.Role updateRole(long id, String name, String description, List<String> permissions, Boolean active) {
        AdminRoleRepository.Role current = roles.findRole(id).orElseThrow(() -> new ApiException(404, "Papel não encontrado"));
        String cleanName = name == null ? current.name() : cleanName(name);
        if (!cleanName.equals(current.name()) && roles.roleNameExists(cleanName)) {
            throw new ApiException(409, "Já existe um papel com este nome");
        }
        String cleanDescription = description == null ? current.description() : cleanDescription(description);
        List<String> valid = permissions == null ? current.permissions() : validate(permissions);
        boolean enabled = active == null ? current.active() : active;
        roles.updateRole(id, cleanName, cleanDescription, valid, enabled);
        return new AdminRoleRepository.Role(id, cleanName, cleanDescription, valid, enabled);
    }

    public void deleteRole(long id) {
        roles.findRole(id).orElseThrow(() -> new ApiException(404, "Papel não encontrado"));
        if (roles.roleAssigned(id)) throw new ApiException(409, "Há funcionários com este papel");
        roles.deleteRole(id);
    }

    public List<AdminRoleRepository.Employee> listEmployees() {
        return roles.listEmployees();
    }

    /** Um administrador ativo só pode ser suspenso se houver outro administrador ativo. */
    public boolean isLastActiveAdmin(long userId) {
        AdminRoleRepository.Employee target = roles.findEmployee(userId).orElse(null);
        return target != null && !target.suspended() && roles.countActiveAdmins() <= 1;
    }

    public AdminRoleRepository.Employee createEmployee(String name, String email, String password, Long adminRoleId) {
        String cleanName = name == null ? "" : name.strip();
        if (cleanName.length() < 2) throw new ApiException(400, "Nome inválido");
        String normalizedEmail = email == null ? "" : email.toLowerCase(Locale.ROOT).strip();
        if (normalizedEmail.isBlank()) throw new ApiException(400, "Email inválido");
        if (roles.emailExists(normalizedEmail)) throw new ApiException(409, "Já existe um acesso com este email");
        if (adminRoleId != null) roles.findRole(adminRoleId).orElseThrow(() -> new ApiException(400, "Papel não encontrado"));
        long id = roles.createEmployee(cleanName, normalizedEmail, passwords.hash(password), adminRoleId);
        return new AdminRoleRepository.Employee(id, cleanName, normalizedEmail, adminRoleId, false);
    }

    public AdminRoleRepository.Employee updateEmployee(long id, String name, Long adminRoleId) {
        AdminRoleRepository.Employee current = roles.findEmployee(id).orElseThrow(() -> new ApiException(404, "Funcionário não encontrado"));
        String cleanName = name == null ? current.name() : name.strip();
        if (cleanName.length() < 2) throw new ApiException(400, "Nome inválido");
        if (adminRoleId != null) roles.findRole(adminRoleId).orElseThrow(() -> new ApiException(400, "Papel não encontrado"));
        boolean restrictingFullAdmin = current.adminRoleId() == null && adminRoleId != null;
        if (restrictingFullAdmin && roles.countFullAdmins() <= 1) {
            throw new ApiException(409, "É necessário manter ao menos um administrador com acesso total");
        }
        roles.updateEmployee(id, cleanName, adminRoleId);
        return new AdminRoleRepository.Employee(id, cleanName, current.email(), adminRoleId, current.suspended());
    }

    private static String cleanName(String name) {
        String cleanName = name == null ? "" : name.strip();
        if (cleanName.length() < 2 || cleanName.length() > 80) throw new ApiException(400, "Nome do papel inválido");
        return cleanName;
    }

    private static String cleanDescription(String description) {
        String value = description == null ? "" : description.strip();
        return value.length() > 255 ? value.substring(0, 255) : value;
    }

    private static List<String> validate(List<String> permissions) {
        if (permissions == null) return List.of();
        return permissions.stream().filter(AdminPermissions::isKnown).distinct().toList();
    }
}
