package com.giftmarket.order.repository;

import com.giftmarket.order.entity.OrderCancellationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

public interface OrderCancellationItemRepository extends JpaRepository<OrderCancellationItem, Long> {

    @EntityGraph(attributePaths = "orderItem")
    List<OrderCancellationItem> findAllByOrderCancellationIdOrderByIdAsc(Long orderCancellationId);

    @EntityGraph(attributePaths = "orderItem")
    List<OrderCancellationItem> findAllByOrderCancellationIdInOrderByOrderCancellationIdAscOrderItemIdAsc(
            List<Long> orderCancellationIds
    );
}
