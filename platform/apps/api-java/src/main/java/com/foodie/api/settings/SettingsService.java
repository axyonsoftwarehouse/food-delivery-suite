package com.foodie.api.settings;

import com.foodie.api.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Configurações de sistema (E02): leitura com padrões, validação e escrita. */
@Service
public class SettingsService {
    private final SettingsRepository repository;

    public SettingsService(SettingsRepository repository) {
        this.repository = repository;
    }

    public Map<String, String> merged() {
        Map<String, String> values = new LinkedHashMap<>();
        for (SettingsCatalog.Descriptor descriptor : SettingsCatalog.CATALOG) {
            values.put(descriptor.key(), descriptor.defaultValue());
        }
        values.putAll(repository.storedValues());
        return values;
    }

    public String text(String key) {
        SettingsCatalog.Descriptor descriptor = SettingsCatalog.BY_KEY.get(key);
        String fallback = descriptor == null ? "" : descriptor.defaultValue();
        String value = merged().get(key);
        return value == null ? fallback : value;
    }

    public boolean bool(String key) {
        return "true".equalsIgnoreCase(text(key)) || "1".equals(text(key));
    }

    public int intValue(String key) {
        try {
            return Integer.parseInt(text(key).strip());
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    public boolean maintenanceActive() {
        return bool(SettingsCatalog.MAINTENANCE_ENABLED) || repository.hasActiveWindow();
    }

    public String maintenanceMessage() {
        String message = text(SettingsCatalog.MAINTENANCE_MESSAGE);
        return message.isBlank() ? "Estamos em manutenção. Voltamos em breve." : message;
    }

    public List<Map<String, Object>> adminView() {
        Map<String, String> values = merged();
        return SettingsCatalog.CATALOG.stream().map(descriptor -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("key", descriptor.key());
            entry.put("label", descriptor.label());
            entry.put("type", descriptor.type());
            entry.put("group", descriptor.group());
            entry.put("value", values.getOrDefault(descriptor.key(), descriptor.defaultValue()));
            return entry;
        }).toList();
    }

    public void update(Map<String, String> incoming) {
        if (incoming == null) return;
        for (Map.Entry<String, String> entry : incoming.entrySet()) {
            SettingsCatalog.Descriptor descriptor = SettingsCatalog.BY_KEY.get(entry.getKey());
            if (descriptor == null) throw new ApiException(400, "Configuração desconhecida: " + entry.getKey());
            repository.upsert(descriptor.key(), normalize(descriptor, entry.getValue()));
        }
    }

    public Map<String, Object> publicConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        Map<String, Object> business = new LinkedHashMap<>();
        business.put("name", text(SettingsCatalog.BUSINESS_NAME));
        business.put("supportEmail", text(SettingsCatalog.BUSINESS_SUPPORT_EMAIL));
        business.put("supportPhone", text(SettingsCatalog.BUSINESS_SUPPORT_PHONE));
        business.put("address", text(SettingsCatalog.BUSINESS_ADDRESS));
        business.put("currency", text(SettingsCatalog.BUSINESS_CURRENCY));
        business.put("timezone", text(SettingsCatalog.BUSINESS_TIMEZONE));
        business.put("country", text(SettingsCatalog.BUSINESS_COUNTRY));
        config.put("business", business);

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("delivery", bool(SettingsCatalog.ORDER_DELIVERY));
        order.put("takeaway", bool(SettingsCatalog.ORDER_TAKEAWAY));
        order.put("dineIn", bool(SettingsCatalog.ORDER_DINE_IN));
        order.put("pos", bool(SettingsCatalog.ORDER_POS));
        order.put("scheduled", bool(SettingsCatalog.ORDER_SCHEDULED));
        order.put("guestCheckout", bool(SettingsCatalog.ORDER_GUEST_CHECKOUT));
        config.put("order", order);

        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("cash", bool(SettingsCatalog.PAYMENT_CASH));
        payment.put("card", bool(SettingsCatalog.PAYMENT_CARD));
        payment.put("pix", bool(SettingsCatalog.PAYMENT_PIX));
        payment.put("offline", bool(SettingsCatalog.PAYMENT_OFFLINE));
        config.put("payment", payment);

        Map<String, Object> delivery = new LinkedHashMap<>();
        delivery.put("freeDelivery", bool(SettingsCatalog.DELIVERY_FREE));
        config.put("delivery", delivery);

        Map<String, Object> policies = new LinkedHashMap<>();
        policies.put("cancellationEnabled", bool(SettingsCatalog.POLICY_CANCELLATION_ENABLED));
        policies.put("cancellationText", text(SettingsCatalog.POLICY_CANCELLATION_TEXT));
        policies.put("refundEnabled", bool(SettingsCatalog.POLICY_REFUND_ENABLED));
        policies.put("refundText", text(SettingsCatalog.POLICY_REFUND_TEXT));
        policies.put("shippingText", text(SettingsCatalog.POLICY_SHIPPING_TEXT));
        policies.put("privacyText", text(SettingsCatalog.POLICY_PRIVACY_TEXT));
        policies.put("termsText", text(SettingsCatalog.POLICY_TERMS_TEXT));
        config.put("policies", policies);

        Map<String, Object> maintenance = new LinkedHashMap<>();
        maintenance.put("active", maintenanceActive());
        maintenance.put("message", maintenanceMessage());
        maintenance.put("businessNumber", text(SettingsCatalog.MAINTENANCE_NUMBER));
        maintenance.put("businessEmail", text(SettingsCatalog.MAINTENANCE_EMAIL));
        config.put("maintenance", maintenance);

        Map<String, Object> analytics = new LinkedHashMap<>();
        analytics.put("googleId", text(SettingsCatalog.ANALYTICS_GA));
        analytics.put("gtmId", text(SettingsCatalog.ANALYTICS_GTM));
        analytics.put("metaPixel", text(SettingsCatalog.ANALYTICS_META));
        analytics.put("tiktokPixel", text(SettingsCatalog.ANALYTICS_TIKTOK));
        config.put("analytics", analytics);

        Map<String, Object> social = new LinkedHashMap<>();
        social.put("facebook", text(SettingsCatalog.SOCIAL_FACEBOOK));
        social.put("instagram", text(SettingsCatalog.SOCIAL_INSTAGRAM));
        social.put("whatsapp", text(SettingsCatalog.SOCIAL_WHATSAPP));
        config.put("social", social);

        Map<String, Object> secret = new LinkedHashMap<>();
        secret.put("recaptchaSiteKey", text(SettingsCatalog.RECAPTCHA_SITE_KEY));
        config.put("security", secret);

        return config;
    }

    private static String normalize(SettingsCatalog.Descriptor descriptor, String raw) {
        String value = raw == null ? "" : raw;
        return switch (descriptor.type()) {
            case "bool" -> {
                String lower = value.strip().toLowerCase(Locale.ROOT);
                yield switch (lower) {
                    case "true", "1", "on", "yes" -> "true";
                    case "false", "0", "off", "no", "" -> "false";
                    default -> throw new ApiException(400, "Valor inválido para " + descriptor.label());
                };
            }
            case "int" -> {
                try { Long.parseLong(value.strip()); } catch (NumberFormatException error) {
                    throw new ApiException(400, "Valor inválido para " + descriptor.label());
                }
                yield value.strip();
            }
            default -> {
                if (value.length() > 1000) throw new ApiException(400, "Texto muito longo para " + descriptor.label());
                yield value;
            }
        };
    }
}
