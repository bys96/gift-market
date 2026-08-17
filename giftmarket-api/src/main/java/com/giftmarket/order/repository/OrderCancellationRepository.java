package com.giftmarket.order.repository;

import com.giftmarket.order.entity.OrderCancellation;
import com.giftmarket.order.entity.OrderCancellationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface OrderCancellationRepository extends JpaRepository<OrderCancellation, Long> {

    Optional<OrderCancellation> findByClientRequestKey(String clientRequestKey);

    List<OrderCancellation> findAllByOrderIdOrderByRequestedAtDescIdDesc(Long orderId);

    @Query(
            value = """
                    select c
                    from OrderCancellation c
                    join fetch c.order
                    join fetch c.sellerOrder so
                    where so.seller.id = :sellerId
                      and c.requiresSellerApproval = true
                      and (:status is null or c.status = :status)
                    order by case when c.status = com.giftmarket.order.entity.OrderCancellationStatus.REQUESTED
                                  then 0 else 1 end,
                             c.requestedAt desc,
                             c.id desc
                    """,
            countQuery = """
                    select count(c.id)
                    from OrderCancellation c
                    where c.sellerOrder.seller.id = :sellerId
                      and c.requiresSellerApproval = true
                      and (:status is null or c.status = :status)
                    """
    )
    Page<OrderCancellation> findSellerApprovalCancellations(
            @Param("sellerId") Long sellerId,
            @Param("status") OrderCancellationStatus status,
            Pageable pageable
    );

    @Query("""
            select c.order.id as orderId,
                   c.sellerOrder.id as sellerOrderId
            from OrderCancellation c
            where c.id = :cancellationId
              and c.sellerOrder.seller.id = :sellerId
              and c.requiresSellerApproval = true
            """)
    Optional<OrderCancellationOwnershipProjection> findOwnership(
            @Param("cancellationId") Long cancellationId,
            @Param("sellerId") Long sellerId
    );

    boolean existsBySellerOrderIdAndStatusIn(
            Long sellerOrderId,
            Collection<OrderCancellationStatus> statuses
    );

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
