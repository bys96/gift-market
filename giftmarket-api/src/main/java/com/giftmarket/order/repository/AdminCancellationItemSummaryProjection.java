package com.giftmarket.order.repository;
public interface AdminCancellationItemSummaryProjection {
    Long getCancellationId();
    String getRepresentativeProductName();
    Long getProductTypeCount();
    Long getRequestedQuantity();
}
