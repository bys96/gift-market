package com.giftmarket.settlement.service;

import com.giftmarket.order.entity.ExchangeRequestStatus;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.entity.ReturnRequestStatus;
import com.giftmarket.order.repository.ExchangeRequestRepository;
import com.giftmarket.order.repository.OrderCancellationRepository;
import com.giftmarket.order.repository.ReturnRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ActiveSettlementClaimService {

    private static final Set<OrderCancellationStatus> ACTIVE_CANCELLATIONS = Set.of(
            OrderCancellationStatus.REQUESTED,
            OrderCancellationStatus.PROCESSING
    );
    private static final Set<ReturnRequestStatus> ACTIVE_RETURNS = Set.of(
            ReturnRequestStatus.REQUESTED,
            ReturnRequestStatus.APPROVED,
            ReturnRequestStatus.COLLECTING,
            ReturnRequestStatus.RECEIVED,
            ReturnRequestStatus.INSPECTED,
            ReturnRequestStatus.REFUNDING
    );
    private static final Set<ExchangeRequestStatus> ACTIVE_EXCHANGES = Set.of(
            ExchangeRequestStatus.REQUESTED,
            ExchangeRequestStatus.APPROVED,
            ExchangeRequestStatus.PAYMENT_PENDING,
            ExchangeRequestStatus.COLLECTING,
            ExchangeRequestStatus.RECEIVED,
            ExchangeRequestStatus.INSPECTED,
            ExchangeRequestStatus.RESHIPPING
    );

    private final OrderCancellationRepository cancellationRepository;
    private final ReturnRequestRepository returnRepository;
    private final ExchangeRequestRepository exchangeRepository;

    public Set<Long> findActiveSellerOrderIds(Collection<Long> sellerOrderIds) {
        if (sellerOrderIds == null || sellerOrderIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> activeIds = new HashSet<>();
        activeIds.addAll(cancellationRepository.findSellerOrderIdsWithStatuses(
                sellerOrderIds,
                ACTIVE_CANCELLATIONS
        ));
        activeIds.addAll(returnRepository.findSellerOrderIdsWithStatuses(
                sellerOrderIds,
                ACTIVE_RETURNS
        ));
        activeIds.addAll(exchangeRepository.findSellerOrderIdsWithStatuses(
                sellerOrderIds,
                ACTIVE_EXCHANGES
        ));
        return Set.copyOf(activeIds);
    }
}
