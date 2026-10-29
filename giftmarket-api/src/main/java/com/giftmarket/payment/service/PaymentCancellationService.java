package com.giftmarket.payment.service;

import com.giftmarket.order.dto.request.OrderCancelRequest;
import com.giftmarket.order.dto.response.OrderCancelResponse;
import com.giftmarket.payment.exception.PaymentException;
import com.giftmarket.payment.gateway.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PaymentCancellationService {
    private final PaymentCancellationTransactionService transactionService;
    private final PaymentGatewayRegistry gatewayRegistry;

    public OrderCancelResponse cancel(Long userId, Long orderId, OrderCancelRequest request) {
        PaymentCancelStart start = transactionService.start(userId, orderId, request);
        if (start.action() == PaymentCancelStart.Action.COMPLETED) return start.response();
        PaymentGateway gateway = gatewayRegistry.get(start.provider());
        try {
            if (start.action() == PaymentCancelStart.Action.QUERY) {
                GatewayPaymentQueryResult query = gateway.getPayment(start.providerPaymentKey());
                if (query.status() == GatewayPaymentStatus.CANCELED) {
                    return transactionService.completeFromQuery(userId, start.paymentId(), start.cancellationId(), query);
                }
                if (query.status() != GatewayPaymentStatus.PAID) {
                    throw new PaymentException("결제 취소 결과를 확인 중입니다.");
                }
                if (!Objects.equals(query.providerPaymentKey(), start.providerPaymentKey())
                        || !Objects.equals(query.merchantPaymentId(), start.merchantPaymentId())
                        || !Objects.equals(query.amount(), start.amount())
                        || !Objects.equals(query.currency(), start.currency())
                        || !Objects.equals(query.remainingAmount(), start.amount())) {
                    throw new PaymentException("결제 취소 결과를 확인 중입니다.");
                }
                if (start.requestedAt() == null || !start.requestedAt().plusDays(15).isAfter(LocalDateTime.now())) {
                    throw new PaymentException("결제 취소 결과를 확인 중입니다. 운영자에게 문의해주세요.");
                }
            }
            GatewayCancelResult result = gateway.cancel(new GatewayCancelCommand(
                    start.providerPaymentKey(), start.merchantPaymentId(), start.amount(), start.currency(),
                    start.reason(), start.idempotencyKey()));
            return transactionService.complete(userId, start.paymentId(), start.cancellationId(), result);
        } catch (PaymentGatewayDeclinedException exception) {
            transactionService.explicitFailure(userId, start.paymentId(), start.cancellationId(),
                    exception.getFailureCode(), exception.getMessage());
            throw new PaymentException("결제 취소를 완료하지 못했습니다. 잠시 후 다시 시도해주세요.");
        } catch (PaymentGatewayUncertainException exception) {
            throw new PaymentException("결제 취소 결과를 확인 중입니다. 잠시 후 다시 확인해주세요.");
        }
    }
}
