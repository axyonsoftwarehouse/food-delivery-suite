package com.foodie.api.admin;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Catálogo de permissões do lado da administração. Chaves estáveis usadas pela API e pela
 * interface para papéis de funcionários do admin (E01).
 */
public final class AdminPermissions {
    private AdminPermissions() {}

    public static final String DASHBOARD_VIEW = "dashboard.view";
    public static final String ORDERS_MANAGE = "orders.manage";
    public static final String CATALOG_MANAGE = "catalog.manage";
    public static final String ZONES_MANAGE = "zones.manage";
    public static final String RESTAURANTS_MANAGE = "restaurants.manage";
    public static final String COURIERS_MANAGE = "couriers.manage";
    public static final String CUSTOMERS_MANAGE = "customers.manage";
    public static final String USERS_SUSPEND = "users.suspend";
    public static final String FINANCE_VIEW = "finance.view";
    public static final String FINANCE_MANAGE = "finance.manage";
    public static final String REPORTS_VIEW = "reports.view";
    public static final String PROMOTIONS_MANAGE = "promotions.manage";
    public static final String TEAM_MANAGE = "team.manage";
    public static final String ADMIN_MANAGE = "admin.manage";
    public static final String SETTINGS_MANAGE = "settings.manage";
    public static final String AUDIT_VIEW = "audit.view";
    public static final String SUPPORT_VIEW = "support.view";
    public static final String SUPPORT_ACT = "support.act";

    public record Descriptor(String key, String label, String group) {}

    public static final List<Descriptor> CATALOG = List.of(
        new Descriptor(DASHBOARD_VIEW, "Ver painel e indicadores", "Visão geral"),
        new Descriptor(ORDERS_MANAGE, "Gerenciar pedidos", "Pedidos"),
        new Descriptor(CATALOG_MANAGE, "Gerenciar catálogo", "Catálogo"),
        new Descriptor(ZONES_MANAGE, "Gerenciar zonas e cobertura", "Operação"),
        new Descriptor(RESTAURANTS_MANAGE, "Gerenciar restaurantes", "Operação"),
        new Descriptor(COURIERS_MANAGE, "Gerenciar entregadores", "Operação"),
        new Descriptor(CUSTOMERS_MANAGE, "Gerenciar clientes", "Pessoas"),
        new Descriptor(USERS_SUSPEND, "Suspender e reativar acessos", "Pessoas"),
        new Descriptor(FINANCE_VIEW, "Ver financeiro", "Financeiro"),
        new Descriptor(FINANCE_MANAGE, "Gerenciar financeiro", "Financeiro"),
        new Descriptor(REPORTS_VIEW, "Ver relatórios", "Relatórios"),
        new Descriptor(PROMOTIONS_MANAGE, "Gerenciar promoções", "Promoções"),
        new Descriptor(TEAM_MANAGE, "Gerenciar equipe do restaurante", "Equipe"),
        new Descriptor(ADMIN_MANAGE, "Administrar papéis e funcionários", "Administração"),
        new Descriptor(SETTINGS_MANAGE, "Gerenciar configurações", "Administração"),
        new Descriptor(AUDIT_VIEW, "Ver trilha administrativa", "Administração"),
        new Descriptor(SUPPORT_VIEW, "Ver lojas no modo suporte", "Suporte"),
        new Descriptor(SUPPORT_ACT, "Intervir em lojas no modo suporte", "Suporte")
    );

    private static final Set<String> ALL = CATALOG.stream().map(Descriptor::key).collect(Collectors.toUnmodifiableSet());

    public static Set<String> all() {
        return ALL;
    }

    public static boolean isKnown(String key) {
        return ALL.contains(key);
    }
}
