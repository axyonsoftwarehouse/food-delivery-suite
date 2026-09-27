package com.foodie.api.permissions;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Catálogo de permissões do lado do restaurante. As chaves são estáveis e usadas
 * tanto pela API quanto pela interface (papéis de funcionário).
 */
public final class Permissions {
    private Permissions() {}

    public static final String ORDERS_VIEW = "orders.view";
    public static final String ORDERS_ACCEPT = "orders.accept";
    public static final String ORDERS_READY = "orders.ready";
    public static final String ORDERS_REJECT = "orders.reject";
    public static final String CATALOG_MANAGE = "catalog.manage";
    public static final String HOURS_MANAGE = "hours.manage";
    public static final String TABLES_MANAGE = "tables.manage";
    public static final String POS_MANAGE = "pos.manage";
    public static final String INVENTORY_MANAGE = "inventory.manage";
    public static final String STAFF_MANAGE = "staff.manage";
    public static final String REPORTS_VIEW = "reports.view";
    public static final String PAYMENTS_MANAGE = "payments.manage";
    public static final String PROMOTIONS_MANAGE = "promotions.manage";
    public static final String SETTINGS_MANAGE = "settings.manage";

    public record Descriptor(String key, String label, String group) {}

    public static final List<Descriptor> CATALOG = List.of(
        new Descriptor(ORDERS_VIEW, "Ver pedidos", "Pedidos"),
        new Descriptor(ORDERS_ACCEPT, "Aceitar pedidos", "Pedidos"),
        new Descriptor(ORDERS_READY, "Marcar pronto", "Pedidos"),
        new Descriptor(ORDERS_REJECT, "Recusar pedidos", "Pedidos"),
        new Descriptor(CATALOG_MANAGE, "Gerenciar catálogo", "Catálogo"),
        new Descriptor(HOURS_MANAGE, "Gerenciar horários", "Operação"),
        new Descriptor(TABLES_MANAGE, "Gerenciar mesas", "Operação"),
        new Descriptor(POS_MANAGE, "Usar o PDV/balcão", "Operação"),
        new Descriptor(INVENTORY_MANAGE, "Gerenciar estoque e fornecedores", "Operação"),
        new Descriptor(STAFF_MANAGE, "Gerenciar equipe", "Equipe"),
        new Descriptor(REPORTS_VIEW, "Ver relatórios", "Relatórios"),
        new Descriptor(PAYMENTS_MANAGE, "Gerenciar pagamentos", "Financeiro"),
        new Descriptor(PROMOTIONS_MANAGE, "Gerenciar promoções", "Promoções"),
        new Descriptor(SETTINGS_MANAGE, "Gerenciar configurações", "Configurações")
    );

    private static final Set<String> ALL = CATALOG.stream().map(Descriptor::key).collect(Collectors.toUnmodifiableSet());

    public static Set<String> all() {
        return ALL;
    }

    /** Permissões padrão quando o usuário não tem papel de funcionário atribuído. */
    public static Set<String> defaultsFor(String role) {
        return switch (role) {
            case "admin", "restaurant" -> ALL;
            case "kitchen" -> Set.of(ORDERS_VIEW, ORDERS_ACCEPT, ORDERS_READY, ORDERS_REJECT);
            default -> Set.of();
        };
    }

    public static boolean isKnown(String key) {
        return ALL.contains(key);
    }
}
