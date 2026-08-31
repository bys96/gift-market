package com.giftmarket.order.repository;

import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface SellerOrderRepository
        extends JpaRepository<SellerOrder, Long> {

    long countBySellerId(Long sellerId);

    long countBySellerIdAndStatusIn(
            Long sellerId,
            Collection<SellerOrderStatus> statuses
    );

    @Query("""
            select so
            from SellerOrder so
            join fetch so.order o
            where so.seller.id = :sellerId
              and so.status <> :excludedStatus
            order by o.orderedAt desc, so.id desc
            """)
    List<SellerOrder> findRecentSellerOrders(
            @Param("sellerId") Long sellerId,
            @Param("excludedStatus") SellerOrderStatus excludedStatus,
            Pageable pageable
    );

    Optional<SellerOrder> findByOrderIdAndSellerId(
            Long orderId,
            Long sellerId
    );

    List<SellerOrder> findAllByOrderIdOrderByIdAsc(Long orderId);

    @EntityGraph(attributePaths = {"seller", "order"})
    List<SellerOrder> findAllByOrderIdInOrderByOrderIdAscIdAsc(
            List<Long> orderIds
    );

    @Query(
            value = """
                    select so
                    from SellerOrder so
                    join fetch so.order o
                    where so.seller.id = :sellerId
                      and so.status <> :excludedStatus
                      and (:status is null or so.status = :status)
                      and (
                            :keyword is null
                            or lower(o.orderNumber) like lower(concat('%', :keyword, '%'))
                            or exists (
                                select oi.id
                                from OrderItem oi
                                where oi.sellerOrder = so
                                  and lower(oi.productName) like lower(concat('%', :keyword, '%'))
                            )
                      )
                    """,
            countQuery = """
                    select count(so.id)
                    from SellerOrder so
                    join so.order o
                    where so.seller.id = :sellerId
                      and so.status <> :excludedStatus
                      and (:status is null or so.status = :status)
                      and (
                            :keyword is null
                            or lower(o.orderNumber) like lower(concat('%', :keyword, '%'))
                            or exists (
                                select oi.id
                                from OrderItem oi
                                where oi.sellerOrder = so
                                  and lower(oi.productName) like lower(concat('%', :keyword, '%'))
                            )
                      )
                    """
    )
    Page<SellerOrder> findSellerOrders(
            @Param("sellerId") Long sellerId,
            @Param("excludedStatus") SellerOrderStatus excludedStatus,
            @Param("status") SellerOrderStatus status,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"order", "seller"})
    Optional<SellerOrder> findByIdAndSellerId(
            Long sellerOrderId,
            Long sellerId
    );

    @Query("""
            select so.order.id
            from SellerOrder so
            where so.id = :sellerOrderId
              and so.seller.id = :sellerId
            """)
    Optional<Long> findOrderIdByIdAndSellerId(
            @Param("sellerOrderId") Long sellerOrderId,
            @Param("sellerId") Long sellerId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select so
            from SellerOrder so
            join fetch so.order
            where so.id = :sellerOrderId
              and so.seller.id = :sellerId
            """)
    Optional<SellerOrder> findByIdAndSellerIdForUpdate(
            @Param("sellerOrderId") Long sellerOrderId,
            @Param("sellerId") Long sellerId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select so
            from SellerOrder so
            where so.id = :sellerOrderId
              and so.order.id = :orderId
            """)
    Optional<SellerOrder> findByIdAndOrderIdForUpdate(
            @Param("sellerOrderId") Long sellerOrderId,
            @Param("orderId") Long orderId
    );
}
