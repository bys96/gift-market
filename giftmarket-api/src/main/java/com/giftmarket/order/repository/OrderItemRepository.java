package com.giftmarket.order.repository;

import com.giftmarket.order.entity.OrderItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderItemRepository
        extends JpaRepository<OrderItem, Long> {

    @EntityGraph(attributePaths = {
            "product",
            "variant"
    })
    List<OrderItem> findAllByOrderIdOrderByIdAsc(
            Long orderId
    );

    @EntityGraph(attributePaths = {"product", "variant"})
    List<OrderItem> findAllBySellerOrderIdOrderByIdAsc(Long sellerOrderId);

    @Query("""
            select oi.sellerOrder.id as sellerOrderId,
                   min(oi.productName) as representativeProductName,
                   count(oi.id) as productTypeCount,
                   sum(oi.quantity) as totalQuantity,
                   sum(oi.totalPrice) as totalProductAmount
            from OrderItem oi
            where oi.sellerOrder.id in :sellerOrderIds
            group by oi.sellerOrder.id
            """)
    List<SellerOrderItemSummaryProjection> summarizeBySellerOrderIds(
            @Param("sellerOrderIds") List<Long> sellerOrderIds
    );
}
