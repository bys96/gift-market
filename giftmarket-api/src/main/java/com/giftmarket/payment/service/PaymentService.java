package com.giftmarket.payment.service;

import com.giftmarket.payment.dto.request.PaymentConfirmRequest;
import com.giftmarket.payment.dto.request.PaymentPreparationUpdateRequest;
import com.giftmarket.payment.dto.response.PaymentResponse;
import com.giftmarket.payment.exception.PaymentException;
import com.giftmarket.payment.gateway.GatewayConfirmCommand;
import com.giftmarket.payment.gateway.GatewayConfirmResult;
import com.giftmarket.payment.gateway.GatewayPaymentQueryResult;
import com.giftmarket.payment.gateway.GatewayPaymentStatus;
import com.giftmarket.payment.gateway.PaymentGateway;
import com.giftmarket.payment.gateway.PaymentGatewayDeclinedException;
import com.giftmarket.payment.gateway.PaymentGatewayRegistry;
import com.giftmarket.payment.gateway.PaymentGatewayUncertainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentTransactionService transactionService;
    private final PaymentGatewayRegistry gatewayRegistry;

    public PaymentResponse confirm(
            Long userId,
            Long paymentId,
            PaymentConfirmRequest request
    ) {
        PaymentConfirmStart start = transactionService.startConfirm(
                userId,
                paymentId,
                request
        );
        if (start.action() == PaymentConfirmStart.Action.COMPLETED) {
            return start.response();
        }

        PaymentGateway gateway = gatewayRegistry.get(start.provider());
        try {
            if (start.action() == PaymentConfirmStart.Action.QUERY) {
                return handleQuery(
                        userId,
                        paymentId,
                        gateway.getPayment(start.providerPaymentKey())
                );
            }

            GatewayConfirmResult result = gateway.confirm(
                    new GatewayConfirmCommand(
                            start.providerPaymentKey(),
                            start.merchantPaymentId(),
                            start.amount(),
                            start.currency(),
                            start.confirmIdempotencyKey()
                    )
            );
            if (result.status() == GatewayPaymentStatus.PAID) {
                return transactionService.complete(userId, paymentId, result);
            }
            if (isDefinitiveFailure(result.status())) {
                fail(
                        userId,
                        paymentId,
                        "PAYMENT_DECLINED",
                        "결제 승인이 거절되었습니다.",
                        result.providerStatus()
                );
                throw new PaymentException(
                        "결제 승인이 거절되었습니다. 결제 정보를 다시 확인해주세요."
                );
            }
            return transactionService.getPayment(userId, paymentId);
        } catch (PaymentGatewayDeclinedException exception) {
            fail(
                    userId,
                    paymentId,
                    exception.getFailureCode(),
                    exception.getMessage(),
                    null
            );
            throw new PaymentException(
                    "결제 승인이 거절되었습니다. 결제 정보를 다시 확인해주세요."
            );
        } catch (PaymentGatewayUncertainException exception) {
            return transactionService.getPayment(userId, paymentId);
        }
    }

    public PaymentResponse getPayment(Long userId, Long paymentId) {
        PaymentConfirmStart start = transactionService.startQuery(
                userId,
                paymentId
        );
        if (start.action() == PaymentConfirmStart.Action.COMPLETED) {
            return start.response();
        }

        try {
            PaymentGateway gateway = gatewayRegistry.get(start.provider());
            return handleQuery(
                    userId,
                    paymentId,
                    gateway.getPayment(start.providerPaymentKey())
            );
        } catch (PaymentGatewayUncertainException exception) {
            return transactionService.getPayment(userId, paymentId);
        }
    }

    public PaymentResponse getPaymentByMerchantPaymentId(
            Long userId,
            String merchantPaymentId
    ) {
        return getPayment(
                userId,
                transactionService.getOwnedPaymentId(userId, merchantPaymentId)
        );
    }

    public PaymentResponse updateReadyPreparation(
            Long userId,
            Long paymentId,
            PaymentPreparationUpdateRequest request
    ) {
        return transactionService.updateReadyPreparation(
                userId,
                paymentId,
                request
        );
    }

    private PaymentResponse handleQuery(
            Long userId,
            Long paymentId,
            GatewayPaymentQueryResult result
    ) {
        PaymentResponse response = transactionService.complete(
                userId,
                paymentId,
                result
        );
        if (isDefinitiveFailure(result.status())) {
            throw new PaymentException(
                    "결제가 완료되지 않았습니다. 결제 정보를 다시 확인해주세요."
            );
        }
        return response;
    }

    private boolean isDefinitiveFailure(GatewayPaymentStatus status) {
        return status == GatewayPaymentStatus.FAILED
                || status == GatewayPaymentStatus.CANCELED;
    }

    private void fail(
            Long userId,
            Long paymentId,
            String failureCode,
            String failureMessage,
            String providerStatus
    ) {
        transactionService.fail(
                userId,
                paymentId,
                failureCode,
                failureMessage,
                providerStatus
        );
    }
}
