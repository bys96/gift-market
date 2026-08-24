package com.giftmarket.payment.service;

import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.*;
import com.giftmarket.order.service.OrderInventoryService;
import com.giftmarket.payment.entity.*;
import com.giftmarket.payment.repository.ExchangeShippingPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExchangePaymentExpirationTransactionService {
    private final ExchangeShippingPaymentRepository paymentRepository;
    private final ExchangeRequestRepository exchangeRepository;
    private final ExchangeRequestItemRepository exchangeItemRepository;
    private final OrderRepository orderRepository;
    private final SellerOrderRepository sellerOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderInventoryService inventoryService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expire(Long requestId, LocalDateTime now) {
        ExchangeShippingPayment payment = paymentRepository.findByExchangeRequestIdForUpdate(requestId).orElse(null);
        ExchangeRequest snapshot = exchangeRepository.findById(requestId).orElse(null);
        if (snapshot == null) return false;
        Order order = orderRepository.findByIdForUpdate(snapshot.getOrder().getId()).orElseThrow();
        sellerOrderRepository.findByIdAndOrderIdForUpdate(snapshot.getSellerOrder().getId(), order.getId()).orElseThrow();
        ExchangeRequest exchange = exchangeRepository.findByIdForUpdate(requestId).orElseThrow();
        if (exchange.getStatus() != ExchangeRequestStatus.PAYMENT_PENDING || exchange.getPaymentDueAt() == null
                || !exchange.getPaymentDueAt().isBefore(now)) return false;
        if (payment != null && (payment.getStatus() == ExchangeShippingPaymentStatus.REQUESTED
                || payment.getStatus() == ExchangeShippingPaymentStatus.SUCCEEDED
                || payment.getStatus() == ExchangeShippingPaymentStatus.COMPENSATION_REQUIRED)) return false;

        List<ExchangeRequestItem> items = exchangeItemRepository.findAllByExchangeRequestIdOrderByOrderItemIdAsc(requestId);
        List<Long> orderItemIds = items.stream().map(item -> item.getOrderItem().getId()).sorted().toList();
        if (items.isEmpty() || orderItemRepository.findAllByIdInForUpdate(orderItemIds).size() != orderItemIds.size())
            throw new IllegalStateException("교환 상품 정보를 확인할 수 없습니다.");
        inventoryService.releaseExchangeTargets(items);
        for (ExchangeRequestItem item : items) if (item.getEffectiveReservedQuantity() != 0)
            throw new IllegalStateException("교환 target 예약 해제가 완료되지 않았습니다.");
        if (payment != null) payment.expire(now);
        exchange.cancelExpiredPayment(now);
        return true;
    }
}
