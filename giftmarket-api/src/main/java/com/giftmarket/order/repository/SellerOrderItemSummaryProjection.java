package com.giftmarket.order.repository;

public interface SellerOrderItemSummaryProjection {

    Long getSellerOrderId();

    String getRepresentativeProductName();

    Long getProductTypeCount();

    Long getTotalQuantity();

    Long getTotalProductAmount();
}
