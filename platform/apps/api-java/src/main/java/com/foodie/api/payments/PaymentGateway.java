package com.foodie.api.payments;

import java.time.Instant;

public interface PaymentGateway {
    String provider();

    Charge create(ChargeRequest request);

    Charge fetch(String externalId);

    record ChargeRequest(long orderId, long amountCents, String method, String description, String payerEmail, String idempotencyKey, String notificationUrl) {}

    record Charge(String externalId, String externalReference, long amountCents, String status, String rawStatus,
                  String qrCode, String qrCodeBase64, String ticketUrl, Instant expiresAt) {}
}
