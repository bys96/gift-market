package com.giftmarket.payment.service;

import com.giftmarket.order.dto.response.CancellationRefundCalculation;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.order.repository.*;
import com.giftmarket.order.service.OrderCancellationCompletionService;
import com.giftmarket.order.service.OrderCancellationRefundCalculator;
import com.giftmarket.payment.entity.*;
import com.giftmarket.payment.exception.PaymentException;
import com.giftmarket.payment.gateway.GatewayCancelResult;
import com.giftmarket.payment.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PartialPaymentCancellationTransactionService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final SellerOrderRepository sellerOrderRepository;
    private final OrderCancellationRepository orderCancellationRepository;
    private final PaymentCancellationRepository paymentCancellationRepository;
    private final OrderCancellationRefundCalculator refundCalculator;
    private final PartialPaymentCancellationPreparationService preparationService;
    private final OrderCancellationCompletionService completionService;

    @Transactional
    public PartialCancellationStart start(Long cancellationId) {
        OrderCancellation reference = orderCancellationRepository.findById(cancellationId)
                .orElseThrow(this::notAvailable);
        Long orderId = reference.getOrder().getId();
        Long sellerOrderId = reference.getSellerOrder().getId();
        Payment payment = paymentRepository.findFirstByOrderIdOrderByIdDesc(orderId)
                .flatMap(value -> paymentRepository.findByIdForUpdate(value.getId()))
                .orElseThrow(this::notAvailable);
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(this::notAvailable);
        SellerOrder sellerOrder = sellerOrderRepository
                .findByIdAndOrderIdForUpdate(sellerOrderId, orderId).orElseThrow(this::notAvailable);
        OrderCancellation cancellation = orderCancellationRepository.findByIdForUpdate(cancellationId)
                .orElseThrow(this::notAvailable);

        if (cancellation.getStatus() == OrderCancellationStatus.COMPLETED) {
            return completed(cancellationId, payment);
        }
        if (cancellation.isRequiresSellerApproval()
                && cancellation.getStatus() == OrderCancellationStatus.REQUESTED) {
            return waiting(cancellationId, payment);
        }
        validateStartState(payment, order, sellerOrder, cancellation);
        if (!cancellation.isRequiresSellerApproval()) {
            if (cancellation.getStatus() == OrderCancellationStatus.REQUESTED) {
                cancellation.startProcessing(LocalDateTime.now());
            } else if (cancellation.getStatus() != OrderCancellationStatus.PROCESSING) {
                throw notAvailable();
            }
        } else if (cancellation.getStatus() != OrderCancellationStatus.PROCESSING) {
            throw notAvailable();
        }

        CancellationRefundCalculation calculation = refundCalculator.calculate(cancellationId);
        PaymentCancellation pgCancellation = preparationService.prepare(
                cancellationId, calculation.totalRefundAmount());
        if (pgCancellation.getType() != PaymentCancellationType.PARTIAL
                || !Objects.equals(pgCancellation.getAmount(), calculation.totalRefundAmount())) {
            throw new PaymentException("부분환불 거래 금액이 계산 결과와 일치하지 않습니다.");
        }
        return new PartialCancellationStart(
                PartialCancellationStart.Action.EXECUTE, cancellationId, payment.getId(),
                pgCancellation.getId(), payment.getProvider(), payment.getProviderPaymentKey(),
                payment.getMerchantPaymentId(), payment.getAmount(), pgCancellation.getAmount(),
                payment.getCurrency(), pgCancellation.getReason(), pgCancellation.getIdempotencyKey());
    }

    @Transactional
    public void complete(PartialCancellationStart start, GatewayCancelResult result) {
        Payment payment = paymentRepository.findByIdForUpdate(start.paymentId()).orElseThrow(this::notAvailable);
        Order order = orderRepository.findByIdForUpdate(payment.getOrder().getId()).orElseThrow(this::notAvailable);
        OrderCancellation cancellation = orderCancellationRepository.findByIdForUpdate(start.cancellationId())
                .orElseThrow(this::notAvailable);
        SellerOrder sellerOrder = sellerOrderRepository.findByIdAndOrderIdForUpdate(
                cancellation.getSellerOrder().getId(), order.getId()).orElseThrow(this::notAvailable);
        PaymentCancellation pgCancellation = paymentCancellationRepository
                .findByIdForUpdate(start.paymentCancellationId()).orElseThrow(this::notAvailable);

        if (pgCancellation.getStatus() == PaymentCancellationStatus.SUCCEEDED
                && cancellation.getStatus() == OrderCancellationStatus.COMPLETED) {
            validateSameSuccessfulResult(pgCancellation, result);
            return;
        }
        validateCompletion(payment, order, cancellation, pgCancellation, start, result);
        completionService.complete(cancellation.getId());
        LocalDateTime canceledAt = result.canceledAt() == null ? LocalDateTime.now() : result.canceledAt();
        pgCancellation.succeed(result.providerTransactionId(), canceledAt);
        if (result.remainingAmount() == 0L) {
            payment.markFullyCanceled(result.providerStatus(), canceledAt);
            List<SellerOrder> sellerOrders = sellerOrderRepository.findAllByOrderIdOrderByIdAsc(order.getId());
            if (sellerOrders.stream().allMatch(value -> value.getStatus() == SellerOrderStatus.CANCELLED)) {
                order.cancel();
            }
        } else {
            payment.markPartiallyCanceled(result.providerStatus());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long cancellationId, Long paymentCancellationId, String code, String message) {
        OrderCancellation reference = orderCancellationRepository.findById(cancellationId)
                .orElseThrow(this::notAvailable);
        Payment payment = paymentRepository.findFirstByOrderIdOrderByIdDesc(reference.getOrder().getId())
                .flatMap(value -> paymentRepository.findByIdForUpdate(value.getId())).orElseThrow(this::notAvailable);
        orderRepository.findByIdForUpdate(reference.getOrder().getId()).orElseThrow(this::notAvailable);
        sellerOrderRepository.findByIdAndOrderIdForUpdate(
                reference.getSellerOrder().getId(), reference.getOrder().getId()).orElseThrow(this::notAvailable);
        OrderCancellation cancellation = orderCancellationRepository.findByIdForUpdate(cancellationId)
                .orElseThrow(this::notAvailable);
        PaymentCancellation pgCancellation = paymentCancellationRepository.findByIdForUpdate(paymentCancellationId)
                .orElseThrow(this::notAvailable);
        if (payment.isRefundableState()
                && cancellation.getStatus() == OrderCancellationStatus.PROCESSING
                && pgCancellation.getStatus() == PaymentCancellationStatus.REQUESTED) {
            pgCancellation.fail(code, safeMessage(message), LocalDateTime.now());
            cancellation.fail(LocalDateTime.now());
        }
    }

    private void validateStartState(Payment payment, Order order, SellerOrder sellerOrder,
                                    OrderCancellation cancellation) {
        SellerOrderStatus expected = cancellation.isRequiresSellerApproval()
                ? SellerOrderStatus.PREPARING : SellerOrderStatus.PAID;
        if (!payment.isRefundableState() || order.getStatus() != OrderStatus.PAID
                || sellerOrder.getStatus() != expected
                || cancellation.getOrder() != order || cancellation.getSellerOrder() != sellerOrder
                || payment.getProviderPaymentKey() == null || payment.getProviderPaymentKey().isBlank()) {
            throw notAvailable();
        }
    }

    private void validateCompletion(Payment payment, Order order, OrderCancellation cancellation,
                                    PaymentCancellation pgCancellation, PartialCancellationStart start,
                                    GatewayCancelResult result) {
        boolean partial = result.remainingAmount() != null && result.remainingAmount() > 0L
                && result.status() == com.giftmarket.payment.gateway.GatewayPaymentStatus.PARTIALLY_CANCELED;
        boolean full = Objects.equals(result.remainingAmount(), 0L)
                && result.status() == com.giftmarket.payment.gateway.GatewayPaymentStatus.CANCELED;
        if (!payment.isRefundableState() || order.getStatus() != OrderStatus.PAID
                || cancellation.getStatus() != OrderCancellationStatus.PROCESSING
                || pgCancellation.getStatus() != PaymentCancellationStatus.REQUESTED
                || pgCancellation.getType() != PaymentCancellationType.PARTIAL
                || pgCancellation.getOrderCancellation() != cancellation
                || !Objects.equals(pgCancellation.getAmount(), start.cancelAmount())
                || !Objects.equals(result.canceledAmount(), pgCancellation.getAmount())
                || !Objects.equals(result.providerPaymentKey(), payment.getProviderPaymentKey())
                || !Objects.equals(result.merchantPaymentId(), payment.getMerchantPaymentId())
                || !Objects.equals(result.amount(), payment.getAmount())
                || !Objects.equals(result.currency(), payment.getCurrency())
                || result.providerTransactionId() == null || result.providerTransactionId().isBlank()
                || !"DONE".equalsIgnoreCase(result.cancellationStatus())
                || result.remainingAmount() == null || result.remainingAmount() < 0L
                || result.remainingAmount() > payment.getAmount() || (!partial && !full)) {
            throw new PaymentException("부분환불 결과가 결제 정보와 일치하지 않습니다.");
        }
    }

    private void validateSameSuccessfulResult(PaymentCancellation cancellation, GatewayCancelResult result) {
        if (!Objects.equals(cancellation.getProviderTransactionKey(), result.providerTransactionId())
                || !Objects.equals(cancellation.getAmount(), result.canceledAmount())) {
            throw new PaymentException("기존 부분환불 완료 결과와 일치하지 않습니다.");
        }
    }

    private PartialCancellationStart waiting(Long id, Payment payment) {
        return new PartialCancellationStart(PartialCancellationStart.Action.WAITING_APPROVAL,
                id, payment.getId(), null, payment.getProvider(), null, null,
                null, null, null, null, null);
    }

    private PartialCancellationStart completed(Long id, Payment payment) {
        return new PartialCancellationStart(PartialCancellationStart.Action.COMPLETED,
                id, payment.getId(), null, payment.getProvider(), null, null,
                null, null, null, null, null);
    }

    private String safeMessage(String message) {
        if (message == null || message.isBlank()) return "부분환불이 거절되었습니다.";
        String value = message.trim();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private PaymentException notAvailable() {
        return new PaymentException("현재 상태에서는 부분환불을 처리할 수 없습니다.");
    }
}
