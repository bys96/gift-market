package com.giftmarket.order.repository;

import com.giftmarket.order.entity.ExchangeRequestItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExchangeRequestItemRepository extends JpaRepository<ExchangeRequestItem, Long> {
    @EntityGraph(attributePaths = {"orderItem", "targetProduct", "targetVariant"})
    List<ExchangeRequestItem> findAllByExchangeRequestIdOrderByOrderItemIdAsc(Long exchangeRequestId);
}
