package com.giftmarket.payment.service;

import com.giftmarket.payment.exception.PaymentException;
import com.giftmarket.payment.gateway.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OrderCancellationRefundExecutionService {

    private final PartialPaymentCancellationTransactionService transactions;
    private final PaymentGatewayRegistry gatewayRegistry;

    public void execute(Long cancellationId) {
        PartialCancellationStart start = transactions.start(cancellationId);
        if (start.action() != PartialCancellationStart.Action.EXECUTE) return;

        PaymentGateway gateway = gatewayRegistry.get(start.provider());
        try {
            GatewayPaymentQueryResult current = gateway.getPayment(start.providerPaymentKey());
            validateCurrentPayment(start, current);
            if (!Boolean.TRUE.equals(current.partialCancelable())) {
                fail(start, "NOT_PARTIAL_CANCELABLE_PAYMENT", "부분취소를 지원하지 않는 결제수단입니다.");
                return;
            }
            GatewayCancelResult result = gateway.cancel(GatewayCancelCommand.partial(
                    start.providerPaymentKey(), start.merchantPaymentId(), start.originalAmount(),
                    start.currency(), start.reason(), start.idempotencyKey(), start.cancelAmount()));
            transactions.complete(start, result);
        } catch (PaymentGatewayDeclinedException exception) {
            fail(start, exception.getFailureCode(), exception.getMessage());
        } catch (PaymentGatewayUncertainException exception) {
            // PG에서 실제 취소가 성공했을 수 있으므로 REQUESTED/PROCESSING을 유지한다.
        }
    }

    private void validateCurrentPayment(PartialCancellationStart start, GatewayPaymentQueryResult result) {
        boolean validStatus = result.status() == GatewayPaymentStatus.PAID
                || result.status() == GatewayPaymentStatus.PARTIALLY_CANCELED;
        if (!validStatus
                || !Objects.equals(start.providerPaymentKey(), result.providerPaymentKey())
                || !Objects.equals(start.merchantPaymentId(), result.merchantPaymentId())
                || !Objects.equals(start.originalAmount(), result.amount())
                || !Objects.equals(start.currency(), result.currency())) {
            fail(start, "PAYMENT_QUERY_MISMATCH", "최신 결제 정보가 부분환불 요청과 일치하지 않습니다.");
            throw new PaymentException("최신 결제 정보를 확인할 수 없어 부분환불을 진행하지 않았습니다.");
        }
    }

    private void fail(PartialCancellationStart start, String code, String message) {
        transactions.fail(start.cancellationId(), start.paymentCancellationId(), code, message);
    }
}
