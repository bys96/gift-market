package com.giftmarket.payment.service;

import com.giftmarket.payment.dto.request.PaymentConfirmRequest;
import com.giftmarket.payment.dto.response.ExchangeShippingPaymentResponse;
import com.giftmarket.payment.exception.PaymentException;
import com.giftmarket.payment.gateway.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExchangeShippingPaymentService {
    private final ExchangeShippingPaymentTransactionService transactionService;
    private final PaymentGatewayRegistry gatewayRegistry;

    public ExchangeShippingPaymentResponse prepare(Long userId, Long requestId) {
        return transactionService.prepare(userId, requestId);
    }

    public ExchangeShippingPaymentResponse get(Long userId, Long requestId) {
        return transactionService.get(userId, requestId);
    }

    public ExchangeShippingPaymentResponse confirm(Long userId, Long requestId, PaymentConfirmRequest request) {
        ExchangeShippingPaymentStart start = transactionService.startConfirm(userId, requestId, request);
        if (start.action() == ExchangeShippingPaymentStart.Action.COMPLETED) return start.response();
        PaymentGateway gateway = gatewayRegistry.get(start.provider());
        try {
            GatewayConfirmResult result = gateway.confirm(new GatewayConfirmCommand(start.providerPaymentKey(),
                    start.providerOrderId(), start.amount(), "KRW", start.idempotencyKey()));
            if (result.status() == GatewayPaymentStatus.PAID) return transactionService.apply(start.paymentId(), result);
            if (result.status() == GatewayPaymentStatus.FAILED || result.status() == GatewayPaymentStatus.CANCELED) {
                transactionService.fail(start.paymentId(), "PAYMENT_DECLINED", "결제 승인이 거절되었습니다.", result.providerStatus());
                throw new PaymentException("결제 승인이 거절되었습니다. 기한 내 다시 시도해주세요.");
            }
            return transactionService.get(userId, requestId);
        } catch (PaymentGatewayDeclinedException exception) {
            transactionService.fail(start.paymentId(), exception.getFailureCode(), exception.getMessage(), null);
            throw new PaymentException("결제 승인이 거절되었습니다. 기한 내 다시 시도해주세요.");
        } catch (PaymentGatewayUncertainException exception) {
            return transactionService.get(userId, requestId);
        }
    }
}
