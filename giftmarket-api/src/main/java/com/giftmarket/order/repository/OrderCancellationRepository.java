package com.giftmarket.order.repository;

import com.giftmarket.order.entity.OrderCancellation;
import com.giftmarket.order.entity.OrderCancellationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface OrderCancellationRepository extends JpaRepository<OrderCancellation, Long> {

    Optional<OrderCancellation> findByClientRequestKey(String clientRequestKey);

    @Query("""
            select ci.orderItem.id as orderItemId,
                   sum(ci.quantity) as pendingQuantity
            from OrderCancellationItem ci
            where ci.orderItem.id in :orderItemIds
              and ci.orderCancellation.status in :statuses
            group by ci.orderItem.id
            """)
    List<PendingCancellationQuantityProjection> sumItemQuantitiesByStatuses(
            @Param("orderItemIds") Collection<Long> orderItemIds,
            @Param("statuses") Collection<OrderCancellationStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OrderCancellation c where c.id = :id")
    Optional<OrderCancellation> findByIdForUpdate(@Param("id") Long id);
}
