package com.foodie.api.payments.accounts;

/** Credenciais da conta Mercado Pago de uma loja, já decifradas e renovadas. Nunca saem da API. */
public record MerchantCredentials(long accountId, long restaurantId, String accessToken, String publicKey, String providerUserId) {}
