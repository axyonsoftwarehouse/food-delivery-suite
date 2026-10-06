package com.foodie.api.payments;

import com.foodie.api.ApiException;
import com.foodie.api.settings.SettingsCatalog;
import com.foodie.api.settings.SettingsService;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Gateway de Pix estático (BR Code) que não depende de provedor externo (E45). A confirmação é
 * manual, como nos pagamentos offline; serve para operar sem intermediário.
 */
@Component
public class StaticPixGateway implements PaymentGateway {
    private final SettingsService settings;

    public StaticPixGateway(SettingsService settings) {
        this.settings = settings;
    }

    @Override
    public String provider() {
        return "static_pix";
    }

    @Override
    public Charge create(com.foodie.api.payments.accounts.MerchantCredentials credentials, ChargeRequest request) {
        String key = settings.text(SettingsCatalog.PAYMENT_PIX_KEY);
        if (key.isBlank()) throw new ApiException(503, "Chave Pix estática não configurada");
        String payload = brCode(key,
            settings.text(SettingsCatalog.PAYMENT_PIX_MERCHANT),
            settings.text(SettingsCatalog.PAYMENT_PIX_CITY),
            request.amountCents());
        return new Charge("static-" + request.orderId(), String.valueOf(request.orderId()), request.amountCents(),
            "pending", "pending", payload, null, null, null);
    }

    @Override
    public Charge fetch(com.foodie.api.payments.accounts.MerchantCredentials credentials, String externalId) {
        return new Charge(externalId, null, 0, "pending", "pending", null, null, null, null);
    }

    public static String brCode(String key, String merchant, String city, long amountCents) {
        String name = (merchant == null || merchant.isBlank() ? "FOODIE" : merchant).toUpperCase(Locale.ROOT).trim();
        String town = (city == null || city.isBlank() ? "SAO PAULO" : city).toUpperCase(Locale.ROOT).trim();
        if (name.length() > 25) name = name.substring(0, 25);
        if (town.length() > 15) town = town.substring(0, 15);
        StringBuilder payload = new StringBuilder();
        payload.append(tlv("00", "01"));
        payload.append(tlv("26", tlv("00", "br.gov.bcb.pix") + tlv("01", key)));
        payload.append(tlv("52", "0000"));
        payload.append(tlv("53", "986"));
        if (amountCents > 0) payload.append(tlv("54", String.format(Locale.ROOT, "%.2f", amountCents / 100.0)));
        payload.append(tlv("58", "BR"));
        payload.append(tlv("59", name));
        payload.append(tlv("60", town));
        payload.append(tlv("62", tlv("05", "***")));
        String withCrc = payload + "6304";
        return withCrc + crc16(withCrc);
    }

    static String tlv(String id, String value) {
        return id + String.format("%02d", value.length()) + value;
    }

    static String crc16(String data) {
        int crc = 0xFFFF;
        for (int i = 0; i < data.length(); i++) {
            crc ^= (data.charAt(i) << 8);
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return String.format("%04X", crc);
    }
}
