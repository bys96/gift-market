package com.giftmarket.payment.service;

import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.*;
import com.giftmarket.payment.entity.*;
import com.giftmarket.payment.exception.PartialCancellationValidationException;
import com.giftmarket.payment.exception.PaymentException;
import com.giftmarket.payment.gateway.GatewayCancelResult;
import com.giftmarket.payment.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReturnPaymentCancellationTransactionService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final SellerOrderRepository sellerOrderRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentCancellationRepository cancellationRepository;
    private final PaymentRefundBalanceService refundBalanceService;

    @Transactional
    public ReturnCancellationStart start(Long returnRequestId) {
        ReturnRequest reference = returnRequestRepository.findById(returnRequestId).orElseThrow(this::notAvailable);
        Payment payment = paymentRepository.findFirstByOrderIdOrderByIdDesc(reference.getOrder().getId())
                .flatMap(value -> paymentRepository.findByIdForUpdate(value.getId())).orElseThrow(this::notAvailable);
        Order order = orderRepository.findByIdForUpdate(reference.getOrder().getId()).orElseThrow(this::notAvailable);
        SellerOrder sellerOrder = sellerOrderRepository.findByIdAndOrderIdForUpdate(
                reference.getSellerOrder().getId(), order.getId()).orElseThrow(this::notAvailable);
        ReturnRequest request = returnRequestRepository.findByIdForUpdate(returnRequestId).orElseThrow(this::notAvailable);
        orderItemRepository.findAllBySellerOrderIdForUpdate(sellerOrder.getId());
        PaymentCancellation existing = cancellationRepository.findByReturnRequestId(returnRequestId).orElse(null);

        validateBase(payment, order, sellerOrder, request);
        if (request.getRefundAmount() == 0L) {
            if (request.getStatus() == ReturnRequestStatus.INSPECTED) request.startRefunding(LocalDateTime.now());
            return result(ReturnCancellationStart.Action.ZERO_REFUND, request, payment, null);
        }
        if (existing != null) {
            validateExisting(existing, request);
            ReturnCancellationStart.Action action = existing.getStatus() == PaymentCancellationStatus.SUCCEEDED
                    ? ReturnCancellationStart.Action.SUCCEEDED : ReturnCancellationStart.Action.RECONCILE;
            return result(action, request, payment, existing);
        }
        if (request.getStatus() != ReturnRequestStatus.INSPECTED) throw notAvailable();
        PaymentRefundBalance balance = refundBalanceService.getBalance(payment);
        long unreserved = returnRequestRepository.sumUnreservedRefundAmountByOrderIdExcludingRequest(
                order.getId(), request.getId(), Set.of(ReturnRequestStatus.INSPECTED, ReturnRequestStatus.REFUNDING),
                Set.of(PaymentCancellationStatus.REQUESTED, PaymentCancellationStatus.SUCCEEDED));
        long available;
        try {
            available = Math.subtractExact(balance.availableRefundAmount(), unreserved);
        } catch (ArithmeticException exception) {
            throw new PaymentException("환불 가능 금액을 안전하게 계산할 수 없습니다.");
        }
        if (unreserved < 0 || available < request.getRefundAmount()) throw new PaymentException("환불 가능 금액을 초과했습니다.");
        refundBalanceService.validateRefundAmount(balance, request.getRefundAmount());
        String key = "RETURN-REFUND-" + returnRequestId;
        PaymentCancellation created = PaymentCancellation.createReturnPartial(
                payment, request, key, key, request.getRefundAmount(), reason(returnRequestId), LocalDateTime.now());
        cancellationRepository.saveAndFlush(created);
        request.startRefunding(LocalDateTime.now());
        return result(ReturnCancellationStart.Action.EXECUTE, request, payment, created);
    }

    @Transactional
    public void complete(ReturnCancellationStart start, GatewayCancelResult result) {
        Payment payment = paymentRepository.findByIdForUpdate(start.paymentId()).orElseThrow(this::notAvailable);
        Order order = orderRepository.findByIdForUpdate(payment.getOrder().getId()).orElseThrow(this::notAvailable);
        ReturnRequest reference = returnRequestRepository.findById(start.returnRequestId()).orElseThrow(this::notAvailable);
        SellerOrder sellerOrder = sellerOrderRepository.findByIdAndOrderIdForUpdate(
                reference.getSellerOrder().getId(), order.getId()).orElseThrow(this::notAvailable);
        ReturnRequest request = returnRequestRepository.findByIdForUpdate(start.returnRequestId()).orElseThrow(this::notAvailable);
        PaymentCancellation cancellation = cancellationRepository.findByIdForUpdate(start.paymentCancellationId())
                .orElseThrow(this::notAvailable);
        if (cancellation.getStatus() == PaymentCancellationStatus.SUCCEEDED) {
            if (!Objects.equals(cancellation.getProviderTransactionKey(), result.providerTransactionId())) throw notAvailable();
            return;
        }
        validateCompletion(payment, order, sellerOrder, request, cancellation, start, result);
        LocalDateTime canceledAt = result.canceledAt() == null ? LocalDateTime.now() : result.canceledAt();
        cancellation.succeed(result.providerTransactionId(), canceledAt);
        if (result.remainingAmount() == 0L) payment.markFullyCanceled(result.providerStatus(), canceledAt);
        else payment.markPartiallyCanceled(result.providerStatus());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long returnRequestId, Long cancellationId, String code, String message) {
        ReturnRequest reference = returnRequestRepository.findById(returnRequestId).orElseThrow(this::notAvailable);
        Payment payment = paymentRepository.findFirstByOrderIdOrderByIdDesc(reference.getOrder().getId())
                .flatMap(value -> paymentRepository.findByIdForUpdate(value.getId())).orElseThrow(this::notAvailable);
        orderRepository.findByIdForUpdate(reference.getOrder().getId()).orElseThrow(this::notAvailable);
        sellerOrderRepository.findByIdAndOrderIdForUpdate(reference.getSellerOrder().getId(), reference.getOrder().getId())
                .orElseThrow(this::notAvailable);
        ReturnRequest request = returnRequestRepository.findByIdForUpdate(returnRequestId).orElseThrow(this::notAvailable);
        PaymentCancellation cancellation = cancellationRepository.findByIdForUpdate(cancellationId).orElseThrow(this::notAvailable);
        if (payment.isRefundableState() && request.getStatus() == ReturnRequestStatus.REFUNDING
                && cancellation.getStatus() == PaymentCancellationStatus.REQUESTED) {
            cancellation.fail(code, safe(message), LocalDateTime.now());
            request.fail(LocalDateTime.now());
        }
    }

    private void validateBase(Payment payment, Order order, SellerOrder sellerOrder, ReturnRequest request) {
        boolean state = request.getStatus() == ReturnRequestStatus.INSPECTED
                || request.getStatus() == ReturnRequestStatus.REFUNDING;
        if (!state || request.getResponsibility() == null || request.getRefundAmount() == null
                || request.getRefundAmount() < 0 || request.getOrder() != order || request.getSellerOrder() != sellerOrder
                || payment.getOrder() != order || !payment.isRefundableState() || order.getStatus() != OrderStatus.PAID
                || sellerOrder.getStatus() != SellerOrderStatus.DELIVERED || !hasText(payment.getProviderPaymentKey())) {
            throw notAvailable();
        }
    }

    private void validateExisting(PaymentCancellation value, ReturnRequest request) {
        if (value.getReturnRequest() != request || value.getOrderCancellation() != null
                || value.getType() != PaymentCancellationType.PARTIAL
                || !Objects.equals(value.getAmount(), request.getRefundAmount())
                || value.getStatus() == PaymentCancellationStatus.FAILED) throw notAvailable();
    }

    private void validateCompletion(Payment payment, Order order, SellerOrder sellerOrder, ReturnRequest request,
                                    PaymentCancellation cancellation, ReturnCancellationStart start, GatewayCancelResult result) {
        if (!payment.isRefundableState() || order.getStatus() != OrderStatus.PAID
                || sellerOrder.getStatus() != SellerOrderStatus.DELIVERED || request.getStatus() != ReturnRequestStatus.REFUNDING
                || cancellation.getStatus() != PaymentCancellationStatus.REQUESTED || cancellation.getReturnRequest() != request
                || cancellation.getOrderCancellation() != null || !Objects.equals(cancellation.getAmount(), start.cancelAmount())
                || !Objects.equals(result.canceledAmount(), cancellation.getAmount())) throw validation("INTERNAL_STATE");
        if (!Objects.equals(result.providerPaymentKey(), payment.getProviderPaymentKey())
                || !Objects.equals(result.merchantPaymentId(), payment.getMerchantPaymentId())
                || !Objects.equals(result.amount(), payment.getAmount()) || !Objects.equals(result.currency(), payment.getCurrency()))
            throw validation("PAYMENT_IDENTITY");
        Long succeededValue = cancellationRepository.sumAmountByPaymentIdAndStatus(payment.getId(), PaymentCancellationStatus.SUCCEEDED);
        long succeeded = succeededValue == null ? 0L : succeededValue;
        long expectedRemaining;
        try {
            expectedRemaining = Math.subtractExact(Math.subtractExact(payment.getAmount(), succeeded), cancellation.getAmount());
        } catch (ArithmeticException exception) {
            throw validation("REFUND_BALANCE");
        }
        if (!hasText(result.providerTransactionId()) || !"DONE".equalsIgnoreCase(result.cancellationStatus())
                || result.remainingAmount() == null || result.transactionRemainingAmount() == null
                || result.remainingAmount() < 0 || result.remainingAmount() != expectedRemaining
                || !Objects.equals(result.remainingAmount(), result.transactionRemainingAmount()))
            throw validation("CANCELLATION_TRANSACTION");
        boolean partial = result.remainingAmount() > 0 && result.status() == com.giftmarket.payment.gateway.GatewayPaymentStatus.PARTIALLY_CANCELED;
        boolean full = result.remainingAmount() == 0 && (result.status() == com.giftmarket.payment.gateway.GatewayPaymentStatus.CANCELED
                || result.status() == com.giftmarket.payment.gateway.GatewayPaymentStatus.PARTIALLY_CANCELED);
        if (!partial && !full) throw validation("PAYMENT_STATUS_BALANCE");
    }

    private ReturnCancellationStart result(ReturnCancellationStart.Action action, ReturnRequest request,
                                           Payment payment, PaymentCancellation cancellation) {
        return new ReturnCancellationStart(action, request.getId(), payment.getId(), cancellation == null ? null : cancellation.getId(),
                payment.getProvider(), payment.getProviderPaymentKey(), payment.getMerchantPaymentId(), payment.getAmount(),
                request.getRefundAmount(), payment.getCurrency(), cancellation == null ? null : cancellation.getReason(),
                cancellation == null ? null : cancellation.getIdempotencyKey());
    }
    private String reason(Long id) { return "반품 부분환불 요청 #" + id; }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String safe(String value) { if (!hasText(value)) return "반품 환불이 거절되었습니다."; String v=value.trim(); return v.length()<=500?v:v.substring(0,500); }
    private PaymentException notAvailable() { return new PaymentException("현재 상태에서는 반품 환불을 처리할 수 없습니다."); }
    private PartialCancellationValidationException validation(String type) { return new PartialCancellationValidationException(type); }
}
