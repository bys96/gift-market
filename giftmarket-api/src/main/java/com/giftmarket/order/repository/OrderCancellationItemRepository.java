package com.giftmarket.order.repository;

import com.giftmarket.order.entity.OrderCancellationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderCancellationItemRepository extends JpaRepository<OrderCancellationItem, Long> {

    List<OrderCancellationItem> findAllByOrderCancellationIdOrderByIdAsc(Long orderCancellationId);
}
