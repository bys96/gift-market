package com.giftmarket.order.repository;

public interface AdminOrderItemSummaryProjection {
    Long getOrderId();
    String getRepresentativeProductName();
    Long getProductTypeCount();
    Long getTotalItemCount();
}
