package com.giftmarket.order.repository;

import com.giftmarket.order.entity.ExchangeRequest;
import com.giftmarket.order.entity.ExchangeRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;

public interface ExchangeRequestRepository extends JpaRepository<ExchangeRequest, Long> {
    @EntityGraph(attributePaths={"order","order.user","sellerOrder","sellerOrder.seller","collectionShipment","outboundShipment"})
    @Query(value="""
            select e from ExchangeRequest e where (:keyword is null or lower(e.order.orderNumber) like lower(concat('%',:keyword,'%')) or lower(e.order.user.name) like lower(concat('%',:keyword,'%')) or lower(e.order.user.email) like lower(concat('%',:keyword,'%')) or lower(e.sellerOrder.seller.storeName) like lower(concat('%',:keyword,'%')) or exists(select ei.id from ExchangeRequestItem ei where ei.exchangeRequest=e and lower(ei.orderItem.productName) like lower(concat('%',:keyword,'%')))) and (:status is null or e.status=:status) and (:responsibility is null or e.responsibility=:responsibility)
            """,countQuery="""
            select count(e.id) from ExchangeRequest e where (:keyword is null or lower(e.order.orderNumber) like lower(concat('%',:keyword,'%')) or lower(e.order.user.name) like lower(concat('%',:keyword,'%')) or lower(e.order.user.email) like lower(concat('%',:keyword,'%')) or lower(e.sellerOrder.seller.storeName) like lower(concat('%',:keyword,'%')) or exists(select ei.id from ExchangeRequestItem ei where ei.exchangeRequest=e and lower(ei.orderItem.productName) like lower(concat('%',:keyword,'%')))) and (:status is null or e.status=:status) and (:responsibility is null or e.responsibility=:responsibility)
            """) Page<ExchangeRequest> findAdminExchanges(@Param("keyword")String keyword,@Param("status")ExchangeRequestStatus status,@Param("responsibility")com.giftmarket.order.entity.ExchangeResponsibility responsibility,Pageable pageable);
    @EntityGraph(attributePaths={"order","order.user","sellerOrder","sellerOrder.seller","collectionShipment","outboundShipment"}) @Query("select e from ExchangeRequest e where e.id=:id") Optional<ExchangeRequest> findAdminById(@Param("id")Long id);
    long countByStatus(ExchangeRequestStatus status);
    long countByOrderId(Long orderId);
    long countBySellerOrderSellerIdAndStatus(
            Long sellerId,
            ExchangeRequestStatus status
    );

    Optional<ExchangeRequest> findByClientRequestKey(String clientRequestKey);

    @EntityGraph(attributePaths = {"order", "sellerOrder", "collectionShipment", "outboundShipment"})
    List<ExchangeRequest> findAllByOrderIdOrderByRequestedAtDescIdDesc(Long orderId);

    List<ExchangeRequest> findAllBySellerOrderIdOrderByRequestedAtDescIdDesc(Long sellerOrderId);

    @Query(
            value = """
                    select e from ExchangeRequest e
                    join fetch e.order
                    join fetch e.sellerOrder so
                    left join fetch e.collectionShipment
                    left join fetch e.outboundShipment
                    where so.seller.id = :sellerId
                      and (:status is null or e.status = :status)
                    order by e.requestedAt desc, e.id desc
                    """,
            countQuery = """
                    select count(e.id) from ExchangeRequest e
                    where e.sellerOrder.seller.id = :sellerId
                      and (:status is null or e.status = :status)
                    """
    )
    Page<ExchangeRequest> findSellerExchanges(
            @Param("sellerId") Long sellerId,
            @Param("status") ExchangeRequestStatus status,
            Pageable pageable
    );

    @Query("""
            select e.order.id as orderId, e.sellerOrder.id as sellerOrderId
            from ExchangeRequest e
            where e.id = :exchangeRequestId
              and e.sellerOrder.seller.id = :sellerId
            """)
    Optional<ExchangeRequestOwnershipProjection> findOwnership(
            @Param("exchangeRequestId") Long exchangeRequestId,
            @Param("sellerId") Long sellerId
    );

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

    @Query("""
            select ei.orderItem.id as orderItemId, sum(ei.quantity) as pendingQuantity
            from ExchangeRequestItem ei
            where ei.orderItem.id in :orderItemIds
              and ei.exchangeRequest.id <> :excludedExchangeRequestId
              and ei.exchangeRequest.status in :statuses
            group by ei.orderItem.id
            """)
    List<PendingExchangeQuantityProjection> sumItemQuantitiesByStatusesExcludingRequest(
            @Param("orderItemIds") Collection<Long> orderItemIds,
            @Param("excludedExchangeRequestId") Long excludedExchangeRequestId,
            @Param("statuses") Collection<ExchangeRequestStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ExchangeRequest e where e.id = :id")
    Optional<ExchangeRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("select e.id from ExchangeRequest e where e.status = :status and e.paymentDueAt < :now order by e.id")
    List<Long> findExpiredPaymentCandidateIds(@Param("status") ExchangeRequestStatus status,
                                               @Param("now") LocalDateTime now, Pageable pageable);
}
