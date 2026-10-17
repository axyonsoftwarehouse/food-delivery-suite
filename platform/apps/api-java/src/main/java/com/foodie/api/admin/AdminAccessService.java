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

    public AdminRoleRepository.Role createRole(long actorId, String name, String description, List<String> permissions, Boolean active) {
        String cleanName = cleanName(name);
        if (roles.roleNameExists(cleanName)) throw new ApiException(409, "Já existe um papel com este nome");
        List<String> valid = validate(permissions);
        requireGrantable(actorId, valid);
        boolean enabled = active == null || active;
        long id = roles.createRole(cleanName, cleanDescription(description), valid, enabled);
        return new AdminRoleRepository.Role(id, cleanName, cleanDescription(description), valid, enabled);
    }

    public AdminRoleRepository.Role updateRole(long actorId, long id, String name, String description, List<String> permissions, Boolean active) {
        AdminRoleRepository.Role current = roles.findRole(id).orElseThrow(() -> new ApiException(404, "Papel não encontrado"));
        if (current.fullAccess()) throw new ApiException(409, "O papel de acesso total é do sistema e não pode ser alterado");
        // Um admin não pode aumentar o próprio papel: isso tornaria `admin.manage` uma autopromoção.
        if (roles.roleAssignedTo(id, actorId)) throw new ApiException(409, "Não é possível alterar o próprio nível de acesso");
        String cleanName = name == null ? current.name() : cleanName(name);
        if (!cleanName.equals(current.name()) && roles.roleNameExists(cleanName)) {
            throw new ApiException(409, "Já existe um papel com este nome");
        }
        String cleanDescription = description == null ? current.description() : cleanDescription(description);
        List<String> valid = permissions == null ? current.permissions() : validate(permissions);
        // Só as permissões novas precisam estar no conjunto de quem edita; remover é sempre permitido.
        requireGrantable(actorId, valid.stream().filter(permission -> !current.permissions().contains(permission)).toList());
        boolean enabled = active == null ? current.active() : active;
        roles.updateRole(id, cleanName, cleanDescription, valid, enabled);
        return new AdminRoleRepository.Role(id, cleanName, cleanDescription, valid, enabled);
    }

    public void deleteRole(long id) {
        AdminRoleRepository.Role current = roles.findRole(id).orElseThrow(() -> new ApiException(404, "Papel não encontrado"));
        if (current.fullAccess()) throw new ApiException(409, "O papel de acesso total é do sistema e não pode ser excluído");
        if (roles.roleAssigned(id)) throw new ApiException(409, "Há funcionários com este papel");
        try {
            roles.deleteRole(id);
        } catch (org.springframework.dao.DataIntegrityViolationException concurrentAssignment) {
            throw new ApiException(409, "Há funcionários com este papel");
        }
    }

    public List<AdminRoleRepository.Employee> listEmployees() {
        return roles.listEmployees();
    }

    /** Um administrador ativo só pode ser suspenso se houver outro administrador ativo. */
    public boolean isLastActiveAdmin(long userId) {
        AdminRoleRepository.Employee target = roles.findEmployee(userId).orElse(null);
        return target != null && !target.suspended() && roles.countActiveAdmins() <= 1;
    }

    public AdminRoleRepository.Employee createEmployee(long actorId, String name, String email, String password, Long adminRoleId) {
        String cleanName = name == null ? "" : name.strip();
        if (cleanName.length() < 2) throw new ApiException(400, "Nome inválido");
        String normalizedEmail = email == null ? "" : email.toLowerCase(Locale.ROOT).strip();
        if (normalizedEmail.isBlank()) throw new ApiException(400, "Email inválido");
        if (roles.emailExists(normalizedEmail)) throw new ApiException(409, "Já existe um acesso com este email");
        AdminRoleRepository.Role target = targetRole(adminRoleId);
        if (target.fullAccess() && !isFullAdmin(actorId)) {
            throw new ApiException(403, "Só um administrador com acesso total pode criar outro acesso total");
        }
        requireGrantable(actorId, target.permissions());
        long id = roles.createEmployee(cleanName, normalizedEmail, passwords.hash(password), target.id());
        return new AdminRoleRepository.Employee(id, cleanName, normalizedEmail, target.id(), false);
    }

    public AdminRoleRepository.Employee updateEmployee(long actorId, long id, String name, Long adminRoleId) {
        AdminRoleRepository.Employee current = roles.findEmployee(id).orElseThrow(() -> new ApiException(404, "Funcionário não encontrado"));
        String cleanName = name == null ? current.name() : name.strip();
        if (cleanName.length() < 2) throw new ApiException(400, "Nome inválido");
        AdminRoleRepository.Role target = targetRole(adminRoleId);
        if (actorId == id && !java.util.Objects.equals(current.adminRoleId(), target.id())) {
            throw new ApiException(409, "Não é possível alterar o próprio nível de acesso");
        }
        if (target.fullAccess() && !isFullAdmin(actorId)) {
            throw new ApiException(403, "Só um administrador com acesso total pode conceder acesso total");
        }
        requireGrantable(actorId, target.permissions());
        boolean currentFull = current.adminRoleId() != null
            && roles.findRole(current.adminRoleId()).map(AdminRoleRepository.Role::fullAccess).orElse(false);
        if (currentFull && !target.fullAccess() && roles.countFullAdmins() <= 1) {
            throw new ApiException(409, "É necessário manter ao menos um administrador com acesso total");
        }
        roles.updateEmployee(id, cleanName, target.id());
        return new AdminRoleRepository.Employee(id, cleanName, current.email(), target.id(), current.suspended());
    }

    /** Sem papel informado, usa o papel de acesso total explícito; sem ele, falha fechado. */
    private AdminRoleRepository.Role targetRole(Long adminRoleId) {
        AdminRoleRepository.Role target = adminRoleId == null
            ? roles.findFullAccessRole().orElseThrow(() -> new ApiException(409, "O papel de acesso total não está configurado"))
            : roles.findRole(adminRoleId).orElseThrow(() -> new ApiException(400, "Papel não encontrado"));
        if (!target.active()) throw new ApiException(400, "Papel inativo");
        return target;
    }

    private boolean isFullAdmin(long userId) {
        return roles.findRoleForUser(userId).map(AdminRoleRepository.Role::fullAccess).orElse(false);
    }

    /** Um admin só concede permissões que ele próprio tem; evita escalada indireta via papel novo. */
    private void requireGrantable(long actorId, List<String> permissions) {
        java.util.Set<String> allowed = actorPermissions(actorId);
        for (String permission : permissions) {
            if (!allowed.contains(permission)) {
                throw new ApiException(403, "Você não pode conceder uma permissão que não possui");
            }
        }
    }

    private java.util.Set<String> actorPermissions(long actorId) {
        return roles.findRoleForUser(actorId)
            .map(role -> {
                if (!role.active()) return java.util.Set.<String>of();
                if (role.fullAccess()) return AdminPermissions.all();
                return role.permissions().stream().filter(AdminPermissions::isKnown)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            })
            .orElseGet(java.util.Set::of);
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
