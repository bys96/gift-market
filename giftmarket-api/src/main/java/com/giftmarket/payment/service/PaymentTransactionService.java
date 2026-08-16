package com.giftmarket.payment.service;

import com.giftmarket.cart.entity.CartItem;
import com.giftmarket.cart.repository.CartItemRepository;
import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.order.service.OrderInventoryService;
import com.giftmarket.payment.dto.request.PaymentConfirmRequest;
import com.giftmarket.payment.dto.response.PaymentResponse;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentStatus;
import com.giftmarket.payment.entity.PaymentProvider;
import com.giftmarket.payment.exception.PaymentException;
import com.giftmarket.payment.gateway.GatewayConfirmResult;
import com.giftmarket.payment.gateway.GatewayPaymentQueryResult;
import com.giftmarket.payment.gateway.GatewayPaymentStatus;
import com.giftmarket.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderInventoryService orderInventoryService;

    @Transactional
    public PaymentConfirmStart startConfirm(
            Long userId,
            Long paymentId,
            PaymentConfirmRequest request
    ) {
        Payment payment = getPaymentForUpdate(userId, paymentId);
        Order order = getOrderForUpdate(userId, payment.getOrder().getId());

        validateRequest(payment, request);

        if (payment.getStatus() == PaymentStatus.PAID) {
            if (order.getStatus() != OrderStatus.PAID) {
                throw new PaymentException("결제 상태를 확인 중입니다.");
            }
            return completed(payment);
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new PaymentException("결제를 진행할 수 없는 주문입니다.");
        }

        if (payment.getStatus() == PaymentStatus.READY) {
            if (!payment.getExpiresAt().isAfter(LocalDateTime.now())) {
                throw new PaymentException("결제 가능 시간이 만료되었습니다.");
            }
            if (!request.providerPaymentKey().equals(
                    request.providerPaymentKey().trim()
            )) {
                throw new PaymentException("결제 정보를 다시 확인해주세요.");
            }

            payment.startConfirm(
                    request.providerPaymentKey(),
                    LocalDateTime.now()
            );
            return start(payment, PaymentConfirmStart.Action.CONFIRM);
        }

        if (payment.getStatus() == PaymentStatus.CONFIRMING) {
            if (!Objects.equals(
                    payment.getProviderPaymentKey(),
                    request.providerPaymentKey()
            )) {
                throw new PaymentException("결제 정보를 다시 확인해주세요.");
            }
            return start(payment, PaymentConfirmStart.Action.QUERY);
        }

        throw new PaymentException("결제를 진행할 수 없는 상태입니다.");
    }

    @Transactional
    public PaymentResponse complete(
            Long userId,
            Long paymentId,
            GatewayConfirmResult result
    ) {
        return complete(
                userId,
                paymentId,
                result.status(),
                result.providerPaymentKey(),
                result.providerTransactionId(),
                result.merchantPaymentId(),
                result.amount(),
                result.currency(),
                result.method(),
                result.easyPayProvider(),
                result.providerStatus(),
                result.approvedAt()
        );
    }

    @Transactional
    public PaymentResponse complete(
            Long userId,
            Long paymentId,
            GatewayPaymentQueryResult result
    ) {
        Payment payment = getPaymentForUpdate(userId, paymentId);
        Order order = getOrderForUpdate(userId, payment.getOrder().getId());
        return applyQueryResult(payment, order, result);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentResponse reconcile(
            Long paymentId,
            GatewayPaymentQueryResult result
    ) {
        Payment payment = getPaymentForUpdate(paymentId);
        Order order = getOrderForUpdate(payment.getOrder().getId());
        return applyQueryResult(payment, order, result);
    }

    @Transactional
    public void fail(
            Long userId,
            Long paymentId,
            String failureCode,
            String failureMessage,
            String providerStatus
    ) {
        Payment payment = getPaymentForUpdate(userId, paymentId);
        Order order = getOrderForUpdate(userId, payment.getOrder().getId());
        finishFailure(
                payment,
                order,
                GatewayPaymentStatus.FAILED,
                failureCode,
                failureMessage,
                providerStatus
        );
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long userId, Long paymentId) {
        return PaymentResponse.from(
                paymentRepository.findByIdAndOrderUserId(paymentId, userId)
                        .orElseThrow(() -> new PaymentException(
                                "결제 정보를 찾을 수 없습니다."
                        ))
        );
    }

    @Transactional(readOnly = true)
    public PaymentConfirmStart startQuery(Long userId, Long paymentId) {
        Payment payment = paymentRepository
                .findByIdAndOrderUserId(paymentId, userId)
                .orElseThrow(() -> new PaymentException(
                        "결제 정보를 찾을 수 없습니다."
                ));

        if (payment.getStatus() != PaymentStatus.CONFIRMING) {
            return completed(payment);
        }
        if (payment.getProviderPaymentKey() == null
                || payment.getProviderPaymentKey().isBlank()) {
            throw new PaymentException("결제 정보를 확인할 수 없습니다.");
        }

        return start(payment, PaymentConfirmStart.Action.QUERY);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentConfirmStart startReconciliation(
            Long paymentId,
            LocalDateTime confirmingBefore
    ) {
        Payment payment = getPaymentForUpdate(paymentId);

        if (payment.getStatus() != PaymentStatus.CONFIRMING
                || payment.getConfirmingAt() == null
                || payment.getConfirmingAt().isAfter(confirmingBefore)) {
            return completed(payment);
        }

        Order order = getOrderForUpdate(payment.getOrder().getId());
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return completed(payment);
        }
        if (payment.getProviderPaymentKey() == null
                || payment.getProviderPaymentKey().isBlank()) {
            throw new PaymentException("결제 정보를 확인할 수 없습니다.");
        }

        return start(payment, PaymentConfirmStart.Action.QUERY);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentConfirmStart startWebhookQuery(
            Long paymentId,
            PaymentProvider provider,
            String providerPaymentKey,
            String merchantPaymentId
    ) {
        Payment payment = getPaymentForUpdate(paymentId);
        Order order = getOrderForUpdate(payment.getOrder().getId());

        if (payment.getProvider() != provider
                || !Objects.equals(payment.getMerchantPaymentId(), merchantPaymentId)
                || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return completed(payment);
        }
        if (payment.getStatus() != PaymentStatus.READY
                && payment.getStatus() != PaymentStatus.CONFIRMING) {
            return completed(payment);
        }
        if (payment.getStatus() == PaymentStatus.CONFIRMING
                && !Objects.equals(
                payment.getProviderPaymentKey(),
                providerPaymentKey
        )) {
            throw new PaymentException("결제 식별정보가 일치하지 않습니다.");
        }

        return new PaymentConfirmStart(
                PaymentConfirmStart.Action.QUERY,
                payment.getProvider(),
                providerPaymentKey,
                payment.getMerchantPaymentId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getConfirmIdempotencyKey(),
                payment.getConfirmingAt(),
                null
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentResponse reconcileWebhook(
            Long paymentId,
            String providerPaymentKey,
            GatewayPaymentQueryResult result
    ) {
        Payment payment = getPaymentForUpdate(paymentId);
        Order order = getOrderForUpdate(payment.getOrder().getId());

        if (payment.getStatus() == PaymentStatus.READY
                && order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            validateGatewayBusinessResult(
                    payment,
                    result.merchantPaymentId(),
                    result.amount(),
                    result.currency()
            );
            if (!Objects.equals(providerPaymentKey, result.providerPaymentKey())) {
                throw new PaymentException("결제 식별정보가 일치하지 않습니다.");
            }
            if (result.status() == GatewayPaymentStatus.PENDING
                    || result.status() == GatewayPaymentStatus.UNKNOWN) {
                return PaymentResponse.from(payment);
            }
            payment.startConfirm(providerPaymentKey, LocalDateTime.now());
        }

        return applyQueryResult(payment, order, result);
    }

    private PaymentResponse complete(
            Long userId,
            Long paymentId,
            GatewayPaymentStatus gatewayStatus,
            String providerPaymentKey,
            String providerTransactionId,
            String merchantPaymentId,
            Long amount,
            String currency,
            com.giftmarket.payment.entity.PaymentMethod method,
            com.giftmarket.payment.entity.EasyPayProvider easyPayProvider,
            String providerStatus,
            LocalDateTime approvedAt
    ) {
        Payment payment = getPaymentForUpdate(userId, paymentId);
        Order order = getOrderForUpdate(userId, payment.getOrder().getId());

        return completeLocked(
                payment,
                order,
                gatewayStatus,
                providerPaymentKey,
                providerTransactionId,
                merchantPaymentId,
                amount,
                currency,
                method,
                easyPayProvider,
                providerStatus,
                approvedAt
        );
    }

    private PaymentResponse applyQueryResult(
            Payment payment,
            Order order,
            GatewayPaymentQueryResult result
    ) {
        if (payment.getStatus() == PaymentStatus.PAID
                && order.getStatus() == OrderStatus.PAID) {
            return PaymentResponse.from(payment);
        }
        if ((payment.getStatus() == PaymentStatus.FAILED
                || payment.getStatus() == PaymentStatus.CANCELED)
                && order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            return PaymentResponse.from(payment);
        }
        if (payment.getStatus() != PaymentStatus.CONFIRMING
                || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new PaymentException("결제 결과를 반영할 수 없는 상태입니다.");
        }

        validateGatewayResult(
                payment,
                result.providerPaymentKey(),
                result.merchantPaymentId(),
                result.amount(),
                result.currency()
        );

        if (result.status() == GatewayPaymentStatus.PAID) {
            return completeLocked(
                    payment,
                    order,
                    result.status(),
                    result.providerPaymentKey(),
                    result.providerTransactionId(),
                    result.merchantPaymentId(),
                    result.amount(),
                    result.currency(),
                    result.method(),
                    result.easyPayProvider(),
                    result.providerStatus(),
                    result.approvedAt()
            );
        }
        if (result.status() == GatewayPaymentStatus.FAILED
                || result.status() == GatewayPaymentStatus.CANCELED) {
            finishFailure(
                    payment,
                    order,
                    result.status(),
                    "PAYMENT_NOT_COMPLETED",
                    "결제가 완료되지 않았습니다.",
                    result.providerStatus()
            );
        }
        return PaymentResponse.from(payment);
    }

    private PaymentResponse completeLocked(
            Payment payment,
            Order order,
            GatewayPaymentStatus gatewayStatus,
            String providerPaymentKey,
            String providerTransactionId,
            String merchantPaymentId,
            Long amount,
            String currency,
            com.giftmarket.payment.entity.PaymentMethod method,
            com.giftmarket.payment.entity.EasyPayProvider easyPayProvider,
            String providerStatus,
            LocalDateTime approvedAt
    ) {

        if (payment.getStatus() == PaymentStatus.PAID
                && order.getStatus() == OrderStatus.PAID) {
            return PaymentResponse.from(payment);
        }
        if (payment.getStatus() != PaymentStatus.CONFIRMING
                || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new PaymentException("결제를 확정할 수 없는 상태입니다.");
        }
        if (gatewayStatus != GatewayPaymentStatus.PAID) {
            throw new PaymentException("결제 결과를 확인 중입니다.");
        }
        validateGatewayResult(
                payment,
                providerPaymentKey,
                merchantPaymentId,
                amount,
                currency
        );

        LocalDateTime paidAt = approvedAt == null
                ? LocalDateTime.now()
                : approvedAt;
        payment.complete(
                providerPaymentKey,
                providerTransactionId,
                method,
                easyPayProvider,
                providerStatus,
                paidAt
        );
        order.markPaid(paidAt);
        removeUnchangedCartItems(order.getUser().getId(), order.getId());
        return PaymentResponse.from(payment);
    }

    private void finishFailure(
            Payment payment,
            Order order,
            GatewayPaymentStatus gatewayStatus,
            String failureCode,
            String failureMessage,
            String providerStatus
    ) {
        if ((payment.getStatus() == PaymentStatus.FAILED
                || payment.getStatus() == PaymentStatus.CANCELED)
                && order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.CONFIRMING
                || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new PaymentException("결제 상태를 변경할 수 없습니다.");
        }

        orderInventoryService.restore(order.getId());
        LocalDateTime now = LocalDateTime.now();
        if (gatewayStatus == GatewayPaymentStatus.CANCELED) {
            payment.cancelFromProvider(providerStatus, now);
        } else {
            payment.fail(
                    failureCode,
                    safeFailureMessage(failureMessage),
                    providerStatus,
                    now
            );
        }
        order.markPaymentFailed();
    }

    private void validateGatewayResult(
            Payment payment,
            String providerPaymentKey,
            String merchantPaymentId,
            Long amount,
            String currency
    ) {
        if (!Objects.equals(payment.getProviderPaymentKey(), providerPaymentKey)) {
            throw new PaymentException("결제 결과를 확인 중입니다.");
        }
        validateGatewayBusinessResult(
                payment,
                merchantPaymentId,
                amount,
                currency
        );
    }

    private void validateGatewayBusinessResult(
            Payment payment,
            String merchantPaymentId,
            Long amount,
            String currency
    ) {
        if (!Objects.equals(payment.getMerchantPaymentId(), merchantPaymentId)
                || !Objects.equals(payment.getAmount(), amount)
                || !Objects.equals(payment.getCurrency(), currency)) {
            throw new PaymentException("결제 결과를 확인 중입니다.");
        }
    }

    private void removeUnchangedCartItems(Long userId, Long orderId) {
        for (OrderItem orderItem
                : orderItemRepository.findAllByOrderIdOrderByIdAsc(orderId)) {
            if (orderItem.getSourceCartItemId() == null) {
                continue;
            }

            cartItemRepository.findByIdAndUserId(
                            orderItem.getSourceCartItemId(),
                            userId
                    )
                    .filter(cartItem -> matches(orderItem, cartItem))
                    .ifPresent(cartItemRepository::delete);
        }
    }

    private boolean matches(OrderItem orderItem, CartItem cartItem) {
        Long orderVariantId = orderItem.getVariant() == null
                ? null
                : orderItem.getVariant().getId();
        Long cartVariantId = cartItem.getVariant() == null
                ? null
                : cartItem.getVariant().getId();
        return Objects.equals(
                orderItem.getProduct().getId(),
                cartItem.getProduct().getId()
        ) && Objects.equals(orderVariantId, cartVariantId)
                && Objects.equals(orderItem.getQuantity(), cartItem.getQuantity());
    }

    private void validateRequest(
            Payment payment,
            PaymentConfirmRequest request
    ) {
        if (!Objects.equals(payment.getMerchantPaymentId(), request.merchantPaymentId())) {
            throw new PaymentException("결제 주문정보가 일치하지 않습니다.");
        }
        if (!Objects.equals(payment.getAmount(), request.amount())) {
            throw new PaymentException("결제 금액이 일치하지 않습니다.");
        }
    }

    private Payment getPaymentForUpdate(Long userId, Long paymentId) {
        return paymentRepository.findByIdAndOrderUserIdForUpdate(paymentId, userId)
                .orElseThrow(() -> new PaymentException(
                        "결제 정보를 찾을 수 없습니다."
                ));
    }

    private Order getOrderForUpdate(Long userId, Long orderId) {
        return orderRepository.findByIdAndUserIdForUpdate(orderId, userId)
                .orElseThrow(() -> new PaymentException(
                        "주문 정보를 찾을 수 없습니다."
                ));
    }

    private Payment getPaymentForUpdate(Long paymentId) {
        return paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentException(
                        "결제 정보를 찾을 수 없습니다."
                ));
    }

    private Order getOrderForUpdate(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new PaymentException(
                        "주문 정보를 찾을 수 없습니다."
                ));
    }

    private PaymentConfirmStart start(
            Payment payment,
            PaymentConfirmStart.Action action
    ) {
        return new PaymentConfirmStart(
                action,
                payment.getProvider(),
                payment.getProviderPaymentKey(),
                payment.getMerchantPaymentId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getConfirmIdempotencyKey(),
                payment.getConfirmingAt(),
                null
        );
    }

    private PaymentConfirmStart completed(Payment payment) {
        return new PaymentConfirmStart(
                PaymentConfirmStart.Action.COMPLETED,
                payment.getProvider(),
                payment.getProviderPaymentKey(),
                payment.getMerchantPaymentId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getConfirmIdempotencyKey(),
                payment.getConfirmingAt(),
                PaymentResponse.from(payment)
        );
    }

    private String safeFailureMessage(String message) {
        if (message == null || message.isBlank()) {
            return "결제 승인이 거절되었습니다.";
        }
        return message.length() <= 500
                ? message
                : message.substring(0, 500);
    }
}
