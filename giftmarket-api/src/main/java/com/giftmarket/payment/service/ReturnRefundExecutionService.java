package com.giftmarket.payment.service;

import com.giftmarket.payment.exception.PaymentException;
import com.giftmarket.order.service.ReturnCompletionService;
import com.giftmarket.payment.gateway.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ReturnRefundExecutionService {
    private final ReturnPaymentCancellationTransactionService transactions;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final ReturnPaymentCancellationReconciliationService reconciliationService;
    private final ReturnCompletionService completionService;

    public void execute(Long returnRequestId) {
        ReturnCancellationStart start = transactions.start(returnRequestId);
        if (start.action() == ReturnCancellationStart.Action.ZERO_REFUND
                || start.action() == ReturnCancellationStart.Action.SUCCEEDED) {
            completionService.complete(returnRequestId);
            return;
        }
        if (start.action() == ReturnCancellationStart.Action.RECONCILE) {
            reconciliationService.reconcile(start.paymentCancellationId());
            return;
        }
        if (start.action() != ReturnCancellationStart.Action.EXECUTE) return;
        PaymentGateway gateway = gatewayRegistry.get(start.provider());
        try {
            GatewayPaymentQueryResult current = gateway.getPayment(start.providerPaymentKey());
            validate(start, current);
            if (!Boolean.TRUE.equals(current.partialCancelable())) {
                transactions.fail(start.returnRequestId(), start.paymentCancellationId(), "NOT_PARTIAL_CANCELABLE_PAYMENT", "부분환불할 수 없는 결제입니다.");
                return;
            }
            GatewayCancelResult result = gateway.cancel(GatewayCancelCommand.partial(
                    start.providerPaymentKey(), start.merchantPaymentId(), start.originalAmount(), start.currency(),
                    start.reason(), start.idempotencyKey(), start.cancelAmount()));
            if (result == null) throw new PaymentGatewayUncertainException("PG 환불 응답이 비어 있습니다.", null);
            transactions.complete(start, result);
            completionService.complete(returnRequestId);
        } catch (PaymentGatewayDeclinedException exception) {
            transactions.fail(start.returnRequestId(), start.paymentCancellationId(), exception.getFailureCode(), exception.getMessage());
        } catch (PaymentGatewayUncertainException exception) {
            // 결과 불명은 REQUESTED/REFUNDING을 유지해 reconciliation에서 확인한다.
        }
    }

    private void validate(ReturnCancellationStart start, GatewayPaymentQueryResult result) {
        if (result == null) throw new PaymentGatewayUncertainException("PG 결제 조회 응답이 비어 있습니다.", null);
        boolean status = result.status() == GatewayPaymentStatus.PAID
                || result.status() == GatewayPaymentStatus.PARTIALLY_CANCELED;
        if (!status || !Objects.equals(start.providerPaymentKey(), result.providerPaymentKey())
                || !Objects.equals(start.merchantPaymentId(), result.merchantPaymentId())
                || !Objects.equals(start.originalAmount(), result.amount()) || !Objects.equals(start.currency(), result.currency())) {
            transactions.fail(start.returnRequestId(), start.paymentCancellationId(), "PAYMENT_QUERY_MISMATCH", "최신 결제 정보가 반품 환불 요청과 일치하지 않습니다.");
            throw new PaymentException("최신 결제 정보를 확인하지 못해 반품 환불을 진행하지 못했습니다.");
        }
    }
}
