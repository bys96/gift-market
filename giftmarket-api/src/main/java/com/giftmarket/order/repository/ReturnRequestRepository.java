package com.giftmarket.order.repository;

import com.giftmarket.order.entity.ReturnRequest;
import com.giftmarket.order.entity.ReturnRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    Optional<ReturnRequest> findByClientRequestKey(String clientRequestKey);

    @Query(
            value = """
                    select r
                    from ReturnRequest r
                    join fetch r.order
                    join fetch r.sellerOrder so
                    left join fetch r.collectionShipment
                    where so.seller.id = :sellerId
                      and (:status is null or r.status = :status)
                    order by r.requestedAt desc, r.id desc
                    """,
            countQuery = """
                    select count(r.id)
                    from ReturnRequest r
                    where r.sellerOrder.seller.id = :sellerId
                      and (:status is null or r.status = :status)
                    """
    )
    Page<ReturnRequest> findSellerReturns(
            @Param("sellerId") Long sellerId,
            @Param("status") ReturnRequestStatus status,
            Pageable pageable
    );

    @Query("""
            select r.order.id as orderId,
                   r.sellerOrder.id as sellerOrderId
            from ReturnRequest r
            where r.id = :returnRequestId
              and r.sellerOrder.seller.id = :sellerId
            """)
    Optional<ReturnRequestOwnershipProjection> findOwnership(
            @Param("returnRequestId") Long returnRequestId,
            @Param("sellerId") Long sellerId
    );

    List<ReturnRequest> findAllByOrderIdOrderByRequestedAtDescIdDesc(
            Long orderId
    );

    List<ReturnRequest> findAllBySellerOrderIdOrderByRequestedAtDescIdDesc(
            Long sellerOrderId
    );

    boolean existsBySellerOrderIdAndStatusIn(
            Long sellerOrderId,
            Collection<ReturnRequestStatus> statuses
    );

    @Query("""
            select ri.orderItem.id as orderItemId,
                   sum(ri.quantity) as pendingQuantity
            from ReturnRequestItem ri
            where ri.orderItem.id in :orderItemIds
              and ri.returnRequest.status in :statuses
            group by ri.orderItem.id
            """)
    List<PendingReturnQuantityProjection> sumItemQuantitiesByStatuses(
            @Param("orderItemIds") Collection<Long> orderItemIds,
            @Param("statuses") Collection<ReturnRequestStatus> statuses
    );

    @Query("""
            select ri.orderItem.id as orderItemId,
                   sum(ri.quantity) as pendingQuantity
            from ReturnRequestItem ri
            where ri.returnRequest.sellerOrder.id = :sellerOrderId
              and ri.returnRequest.id <> :excludedReturnRequestId
              and ri.returnRequest.refundAmount is not null
              and ri.returnRequest.status in :statuses
            group by ri.orderItem.id
            """)
    List<PendingReturnQuantityProjection> sumCalculatedItemQuantities(
            @Param("sellerOrderId") Long sellerOrderId,
            @Param("excludedReturnRequestId") Long excludedReturnRequestId,
            @Param("statuses") Collection<ReturnRequestStatus> statuses
    );

    boolean existsBySellerOrderIdAndIdNotAndOriginalShippingRefundAmountGreaterThanAndStatusIn(
            Long sellerOrderId,
            Long excludedReturnRequestId,
            Long amount,
            Collection<ReturnRequestStatus> statuses
    );

    @Query("""
            select coalesce(sum(r.refundAmount), 0)
            from ReturnRequest r
            where r.order.id = :orderId
              and r.id <> :excludedReturnRequestId
              and r.refundAmount is not null
              and r.status in :statuses
            """)
    Long sumRefundAmountByOrderIdExcludingRequest(
            @Param("orderId") Long orderId,
            @Param("excludedReturnRequestId") Long excludedReturnRequestId,
            @Param("statuses") Collection<ReturnRequestStatus> statuses
    );

    @Query("""
            select coalesce(sum(r.refundAmount), 0)
            from ReturnRequest r
            where r.order.id = :orderId
              and r.id <> :excludedReturnRequestId
              and r.refundAmount is not null
              and r.status in :statuses
              and not exists (
                    select pc.id from PaymentCancellation pc
                    where pc.returnRequest = r
                      and pc.status in :cancellationStatuses
              )
            """)
    Long sumUnreservedRefundAmountByOrderIdExcludingRequest(
            @Param("orderId") Long orderId,
            @Param("excludedReturnRequestId") Long excludedReturnRequestId,
            @Param("statuses") Collection<ReturnRequestStatus> statuses,
            @Param("cancellationStatuses") Collection<com.giftmarket.payment.entity.PaymentCancellationStatus> cancellationStatuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReturnRequest r where r.id = :id")
    Optional<ReturnRequest> findByIdForUpdate(
            @Param("id") Long id
    );
}
