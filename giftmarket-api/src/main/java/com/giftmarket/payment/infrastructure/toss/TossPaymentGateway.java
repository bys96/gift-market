package com.giftmarket.payment.infrastructure.toss;

import com.giftmarket.payment.entity.PaymentProvider;
import com.giftmarket.payment.gateway.GatewayConfirmCommand;
import com.giftmarket.payment.gateway.GatewayCancelCommand;
import com.giftmarket.payment.gateway.GatewayCancelResult;
import com.giftmarket.payment.gateway.GatewayConfirmResult;
import com.giftmarket.payment.gateway.GatewayPaymentQueryResult;
import com.giftmarket.payment.gateway.GatewayPaymentStatus;
import com.giftmarket.payment.gateway.PaymentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TossPaymentGateway implements PaymentGateway {

    private final TossPaymentClient tossPaymentClient;
    private final TossPaymentMapper tossPaymentMapper;

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.TOSS;
    }

    @Override
    public GatewayConfirmResult confirm(
            GatewayConfirmCommand command
    ) {
        return tossPaymentMapper.toConfirmResult(
                tossPaymentClient.confirm(command)
        );
    }

    @Override
    public GatewayPaymentQueryResult getPayment(
            String providerPaymentKey
    ) {
        return tossPaymentClient
                .getPayment(providerPaymentKey)
                .map(tossPaymentMapper::toQueryResult)
                .orElseGet(() ->
                        new GatewayPaymentQueryResult(
                                GatewayPaymentStatus.UNKNOWN,
                                providerPaymentKey,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                java.util.List.of()
                        )
                );
    }

    @Override
    public GatewayPaymentQueryResult getPaymentByOrderId(String merchantPaymentId) {
        return tossPaymentClient.getPaymentByOrderId(merchantPaymentId)
                .map(tossPaymentMapper::toQueryResult)
                .orElseGet(() -> unknown(null));
    }

    private GatewayPaymentQueryResult unknown(String providerPaymentKey) {
        return new GatewayPaymentQueryResult(
                GatewayPaymentStatus.UNKNOWN, providerPaymentKey, null, null, null, null,
                null, null, null, null, null, null, null, java.util.List.of());
    }

    @Override
    public GatewayCancelResult cancel(GatewayCancelCommand command) {
        return tossPaymentMapper.toCancelResult(tossPaymentClient.cancel(command), command);
    }
}
