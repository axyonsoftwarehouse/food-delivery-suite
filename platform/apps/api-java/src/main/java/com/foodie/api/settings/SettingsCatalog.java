package com.foodie.api.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogo de configurações de sistema (E02). Chaves estáveis, com tipo e valor padrão, para o
 * super-admin controlar o comportamento sem alterar código.
 */
public final class SettingsCatalog {
    private SettingsCatalog() {}

    public static final String BUSINESS_NAME = "business.name";
    public static final String BUSINESS_SUPPORT_EMAIL = "business.support_email";
    public static final String BUSINESS_SUPPORT_PHONE = "business.support_phone";
    public static final String BUSINESS_ADDRESS = "business.address";
    public static final String BUSINESS_CURRENCY = "business.currency";
    public static final String BUSINESS_TIMEZONE = "business.timezone";
    public static final String BUSINESS_COUNTRY = "business.country";

    public static final String ORDER_DELIVERY = "order.delivery_enabled";
    public static final String ORDER_TAKEAWAY = "order.takeaway_enabled";
    public static final String ORDER_DINE_IN = "order.dine_in_enabled";
    public static final String ORDER_POS = "order.pos_enabled";
    public static final String ORDER_SCHEDULED = "order.scheduled_enabled";
    public static final String ORDER_GUEST_CHECKOUT = "order.guest_checkout_enabled";
    public static final String PAYMENT_CASH = "payment.cash_enabled";
    public static final String PAYMENT_CARD = "payment.card_enabled";
    public static final String PAYMENT_PIX = "payment.pix_enabled";
    public static final String PAYMENT_OFFLINE = "payment.offline_enabled";
    public static final String DELIVERY_FREE = "delivery.free_delivery";

    public static final String POLICY_CANCELLATION_ENABLED = "policy.cancellation_enabled";
    public static final String POLICY_CANCELLATION_TEXT = "policy.cancellation_text";
    public static final String POLICY_REFUND_ENABLED = "policy.refund_enabled";
    public static final String POLICY_REFUND_TEXT = "policy.refund_text";
    public static final String POLICY_SHIPPING_TEXT = "policy.shipping_text";
    public static final String POLICY_PRIVACY_TEXT = "policy.privacy_text";
    public static final String POLICY_TERMS_TEXT = "policy.terms_text";

    public static final String MAINTENANCE_ENABLED = "maintenance.enabled";
    public static final String MAINTENANCE_MESSAGE = "maintenance.message";
    public static final String MAINTENANCE_NUMBER = "maintenance.business_number";
    public static final String MAINTENANCE_EMAIL = "maintenance.business_email";

    public static final String WALLET_ENABLED = "wallet.enabled";
    public static final String LOYALTY_ENABLED = "loyalty.enabled";
    public static final String LOYALTY_POINTS_PER_REAL = "loyalty.points_per_real";
    public static final String LOYALTY_EXCHANGE_RATE = "loyalty.exchange_rate";
    public static final String CASHBACK_ENABLED = "cashback.enabled";

    public static final String ANALYTICS_GA = "analytics.google_id";
    public static final String ANALYTICS_GTM = "analytics.gtm_id";
    public static final String ANALYTICS_META = "analytics.meta_pixel";
    public static final String ANALYTICS_TIKTOK = "analytics.tiktok_pixel";
    public static final String SOCIAL_FACEBOOK = "social.facebook";
    public static final String SOCIAL_INSTAGRAM = "social.instagram";
    public static final String SOCIAL_WHATSAPP = "social.whatsapp";

    public static final String RECAPTCHA_SITE_KEY = "integrations.recaptcha_site_key";
    public static final String RECAPTCHA_SECRET = "integrations.recaptcha_secret";
    public static final String OPENAI_API_KEY = "integrations.openai_api_key";
    public static final String STORAGE_DRIVER = "integrations.storage_driver";
    public static final String STORAGE_PUBLIC_BASE_URL = "integrations.storage_public_base_url";

    public static final String PAYMENT_PIX_KEY = "payment.pix_key";
    public static final String PAYMENT_PIX_MERCHANT = "payment.pix_merchant";
    public static final String PAYMENT_PIX_CITY = "payment.pix_city";

    public record Descriptor(String key, String label, String type, String defaultValue, String group) {}

