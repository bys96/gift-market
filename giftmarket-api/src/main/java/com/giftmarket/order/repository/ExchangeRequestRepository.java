package com.giftmarket.order.repository;

import com.giftmarket.order.entity.ExchangeRequest;
import com.giftmarket.order.entity.ExchangeRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExchangeRequestRepository extends JpaRepository<ExchangeRequest, Long> {
    Optional<ExchangeRequest> findByClientRequestKey(String clientRequestKey);

    @EntityGraph(attributePaths = {"order", "sellerOrder", "collectionShipment", "outboundShipment"})
    List<ExchangeRequest> findAllByOrderIdOrderByRequestedAtDescIdDesc(Long orderId);

    List<ExchangeRequest> findAllBySellerOrderIdOrderByRequestedAtDescIdDesc(Long sellerOrderId);

    @Query("""
            select ei.orderItem.id as orderItemId, sum(ei.quantity) as pendingQuantity
            from ExchangeRequestItem ei
            where ei.orderItem.id in :orderItemIds
              and ei.exchangeRequest.status in :statuses
            group by ei.orderItem.id
            """)
    List<PendingExchangeQuantityProjection> sumItemQuantitiesByStatuses(
            @Param("orderItemIds") Collection<Long> orderItemIds,
            @Param("statuses") Collection<ExchangeRequestStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ExchangeRequest e where e.id = :id")
    Optional<ExchangeRequest> findByIdForUpdate(@Param("id") Long id);
}
