package com.giftmarket.payment.service;

import com.giftmarket.payment.entity.PaymentProvider;
import com.giftmarket.payment.exception.PaymentException;
import com.giftmarket.payment.exception.PaymentWebhookRetryableException;
import com.giftmarket.payment.gateway.GatewayPaymentQueryResult;
import com.giftmarket.payment.gateway.PaymentGateway;
import com.giftmarket.payment.gateway.PaymentGatewayRegistry;
import com.giftmarket.payment.gateway.PaymentGatewayUncertainException;
import com.giftmarket.payment.infrastructure.toss.dto.TossPaymentWebhookRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TossPaymentWebhookService {

    private static final String PAYMENT_STATUS_CHANGED =
            "PAYMENT_STATUS_CHANGED";

    private final PaymentWebhookEventService eventService;
    private final PaymentTransactionService transactionService;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentCancellationTransactionService cancellationTransactionService;
    private final PartialPaymentCancellationReconciliationService partialCancellationReconciliationService;
    private final ReturnPaymentCancellationReconciliationService returnCancellationReconciliationService;

    public void process(
            String transmissionId,
            TossPaymentWebhookRequest request
    ) {
        PaymentProvider provider = PaymentProvider.TOSS;
        PaymentWebhookEventService.BeginResult beginResult;
        try {
            beginResult = eventService.begin(
                    provider,
                    transmissionId,
                    request.eventType()
            );
        } catch (DataIntegrityViolationException exception) {
            throw new PaymentWebhookRetryableException(
                    "웹훅 중복 등록을 확인 중입니다."
            );
        }

        if (beginResult == PaymentWebhookEventService.BeginResult.DUPLICATE) {
            log.info(
                    "Duplicate Toss webhook accepted. eventType={}, transmissionId={}",
                    request.eventType(),
                    transmissionId
            );
            return;
        }
        if (beginResult == PaymentWebhookEventService.BeginResult.IN_PROGRESS) {
            throw new PaymentWebhookRetryableException(
                    "웹훅 처리가 진행 중입니다."
            );
        }
        if (!PAYMENT_STATUS_CHANGED.equals(request.eventType())) {
            eventService.ignored(
                    provider,
                    transmissionId,
                    "UNSUPPORTED_EVENT_TYPE"
            );
            return;
        }

        PaymentWebhookEventService.TargetResult target =
                eventService.findTarget(
                        provider,
                        request.data().orderId(),
                        request.data().paymentKey()
                );
        if (target.status()
                == PaymentWebhookEventService.TargetStatus.NOT_FOUND) {
            retryableFailure(
                    provider,
                    transmissionId,
                    "PAYMENT_NOT_FOUND"
            );
        }
        if (target.status()
                == PaymentWebhookEventService.TargetStatus.MISMATCH) {
            eventService.rejected(
                    provider,
                    transmissionId,
                    target.paymentId(),
                    "PAYMENT_IDENTIFIER_MISMATCH"
            );
            log.warn(
                    "Toss webhook payment identifiers rejected. transmissionId={}, paymentId={}",
                    transmissionId,
                    target.paymentId()
            );
            return;
        }

        Long paymentId = target.paymentId();
        try {
            PaymentConfirmStart start = transactionService.startWebhookQuery(
                    paymentId,
                    provider,
                    request.data().paymentKey(),
                    request.data().orderId()
            );
            if (start.action() == PaymentConfirmStart.Action.COMPLETED) {
                boolean hasOrderCancellation = partialCancellationReconciliationService
                        .hasRequestedPartialCancellation(paymentId);
                boolean hasReturnCancellation = returnCancellationReconciliationService
                        .hasRequestedReturnCancellation(paymentId);
                if (hasOrderCancellation || hasReturnCancellation) {
                    PaymentGateway gateway = gatewayRegistry.get(provider);
                    GatewayPaymentQueryResult result = gateway.getPayment(request.data().paymentKey());
                    if (hasOrderCancellation) {
                        partialCancellationReconciliationService.reconcileFromWebhook(paymentId, result);
                    }
                    if (hasReturnCancellation) {
                        returnCancellationReconciliationService.reconcileFromWebhook(paymentId, result);
                    }
                }
                eventService.processed(provider, transmissionId, paymentId);
                return;
            }

            PaymentGateway gateway = gatewayRegistry.get(provider);
            GatewayPaymentQueryResult result = gateway.getPayment(
                    request.data().paymentKey()
            );
            partialCancellationReconciliationService.reconcileFromWebhook(paymentId, result);
            returnCancellationReconciliationService.reconcileFromWebhook(paymentId, result);
            if (cancellationTransactionService.isCanceling(paymentId)) {
                if (result.status() == com.giftmarket.payment.gateway.GatewayPaymentStatus.CANCELED
                        && result.remainingAmount() != null
                        && result.remainingAmount() == 0L) {
                    cancellationTransactionService.completeFromWebhook(paymentId, result);
                } else {
                    log.warn(
                            "Toss cancellation webhook remains unresolved. transmissionId={}, paymentId={}, providerStatus={}",
                            transmissionId,
                            paymentId,
                            result.providerStatus()
                    );
                }
            } else {
                transactionService.reconcileWebhook(
                        paymentId,
                        request.data().paymentKey(),
                        result
                );
            }
            eventService.processed(provider, transmissionId, paymentId);
            log.info(
                    "Toss webhook processed. eventType={}, transmissionId={}, paymentId={}, result={}",
                    request.eventType(),
                    transmissionId,
                    paymentId,
                    result.status()
            );
        } catch (PaymentGatewayUncertainException exception) {
            retryableFailure(
                    provider,
                    transmissionId,
                    "PROVIDER_QUERY_UNCERTAIN"
            );
        } catch (PaymentException exception) {
            eventService.rejected(
                    provider,
                    transmissionId,
                    paymentId,
                    "PAYMENT_STATE_OR_IDENTIFIER_MISMATCH"
            );
            log.warn(
                    "Toss webhook state transition rejected. transmissionId={}, paymentId={}, exceptionType={}",
                    transmissionId,
                    paymentId,
                    exception.getClass().getSimpleName()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Toss webhook processing failed. transmissionId={}, paymentId={}, exceptionType={}",
                    transmissionId,
                    paymentId,
                    exception.getClass().getSimpleName()
            );
            retryableFailure(
                    provider,
                    transmissionId,
                    "INTERNAL_PROCESSING_ERROR"
            );
        }
    }

    private void retryableFailure(
            PaymentProvider provider,
            String transmissionId,
            String reason
    ) {
        try {
            eventService.retryableFailure(provider, transmissionId, reason);
        } catch (RuntimeException exception) {
            log.error(
                    "Toss webhook retry state recording failed. transmissionId={}, exceptionType={}",
                    transmissionId,
                    exception.getClass().getSimpleName()
            );
        }
        throw new PaymentWebhookRetryableException(
                "결제 상태를 확인하지 못했습니다."
        );
    }
}