    public static final List<Descriptor> CATALOG = List.of(
        new Descriptor(BUSINESS_NAME, "Nome do negócio", "text", "Foodie", "Negócio"),
        new Descriptor(BUSINESS_SUPPORT_EMAIL, "Email de suporte", "text", "", "Negócio"),
        new Descriptor(BUSINESS_SUPPORT_PHONE, "Telefone de suporte", "text", "", "Negócio"),
        new Descriptor(BUSINESS_ADDRESS, "Endereço", "text", "", "Negócio"),
        new Descriptor(BUSINESS_CURRENCY, "Moeda", "text", "BRL", "Negócio"),
        new Descriptor(BUSINESS_TIMEZONE, "Fuso horário", "text", "America/Fortaleza", "Negócio"),
        new Descriptor(BUSINESS_COUNTRY, "País", "text", "BR", "Negócio"),

        new Descriptor(ORDER_DELIVERY, "Aceitar entrega", "bool", "true", "Operação"),
        new Descriptor(ORDER_TAKEAWAY, "Aceitar retirada", "bool", "true", "Operação"),
        new Descriptor(ORDER_DINE_IN, "Aceitar consumo no local", "bool", "true", "Operação"),
        new Descriptor(ORDER_POS, "Habilitar PDV/balcão", "bool", "true", "Operação"),
        new Descriptor(ORDER_SCHEDULED, "Aceitar pedido agendado", "bool", "true", "Operação"),
        new Descriptor(ORDER_GUEST_CHECKOUT, "Permitir compra sem conta", "bool", "false", "Operação"),
        new Descriptor(PAYMENT_CASH, "Aceitar dinheiro", "bool", "true", "Operação"),
        new Descriptor(PAYMENT_CARD, "Aceitar cartão", "bool", "true", "Operação"),
        new Descriptor(PAYMENT_PIX, "Aceitar Pix", "bool", "true", "Operação"),
        new Descriptor(PAYMENT_OFFLINE, "Aceitar pagamento manual", "bool", "true", "Operação"),
        new Descriptor(DELIVERY_FREE, "Entrega grátis (plataforma)", "bool", "false", "Operação"),

        new Descriptor(POLICY_CANCELLATION_ENABLED, "Exibir política de cancelamento", "bool", "true", "Políticas"),
        new Descriptor(POLICY_CANCELLATION_TEXT, "Texto de cancelamento", "text", "", "Políticas"),
        new Descriptor(POLICY_REFUND_ENABLED, "Exibir política de reembolso", "bool", "true", "Políticas"),
        new Descriptor(POLICY_REFUND_TEXT, "Texto de reembolso", "text", "", "Políticas"),
        new Descriptor(POLICY_SHIPPING_TEXT, "Texto de entrega", "text", "", "Políticas"),
        new Descriptor(POLICY_PRIVACY_TEXT, "Texto de privacidade", "text", "", "Políticas"),
        new Descriptor(POLICY_TERMS_TEXT, "Texto de termos de uso", "text", "", "Políticas"),

        new Descriptor(MAINTENANCE_ENABLED, "Modo manutenção ativo", "bool", "false", "Manutenção"),
        new Descriptor(MAINTENANCE_MESSAGE, "Mensagem de manutenção", "text", "Estamos em manutenção. Voltamos em breve.", "Manutenção"),
        new Descriptor(MAINTENANCE_NUMBER, "Telefone durante a manutenção", "text", "", "Manutenção"),
        new Descriptor(MAINTENANCE_EMAIL, "Email durante a manutenção", "text", "", "Manutenção"),

        new Descriptor(WALLET_ENABLED, "Habilitar carteira do cliente", "bool", "true", "Programa"),
        new Descriptor(LOYALTY_ENABLED, "Habilitar pontos de fidelidade", "bool", "true", "Programa"),
        new Descriptor(LOYALTY_POINTS_PER_REAL, "Pontos por R$ 1 gasto", "int", "1", "Programa"),
        new Descriptor(LOYALTY_EXCHANGE_RATE, "Pontos necessários por R$ 1 de desconto", "int", "100", "Programa"),
        new Descriptor(CASHBACK_ENABLED, "Habilitar cashback", "bool", "false", "Programa"),

        new Descriptor(ANALYTICS_GA, "Google Analytics (ID)", "text", "", "Analytics"),
        new Descriptor(ANALYTICS_GTM, "Google Tag Manager (ID)", "text", "", "Analytics"),
        new Descriptor(ANALYTICS_META, "Meta Pixel (ID)", "text", "", "Analytics"),
        new Descriptor(ANALYTICS_TIKTOK, "TikTok Pixel (ID)", "text", "", "Analytics"),
        new Descriptor(SOCIAL_FACEBOOK, "Facebook", "text", "", "Analytics"),
        new Descriptor(SOCIAL_INSTAGRAM, "Instagram", "text", "", "Analytics"),
        new Descriptor(SOCIAL_WHATSAPP, "WhatsApp", "text", "", "Analytics"),

        new Descriptor(RECAPTCHA_SITE_KEY, "reCAPTCHA site key", "text", "", "Integrações"),
        new Descriptor(RECAPTCHA_SECRET, "reCAPTCHA secret", "text", "", "Integrações"),
        new Descriptor(OPENAI_API_KEY, "OpenAI API key", "text", "", "Integrações"),
        new Descriptor(STORAGE_DRIVER, "Driver de storage (local/s3)", "text", "local", "Integrações"),
        new Descriptor(STORAGE_PUBLIC_BASE_URL, "URL pública do storage", "text", "", "Integrações"),
        new Descriptor(PAYMENT_PIX_KEY, "Chave Pix estática", "text", "", "Integrações"),
        new Descriptor(PAYMENT_PIX_MERCHANT, "Nome do recebedor Pix", "text", "FOODIE", "Integrações"),
        new Descriptor(PAYMENT_PIX_CITY, "Cidade do recebedor Pix", "text", "SAO PAULO", "Integrações")
    );

    public static final Map<String, Descriptor> BY_KEY;

    static {
        Map<String, Descriptor> byKey = new LinkedHashMap<>();
        for (Descriptor descriptor : CATALOG) byKey.put(descriptor.key(), descriptor);
        BY_KEY = Map.copyOf(byKey);
    }

    public static boolean isKnown(String key) {
        return BY_KEY.containsKey(key);
    }
}
