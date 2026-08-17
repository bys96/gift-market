package com.giftmarket.payment.service;

import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.OrderCancellationRepository;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.payment.entity.*;
import com.giftmarket.payment.repository.PaymentCancellationRepository;
import com.giftmarket.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PartialCancellationReconciliationTransactionService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final SellerOrderRepository sellerOrderRepository;
    private final OrderCancellationRepository orderCancellationRepository;
    private final PaymentCancellationRepository paymentCancellationRepository;

    @Transactional
    public PartialCancellationReconciliationStart start(Long paymentCancellationId, LocalDateTime requestedBefore) {
        PaymentCancellation reference = paymentCancellationRepository.findById(paymentCancellationId).orElse(null);
        if (reference == null) return noOp();

        Payment payment = paymentRepository.findByIdForUpdate(reference.getPayment().getId()).orElse(null);
        if (payment == null) return noOp();
        Order order = orderRepository.findByIdForUpdate(payment.getOrder().getId()).orElse(null);
        if (order == null || reference.getOrderCancellation() == null) return noOp();
        OrderCancellation cancellation = reference.getOrderCancellation();
        SellerOrder sellerOrder = sellerOrderRepository.findByIdAndOrderIdForUpdate(
                cancellation.getSellerOrder().getId(), order.getId()).orElse(null);
        if (sellerOrder == null) return noOp();
        cancellation = orderCancellationRepository.findByIdForUpdate(cancellation.getId()).orElse(null);
        PaymentCancellation pgCancellation = paymentCancellationRepository
                .findByIdForUpdate(paymentCancellationId).orElse(null);
        if (!isEligible(payment, order, sellerOrder, cancellation, pgCancellation, requestedBefore)) return noOp();

        Long succeeded = paymentCancellationRepository.sumAmountByPaymentIdAndStatus(
                payment.getId(), PaymentCancellationStatus.SUCCEEDED);
        long expectedRemaining = Math.subtractExact(
                Math.subtractExact(payment.getAmount(), succeeded), pgCancellation.getAmount());
        if (expectedRemaining < 0) return noOp();
        return new PartialCancellationReconciliationStart(
                PartialCancellationReconciliationStart.Action.QUERY,
                payment.getId(), pgCancellation.getId(), cancellation.getId(), payment.getProvider(),
                payment.getProviderPaymentKey(), payment.getMerchantPaymentId(), payment.getAmount(),
                pgCancellation.getAmount(), expectedRemaining, payment.getCurrency(), pgCancellation.getReason(),
                pgCancellation.getIdempotencyKey(), pgCancellation.getProviderTransactionKey(),
                pgCancellation.getRequestedAt());
    }

    private boolean isEligible(Payment payment, Order order, SellerOrder sellerOrder,
                               OrderCancellation cancellation, PaymentCancellation pgCancellation,
                               LocalDateTime requestedBefore) {
        return pgCancellation != null
                && pgCancellation.getType() == PaymentCancellationType.PARTIAL
                && pgCancellation.getStatus() == PaymentCancellationStatus.REQUESTED
                && cancellation != null && cancellation.getStatus() == OrderCancellationStatus.PROCESSING
                && pgCancellation.getOrderCancellation() == cancellation
                && cancellation.getOrder() == order && cancellation.getSellerOrder() == sellerOrder
                && order.getStatus() == OrderStatus.PAID && payment.getOrder() == order
                && (payment.getStatus() == PaymentStatus.PAID
                    || payment.getStatus() == PaymentStatus.PARTIALLY_CANCELED)
                && (sellerOrder.getStatus() == SellerOrderStatus.PAID
                    || sellerOrder.getStatus() == SellerOrderStatus.PREPARING)
                && pgCancellation.getAmount() != null && pgCancellation.getAmount() > 0
                && hasText(payment.getProviderPaymentKey()) && hasText(pgCancellation.getIdempotencyKey())
                && pgCancellation.getRequestedAt() != null
                && !pgCancellation.getRequestedAt().isAfter(requestedBefore);
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }

    private PartialCancellationReconciliationStart noOp() {
        return new PartialCancellationReconciliationStart(
                PartialCancellationReconciliationStart.Action.NO_OP, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }
}
