package com.giftmarket.payment.service;

import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.*;
import com.giftmarket.payment.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class FullOrderCancellationPolicy {
    private final SellerOrderRepository sellerOrders;
    private final OrderItemRepository orderItems;
    private final OrderCancellationRepository cancellations;
    private final ReturnRequestRepository returns;
    private final ExchangeRequestRepository exchanges;

    private static final Set<OrderCancellationStatus> ACTIVE_CANCELLATIONS = Set.of(
            OrderCancellationStatus.REQUESTED, OrderCancellationStatus.PROCESSING);
    private static final Set<ReturnRequestStatus> ACTIVE_RETURNS = Set.of(
            ReturnRequestStatus.REQUESTED, ReturnRequestStatus.APPROVED, ReturnRequestStatus.COLLECTING,
            ReturnRequestStatus.RECEIVED, ReturnRequestStatus.INSPECTED, ReturnRequestStatus.REFUNDING);
    private static final Set<ExchangeRequestStatus> ACTIVE_EXCHANGES = Set.of(
            ExchangeRequestStatus.REQUESTED, ExchangeRequestStatus.APPROVED, ExchangeRequestStatus.PAYMENT_PENDING,
            ExchangeRequestStatus.COLLECTING, ExchangeRequestStatus.RECEIVED, ExchangeRequestStatus.INSPECTED,
            ExchangeRequestStatus.RESHIPPING);

    /** The caller holds Payment -> Order locks before locking SellerOrders and their items. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void validate(Order order, SellerOrderStatus requiredStatus) {
        var sellers = sellerOrders.findAllByOrderIdForUpdate(order.getId());
        if (sellers.isEmpty()) throw unavailable();
        for (SellerOrder seller : sellers) {
            if (seller.getStatus() != requiredStatus) throw unavailable();
            var items = orderItems.findAllBySellerOrderIdForUpdate(seller.getId());
            if (items.isEmpty() || items.stream().anyMatch(item ->
                    item.getConfirmedQuantity() != 0 || item.getCanceledQuantity() != 0
                            || item.getReturnedQuantity() != 0 || item.getExchangedQuantity() != 0)) {
                throw unavailable();
            }
            if (cancellations.existsBySellerOrderIdAndStatusIn(seller.getId(), ACTIVE_CANCELLATIONS)
                    || returns.existsBySellerOrderIdAndStatusIn(seller.getId(), ACTIVE_RETURNS)
                    || exchanges.existsBySellerOrderIdAndStatusIn(seller.getId(), ACTIVE_EXCHANGES)) {
                throw unavailable();
            }
        }
    }

    private PaymentException unavailable() {
        return new PaymentException("전체취소가 불가능한 상품 또는 진행 중인 요청이 있습니다. 판매자별 취소·반품·교환 절차를 이용해주세요.");
    }
}
