package com.giftmarket.payment.gateway;

public class PaymentGatewayUncertainException
        extends RuntimeException {

    public PaymentGatewayUncertainException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
