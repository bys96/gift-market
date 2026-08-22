package com.giftmarket.order.repository;

import com.giftmarket.order.entity.ExchangeRequestImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExchangeRequestImageRepository extends JpaRepository<ExchangeRequestImage, Long> {
    List<ExchangeRequestImage> findAllByExchangeRequestIdOrderBySortOrderAsc(Long exchangeRequestId);
}
