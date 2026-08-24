package com.giftmarket.payment.service;

import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.*;
import com.giftmarket.payment.dto.request.PaymentConfirmRequest;
import com.giftmarket.payment.dto.response.ExchangeShippingPaymentResponse;
import com.giftmarket.payment.entity.*;
import com.giftmarket.payment.exception.PaymentException;
import com.giftmarket.payment.gateway.*;
import com.giftmarket.payment.repository.ExchangeShippingPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExchangeShippingPaymentTransactionService {
    private final ExchangeShippingPaymentRepository paymentRepository;
    private final ExchangeRequestRepository exchangeRepository;
    private final ExchangeRequestItemRepository exchangeItemRepository;
    private final OrderRepository orderRepository;
    private final SellerOrderRepository sellerOrderRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public ExchangeShippingPaymentResponse prepare(Long userId, Long requestId) {
        ExchangeShippingPayment existing = paymentRepository.findByExchangeRequestIdForUpdate(requestId).orElse(null);
        ExchangeRequest exchange = lockOwnedExchange(userId, requestId);
        if (existing != null && existing.getStatus() == ExchangeShippingPaymentStatus.SUCCEEDED
                && exchange.getStatus() == ExchangeRequestStatus.COLLECTING) {
            return ExchangeShippingPaymentResponse.from(existing);
        }
        validatePayable(exchange, currentTime());
        List<ExchangeRequestItem> items = items(requestId);
        validateReservation(items);
        ExchangeShippingPayment payment = existing == null
                ? paymentRepository.save(ExchangeShippingPayment.create(exchange,
                        calculateAmount(items), providerOrderId(requestId, 1), idempotencyKey(requestId, 1)))
                : existing;
        if (payment.getStatus() == ExchangeShippingPaymentStatus.FAILED) {
            int nextAttempt = Math.addExact(payment.getAttemptSequence(), 1);
            payment.prepareRetry(providerOrderId(requestId, nextAttempt), idempotencyKey(requestId, nextAttempt));
        }
        if (payment.getAmount() == 0 && payment.getStatus() != ExchangeShippingPaymentStatus.SUCCEEDED) {
            payment.succeed(null, "ZERO_AMOUNT", currentTime());
            exchange.completeShippingPayment(currentTime());
        }
        return ExchangeShippingPaymentResponse.from(payment);
    }

    @Transactional
    public ExchangeShippingPaymentStart startConfirm(Long userId, Long requestId, PaymentConfirmRequest request) {
        ExchangeShippingPayment payment = paymentRepository.findByExchangeRequestIdForUpdate(requestId)
                .orElseThrow(() -> new PaymentException("교환 배송비 결제 정보를 찾을 수 없습니다."));
        ExchangeRequest exchange = lockOwnedExchange(userId, requestId);
        if (payment.getStatus() == ExchangeShippingPaymentStatus.SUCCEEDED) return completed(payment);
        validatePayable(exchange, currentTime());
        validateReservation(items(requestId));
        if (!Objects.equals(payment.getProviderOrderId(), request.merchantPaymentId())
                || payment.getAmount() != request.amount()) throw new PaymentException("결제 주문정보 또는 금액이 일치하지 않습니다.");
        try { payment.request(request.providerPaymentKey(), currentTime()); }
        catch (IllegalStateException exception) { throw new PaymentException(exception.getMessage()); }
        return start(payment, ExchangeShippingPaymentStart.Action.CONFIRM);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExchangeShippingPaymentStart startReconciliation(Long paymentId, LocalDateTime before) {
        ExchangeShippingPayment payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow();
        if (payment.getStatus() != ExchangeShippingPaymentStatus.REQUESTED || payment.getRequestedAt() == null
                || payment.getRequestedAt().isAfter(before)) return completed(payment);
        lockSystemExchange(payment.getExchangeRequest().getId());
        return start(payment, ExchangeShippingPaymentStart.Action.QUERY);
    }

    @Transactional
    public ExchangeShippingPaymentResponse apply(Long paymentId, GatewayConfirmResult result) {
        return apply(paymentId, result.status(), result.providerPaymentKey(), result.merchantPaymentId(),
                result.amount(), result.currency(), result.providerStatus(), result.approvedAt());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExchangeShippingPaymentResponse apply(Long paymentId, GatewayPaymentQueryResult result) {
        return apply(paymentId, result.status(), result.providerPaymentKey(), result.merchantPaymentId(),
                result.amount(), result.currency(), result.providerStatus(), result.approvedAt());
    }

    @Transactional
    public void fail(Long paymentId, String code, String message, String providerStatus) {
        ExchangeShippingPayment payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow();
        lockSystemExchange(payment.getExchangeRequest().getId());
        payment.fail(code, safe(message), providerStatus, currentTime());
    }

    @Transactional(readOnly = true)
    public ExchangeShippingPaymentResponse get(Long userId, Long requestId) {
        ExchangeShippingPayment payment = paymentRepository.findByExchangeRequestId(requestId)
                .orElseThrow(() -> new PaymentException("교환 배송비 결제 정보를 찾을 수 없습니다."));
        if (!payment.getExchangeRequest().getOrder().getUser().getId().equals(userId)) throw new PaymentException("교환 배송비 결제 정보를 찾을 수 없습니다.");
        return ExchangeShippingPaymentResponse.from(payment);
    }

    private ExchangeShippingPaymentResponse apply(Long paymentId, GatewayPaymentStatus status, String paymentKey,
                                                    String orderId, Long amount, String currency,
                                                    String providerStatus, LocalDateTime approvedAt) {
        ExchangeShippingPayment payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow();
        ExchangeRequest exchange = lockSystemExchange(payment.getExchangeRequest().getId());
        if (payment.getStatus() == ExchangeShippingPaymentStatus.SUCCEEDED) return ExchangeShippingPaymentResponse.from(payment);
        if (status == GatewayPaymentStatus.PAID) {
            validateGateway(payment, paymentKey, orderId, amount, currency);
            LocalDateTime paidAt = approvedAt == null ? currentTime() : approvedAt;
            if (exchange.getStatus() == ExchangeRequestStatus.CANCELED) {
                payment.requireCompensation(paymentKey, providerStatus, paidAt);
                return ExchangeShippingPaymentResponse.from(payment);
            }
            if (exchange.getStatus() != ExchangeRequestStatus.PAYMENT_PENDING) throw new PaymentException("교환 상태에 결제 성공을 반영할 수 없습니다.");
            validateReservation(items(exchange.getId()));
            payment.succeed(paymentKey, providerStatus, paidAt);
            exchange.completeShippingPayment(paidAt);
        } else if (status == GatewayPaymentStatus.FAILED || status == GatewayPaymentStatus.CANCELED) {
            validateGateway(payment, paymentKey, orderId, amount, currency);
            payment.fail("PAYMENT_NOT_COMPLETED", "결제가 완료되지 않았습니다.", providerStatus, currentTime());
        }
        return ExchangeShippingPaymentResponse.from(payment);
    }

    private ExchangeRequest lockOwnedExchange(Long userId, Long requestId) {
        ExchangeRequest exchange = exchangeRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new PaymentException("교환 요청 정보를 찾을 수 없습니다."));
        if (userId == null || !exchange.getOrder().getUser().getId().equals(userId)) throw new PaymentException("교환 요청 정보를 찾을 수 없습니다.");
        return exchange;
    }

    private ExchangeRequest lockSystemExchange(Long requestId) {
        ExchangeRequest initial = exchangeRepository.findById(requestId).orElseThrow();
        Order order = orderRepository.findByIdForUpdate(initial.getOrder().getId()).orElseThrow();
        sellerOrderRepository.findByIdAndOrderIdForUpdate(initial.getSellerOrder().getId(), order.getId()).orElseThrow();
        ExchangeRequest exchange = exchangeRepository.findByIdForUpdate(requestId).orElseThrow();
        if (!exchange.getOrder().getId().equals(order.getId())) throw new PaymentException("교환 결제 관계가 일치하지 않습니다.");
        List<Long> ids = items(requestId).stream().map(i -> i.getOrderItem().getId()).sorted().toList();
        if (orderItemRepository.findAllByIdInForUpdate(ids).size() != ids.size()) throw new PaymentException("교환 상품 정보를 확인할 수 없습니다.");
        return exchange;
    }

    private void validatePayable(ExchangeRequest exchange, LocalDateTime now) {
        if (exchange.getStatus() != ExchangeRequestStatus.PAYMENT_PENDING
                || exchange.getResponsibility() != ExchangeResponsibility.BUYER
                || exchange.getPaymentDueAt() == null || !now.isBefore(exchange.getPaymentDueAt()))
            throw new PaymentException("교환 배송비 결제 가능 상태 또는 기한을 확인해주세요.");
    }
    private List<ExchangeRequestItem> items(Long id) {
        List<ExchangeRequestItem> items = exchangeItemRepository.findAllByExchangeRequestIdOrderByOrderItemIdAsc(id);
        if (items.isEmpty()) throw new PaymentException("교환 상품 정보를 확인할 수 없습니다.");
        return items;
    }
    private void validateReservation(List<ExchangeRequestItem> items) {
        for (ExchangeRequestItem item : items) if (item.getReservedQuantity() != item.getQuantity()
                || item.getReleasedQuantity() != 0 || item.getConsumedQuantity() != 0
                || item.getEffectiveReservedQuantity() != item.getQuantity()) throw new PaymentException("교환 target 재고 예약 상태를 확인해주세요.");
    }
    private long calculateAmount(List<ExchangeRequestItem> items) {
        long max = 0;
        for (ExchangeRequestItem item : items) {
            Long fee = item.getOrderItem().getExchangeShippingFee();
            if (fee == null || fee < 0) throw new PaymentException("교환 배송비 snapshot을 확인해주세요.");
            max = Math.max(max, fee);
        }
        return max;
    }
    private void validateGateway(ExchangeShippingPayment p, String key, String orderId, Long amount, String currency) {
        boolean paymentKeyMismatch = p.getProviderPaymentKey() == null
                ? key == null || key.isBlank()
                : !Objects.equals(p.getProviderPaymentKey(), key);
        if (paymentKeyMismatch || !Objects.equals(p.getProviderOrderId(), orderId)
                || amount == null || p.getAmount() != amount || !"KRW".equals(currency)) throw new PaymentException("결제 결과 식별정보가 일치하지 않습니다.");
    }
    private ExchangeShippingPaymentStart start(ExchangeShippingPayment p, ExchangeShippingPaymentStart.Action action) {
        return new ExchangeShippingPaymentStart(action, p.getId(), p.getProvider(), p.getProviderPaymentKey(), p.getProviderOrderId(), p.getAmount(), p.getIdempotencyKey(), null);
    }
    private ExchangeShippingPaymentStart completed(ExchangeShippingPayment p) {
        return new ExchangeShippingPaymentStart(ExchangeShippingPaymentStart.Action.COMPLETED, p.getId(), p.getProvider(), p.getProviderPaymentKey(), p.getProviderOrderId(), p.getAmount(), p.getIdempotencyKey(), ExchangeShippingPaymentResponse.from(p));
    }
    private String providerOrderId(Long id, int attempt) { return "EXCHANGE-SHIPPING-" + id + "-" + attempt; }
    private String idempotencyKey(Long id, int attempt) { return "EXCHANGE-SHIPPING-PAYMENT-" + id + "-" + attempt; }
    private String safe(String value) { return value == null ? "결제가 거절되었습니다." : value.substring(0, Math.min(500, value.length())); }
    LocalDateTime currentTime() { return LocalDateTime.now(); }
}
