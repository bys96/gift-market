package com.giftmarket.payment.service;

import com.giftmarket.order.dto.request.OrderCancelRequest;
import com.giftmarket.order.dto.response.OrderCancelResponse;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.order.repository.*;
import com.giftmarket.order.service.OrderInventoryService;
import com.giftmarket.payment.entity.*;
import com.giftmarket.payment.exception.PaymentException;
import com.giftmarket.payment.gateway.*;
import com.giftmarket.payment.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentCancellationTransactionService {
    private final PaymentRepository paymentRepository;
    private final PaymentCancellationRepository cancellationRepository;
    private final OrderRepository orderRepository;
    private final OrderInventoryService inventoryService;

    @Transactional(readOnly = true)
    public boolean isCanceling(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .map(payment -> payment.getStatus() == PaymentStatus.CANCELING)
                .orElse(false);
    }

    @Transactional
    public PaymentCancelStart start(Long userId, Long orderId, OrderCancelRequest request) {
        Order ownedOrder = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new OrderException("주문 정보를 찾을 수 없습니다."));
        Payment payment = paymentRepository
                .findFirstByOrderIdAndOrderUserIdOrderByIdDesc(orderId, userId).orElse(null);
        if (payment != null) payment = getPayment(payment.getId());
        Order order = orderRepository.findByIdAndUserIdForUpdate(ownedOrder.getId(), userId)
                .orElseThrow(() -> new OrderException("주문 정보를 찾을 수 없습니다."));

        if (order.getStatus() == OrderStatus.CANCELLED) return completed(order, payment, "이미 취소된 주문입니다.");
        if (order.getStatus() == OrderStatus.ORDERED) {
            inventoryService.restore(orderId);
            order.cancel();
            return completed(order, payment, "주문이 취소되었습니다.");
        }
        if (payment == null) throw new PaymentException("결제 정보를 찾을 수 없습니다.");
        if (payment.getStatus() == PaymentStatus.CANCELED && order.getStatus() == OrderStatus.CANCELLED) {
            return completed(order, payment, "이미 취소된 주문입니다.");
        }
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT && payment.getStatus() == PaymentStatus.READY) {
            inventoryService.restore(orderId);
            payment.cancelBeforeApproval(LocalDateTime.now());
            order.cancel();
            return completed(order, payment, "결제 전 주문이 취소되었습니다.");
        }
        if (payment.getStatus() == PaymentStatus.CONFIRMING) {
            throw new PaymentException("결제 결과를 확인 중인 주문은 취소할 수 없습니다.");
        }
        if (payment.getStatus() == PaymentStatus.CANCELING && order.getStatus() == OrderStatus.PAID) {
            PaymentCancellation active = cancellationRepository
                    .findFirstByPaymentIdAndStatusOrderByIdDesc(payment.getId(), PaymentCancellationStatus.REQUESTED)
                    .orElseThrow(() -> new PaymentException("결제 취소 상태를 확인할 수 없습니다."));
            return start(PaymentCancelStart.Action.QUERY, payment, active);
        }
        if (payment.getStatus() != PaymentStatus.PAID || order.getStatus() != OrderStatus.PAID) {
            throw new PaymentException("현재 상태에서는 주문을 취소할 수 없습니다.");
        }

        PaymentCancellation existing = cancellationRepository
                .findByClientRequestKeyAndPaymentOrderUserId(request.clientCancelRequestKey(), userId).orElse(null);
        if (existing != null) {
            if (!Objects.equals(existing.getPayment().getId(), payment.getId()))
                throw new PaymentException("취소 요청 정보를 다시 확인해주세요.");
            if (existing.getStatus() == PaymentCancellationStatus.SUCCEEDED)
                return completed(order, payment, "이미 취소된 주문입니다.");
            if (existing.getStatus() == PaymentCancellationStatus.REQUESTED)
                return start(PaymentCancelStart.Action.QUERY, payment, existing);
            throw new PaymentException("이전 취소 요청은 완료되지 않았습니다. 다시 시도해주세요.");
        }

        PaymentCancellation cancellation = cancellationRepository.save(PaymentCancellation.create(
                payment, request.clientCancelRequestKey(), UUID.randomUUID().toString(),
                request.cancelReason().trim(), LocalDateTime.now()));
        payment.startCancel();
        return start(PaymentCancelStart.Action.CANCEL, payment, cancellation);
    }

    @Transactional
    public OrderCancelResponse complete(Long userId, Long paymentId, Long cancellationId, GatewayCancelResult result) {
        Payment payment = getPayment(paymentId);
        if (!Objects.equals(payment.getOrder().getUser().getId(), userId)) throw new PaymentException("결제 정보를 찾을 수 없습니다.");
        return completeLocked(payment, getOrder(payment.getOrder().getId()), getCancellation(cancellationId),
                result.status(), result.providerPaymentKey(), result.providerTransactionId(), result.merchantPaymentId(),
                result.amount(), result.remainingAmount(), result.currency(), result.providerStatus(), result.canceledAt());
    }

    @Transactional
    public OrderCancelResponse completeFromQuery(Long userId, Long paymentId, Long cancellationId, GatewayPaymentQueryResult result) {
        Payment payment = getPayment(paymentId);
        if (!Objects.equals(payment.getOrder().getUser().getId(), userId)) throw new PaymentException("결제 정보를 찾을 수 없습니다.");
        return completeLocked(payment, getOrder(payment.getOrder().getId()), getCancellation(cancellationId),
                result.status(), result.providerPaymentKey(), result.providerTransactionId(), result.merchantPaymentId(),
                result.amount(), result.remainingAmount(), result.currency(), result.providerStatus(), result.canceledAt());
    }

    @Transactional
    public OrderCancelResponse completeFromWebhook(Long paymentId, GatewayPaymentQueryResult result) {
        Payment payment = getPayment(paymentId);
        Order order = getOrder(payment.getOrder().getId());
        PaymentCancellation cancellation = cancellationRepository
                .findFirstByPaymentIdAndStatusOrderByIdDesc(paymentId, PaymentCancellationStatus.REQUESTED)
                .orElseThrow(() -> new PaymentException("취소 요청을 찾을 수 없습니다."));
        cancellation = getCancellation(cancellation.getId());
        return completeLocked(payment, order, cancellation, result.status(), result.providerPaymentKey(),
                result.providerTransactionId(), result.merchantPaymentId(), result.amount(),
                result.remainingAmount(), result.currency(), result.providerStatus(), result.canceledAt());
    }

    @Transactional
    public void explicitFailure(Long userId, Long paymentId, Long cancellationId, String code, String message) {
        Payment payment = getPayment(paymentId);
        if (!Objects.equals(payment.getOrder().getUser().getId(), userId)) throw new PaymentException("결제 정보를 찾을 수 없습니다.");
        Order order = getOrder(payment.getOrder().getId());
        PaymentCancellation cancellation = getCancellation(cancellationId);
        if (payment.getStatus() == PaymentStatus.CANCELING && order.getStatus() == OrderStatus.PAID) {
            cancellation.fail(code, message, LocalDateTime.now());
            payment.cancelFailed();
        }
    }

    private OrderCancelResponse completeLocked(Payment payment, Order order, PaymentCancellation cancellation,
            GatewayPaymentStatus status, String paymentKey, String transactionId, String merchantId,
            Long amount, Long remaining, String currency, String providerStatus, LocalDateTime canceledAt) {
        if (payment.getStatus() == PaymentStatus.CANCELED && order.getStatus() == OrderStatus.CANCELLED)
            return response(order, payment, "이미 취소된 주문입니다.");
        if (payment.getStatus() != PaymentStatus.CANCELING || order.getStatus() != OrderStatus.PAID)
            throw new PaymentException("결제 취소 결과를 반영할 수 없는 상태입니다.");
        if (status != GatewayPaymentStatus.CANCELED || !Objects.equals(remaining, 0L))
            throw new PaymentException(status == GatewayPaymentStatus.PAID
                    ? "결제 취소 결과를 확인 중입니다." : "전체 결제 취소를 확인하지 못했습니다.");
        if (!Objects.equals(payment.getProviderPaymentKey(), paymentKey)
                || !Objects.equals(payment.getMerchantPaymentId(), merchantId)
                || !Objects.equals(payment.getAmount(), amount)
                || !Objects.equals(payment.getCurrency(), currency))
            throw new PaymentException("결제 취소 결과가 주문 정보와 일치하지 않습니다.");
        LocalDateTime now = canceledAt == null ? LocalDateTime.now() : canceledAt;
        inventoryService.restore(order.getId());
        payment.cancelFromProvider(providerStatus, now);
        cancellation.succeed(transactionId, now);
        order.cancel();
        return response(order, payment, "결제가 취소되었습니다.");
    }

    private PaymentCancelStart start(PaymentCancelStart.Action action, Payment p, PaymentCancellation c) {
        return new PaymentCancelStart(action, p.getId(), c.getId(), p.getProvider(), p.getProviderPaymentKey(),
                p.getMerchantPaymentId(), p.getAmount(), p.getCurrency(), c.getReason(), c.getIdempotencyKey(), null);
    }
    private PaymentCancelStart completed(Order o, Payment p, String m) {
        return new PaymentCancelStart(PaymentCancelStart.Action.COMPLETED, p == null ? null : p.getId(), null,
                p == null ? null : p.getProvider(), null, null, null, null, null, null, response(o,p,m));
    }
    private OrderCancelResponse response(Order o, Payment p, String m) {
        return new OrderCancelResponse(o.getId(), o.getStatus(), p == null ? null : p.getStatus(), m);
    }
    private Payment getPayment(Long id) { return paymentRepository.findByIdForUpdate(id).orElseThrow(() -> new PaymentException("결제 정보를 찾을 수 없습니다.")); }
    private Order getOrder(Long id) { return orderRepository.findByIdForUpdate(id).orElseThrow(() -> new PaymentException("주문 정보를 찾을 수 없습니다.")); }
    private PaymentCancellation getCancellation(Long id) { return cancellationRepository.findByIdForUpdate(id).orElseThrow(() -> new PaymentException("취소 요청을 찾을 수 없습니다.")); }
}
