package com.giftmarket.order.repository;

import com.giftmarket.order.entity.OrderItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}