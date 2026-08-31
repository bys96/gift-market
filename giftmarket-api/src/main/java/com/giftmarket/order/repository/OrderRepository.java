package com.giftmarket.order.repository;

import com.giftmarket.order.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.payment.entity.PaymentStatus;

import java.util.List;
import java.util.Optional;

public interface OrderRepository
        extends JpaRepository<Order, Long> {

    Page<Order> findAllByUserId(
            Long userId,
            Pageable pageable
    );

    long countByUserId(Long userId);

    Page<Order> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "user")
    @Query(value = """
            select o from Order o
            where (:keyword is null
                   or lower(o.orderNumber) like lower(concat('%', :keyword, '%'))
                   or lower(o.user.name) like lower(concat('%', :keyword, '%'))
                   or lower(o.user.email) like lower(concat('%', :keyword, '%')))
              and (:orderStatus is null or o.status = :orderStatus)
              and (:paymentStatus is null or exists (
                    select p.id from Payment p where p.order = o and p.status = :paymentStatus
                      and p.id = (select max(p2.id) from Payment p2 where p2.order = o)
              ))
              and (:sellerOrderStatus is null or exists (
                    select so.id from SellerOrder so where so.order = o and so.status = :sellerOrderStatus
              ))
            """,
            countQuery = """
            select count(o.id) from Order o
            where (:keyword is null
                   or lower(o.orderNumber) like lower(concat('%', :keyword, '%'))
                   or lower(o.user.name) like lower(concat('%', :keyword, '%'))
                   or lower(o.user.email) like lower(concat('%', :keyword, '%')))
              and (:orderStatus is null or o.status = :orderStatus)
              and (:paymentStatus is null or exists (
                    select p.id from Payment p where p.order = o and p.status = :paymentStatus
                      and p.id = (select max(p2.id) from Payment p2 where p2.order = o)
              ))
              and (:sellerOrderStatus is null or exists (
                    select so.id from SellerOrder so where so.order = o and so.status = :sellerOrderStatus
              ))
            """)
    Page<Order> findAdminOrders(@Param("keyword") String keyword,
                                @Param("orderStatus") com.giftmarket.order.entity.OrderStatus orderStatus,
                                @Param("paymentStatus") PaymentStatus paymentStatus,
                                @Param("sellerOrderStatus") SellerOrderStatus sellerOrderStatus,
                                Pageable pageable);

    @EntityGraph(attributePaths = "user")
    @Query("select o from Order o where o.id = :orderId")
    Optional<Order> findAdminById(@Param("orderId") Long orderId);

    Optional<Order> findByIdAndUserId(
            Long orderId,
            Long userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from Order o
            where o.id = :orderId
              and o.user.id = :userId
            """)
    Optional<Order> findByIdAndUserIdForUpdate(
            @Param("orderId") Long orderId,
            @Param("userId") Long userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from Order o
            where o.id = :orderId
            """)
    Optional<Order> findByIdForUpdate(
            @Param("orderId") Long orderId
    );
}
