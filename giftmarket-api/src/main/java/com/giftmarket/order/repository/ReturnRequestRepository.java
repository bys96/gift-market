package com.giftmarket.order.repository;

import com.giftmarket.order.entity.ReturnRequest;
import com.giftmarket.order.entity.ReturnRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    Optional<ReturnRequest> findByClientRequestKey(String clientRequestKey);

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReturnRequest r where r.id = :id")
    Optional<ReturnRequest> findByIdForUpdate(
            @Param("id") Long id
    );
}