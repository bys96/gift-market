package com.giftmarket.payment.gateway;

import com.giftmarket.payment.entity.PaymentProvider;

public interface PaymentGateway {

    PaymentProvider provider();

    GatewayConfirmResult confirm(
            GatewayConfirmCommand command
    );

    GatewayPaymentQueryResult getPayment(
            String providerPaymentKey
    );

    GatewayPaymentQueryResult getPaymentByOrderId(String merchantPaymentId);

    GatewayCancelResult cancel(GatewayCancelCommand command);
}
