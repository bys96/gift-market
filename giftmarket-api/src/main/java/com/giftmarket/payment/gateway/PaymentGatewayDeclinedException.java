package com.giftmarket.payment.gateway;

import lombok.Getter;

@Getter
public class PaymentGatewayDeclinedException
        extends RuntimeException {

    private final String failureCode;

    public PaymentGatewayDeclinedException(
            String failureCode,
            String message
    ) {
        super(message);
        this.failureCode = failureCode;
    }
}
