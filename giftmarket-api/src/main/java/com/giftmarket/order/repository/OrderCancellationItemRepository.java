package com.giftmarket.order.repository;

import com.giftmarket.order.entity.OrderCancellationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderCancellationItemRepository extends JpaRepository<OrderCancellationItem, Long> {

    @EntityGraph(attributePaths = "orderItem")
    List<OrderCancellationItem> findAllByOrderCancellationIdOrderByIdAsc(Long orderCancellationId);

    @EntityGraph(attributePaths = "orderItem")
    List<OrderCancellationItem> findAllByOrderCancellationIdInOrderByOrderCancellationIdAscOrderItemIdAsc(
            List<Long> orderCancellationIds
    );

    @Query("""
            select ci.orderCancellation.id as cancellationId,
                   min(ci.orderItem.productName) as representativeProductName,
                   count(ci.id) as productTypeCount, sum(ci.quantity) as requestedQuantity
            from OrderCancellationItem ci where ci.orderCancellation.id in :ids
            group by ci.orderCancellation.id
            """)
    List<AdminCancellationItemSummaryProjection> summarizeAdminCancellations(@Param("ids") List<Long> ids);

    @EntityGraph(attributePaths = {"orderItem", "orderItem.product"})
    List<OrderCancellationItem> findAdminByOrderCancellationIdOrderByIdAsc(Long orderCancellationId);
}
