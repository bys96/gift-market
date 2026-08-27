package com.giftmarket.order.service;

import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PurchaseConfirmationQuantities {
    private static final Set<OrderCancellationStatus> CANCELLATION_HOLDING = Set.of(
            OrderCancellationStatus.REQUESTED, OrderCancellationStatus.PROCESSING);
    private static final Set<ReturnRequestStatus> RETURN_HOLDING = Set.of(
            ReturnRequestStatus.REQUESTED, ReturnRequestStatus.APPROVED,
            ReturnRequestStatus.COLLECTING, ReturnRequestStatus.RECEIVED,
            ReturnRequestStatus.INSPECTED, ReturnRequestStatus.REFUNDING);
    private static final Set<ExchangeRequestStatus> EXCHANGE_HOLDING = Set.of(
            ExchangeRequestStatus.REQUESTED, ExchangeRequestStatus.APPROVED,
            ExchangeRequestStatus.PAYMENT_PENDING, ExchangeRequestStatus.COLLECTING,
            ExchangeRequestStatus.RECEIVED, ExchangeRequestStatus.INSPECTED,
            ExchangeRequestStatus.RESHIPPING);

    private final OrderCancellationRepository cancellationRepository;
    private final ReturnRequestRepository returnRepository;
    private final ExchangeRequestRepository exchangeRepository;

    public PendingQuantities load(Collection<Long> itemIds) {
        if (itemIds.isEmpty()) return new PendingQuantities(Map.of(), Map.of(), Map.of());
        return new PendingQuantities(
                cancellationRepository.sumItemQuantitiesByStatuses(itemIds, CANCELLATION_HOLDING).stream()
                        .collect(Collectors.toMap(PendingCancellationQuantityProjection::getOrderItemId,
                                PendingCancellationQuantityProjection::getPendingQuantity)),
                returnRepository.sumItemQuantitiesByStatuses(itemIds, RETURN_HOLDING).stream()
                        .collect(Collectors.toMap(PendingReturnQuantityProjection::getOrderItemId,
                                PendingReturnQuantityProjection::getPendingQuantity)),
                exchangeRepository.sumItemQuantitiesByStatuses(itemIds, EXCHANGE_HOLDING).stream()
                        .collect(Collectors.toMap(PendingExchangeQuantityProjection::getOrderItemId,
                                PendingExchangeQuantityProjection::getPendingQuantity))
        );
    }

    public int confirmable(OrderItem item, PendingQuantities pending) {
        long value = (long) item.getQuantity() - item.getCanceledQuantity()
                - item.getReturnedQuantity() - item.getConfirmedQuantity()
                - pending.cancellations().getOrDefault(item.getId(), 0L)
                - pending.returns().getOrDefault(item.getId(), 0L)
                - pending.exchanges().getOrDefault(item.getId(), 0L);
        return Math.toIntExact(Math.max(0L, value));
    }

    public record PendingQuantities(
            Map<Long, Long> cancellations,
            Map<Long, Long> returns,
            Map<Long, Long> exchanges
    ) {}
}
