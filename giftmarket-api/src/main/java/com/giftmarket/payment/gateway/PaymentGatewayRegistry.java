package com.giftmarket.payment.gateway;

import com.giftmarket.payment.entity.PaymentProvider;
import com.giftmarket.payment.exception.PaymentException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentGatewayRegistry {

    private final Map<PaymentProvider, PaymentGateway> gateways;

    public PaymentGatewayRegistry(
            List<PaymentGateway> paymentGateways
    ) {
        this.gateways = new EnumMap<>(PaymentProvider.class);

        for (PaymentGateway paymentGateway : paymentGateways) {
            this.gateways.put(
                    paymentGateway.provider(),
                    paymentGateway
            );
        }
    }

    public PaymentGateway get(
            PaymentProvider provider
    ) {
        PaymentGateway paymentGateway = gateways.get(provider);

        if (paymentGateway == null) {
            throw new PaymentException(
                    "현재 사용할 수 없는 결제 서비스입니다."
            );
        }

        return paymentGateway;
    }
}
