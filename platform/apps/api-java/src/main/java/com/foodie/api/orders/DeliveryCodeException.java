package com.foodie.api.orders;

import com.foodie.api.ApiException;

/**
 * Erro de confirmação por código. `OrderService.changeStatus` o declara em `noRollbackFor`: a tentativa
 * errada gravada antes do erro precisa sobreviver, senão o limite de tentativas nunca seria atingido.
 */
public class DeliveryCodeException extends ApiException {
    public DeliveryCodeException(String message) {
        super(409, message);
    }
}
