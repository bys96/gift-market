package com.giftmarket.notification.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
    ORDER_SHIPPED(NotificationContext.BUYER),
    ORDER_CANCELLED_BY_SELLER(NotificationContext.BUYER),
    CANCELLATION_COMPLETED(NotificationContext.BUYER),
    RETURN_APPROVED(NotificationContext.BUYER),
    RETURN_REJECTED(NotificationContext.BUYER),
    RETURN_COMPLETED(NotificationContext.BUYER),
    EXCHANGE_APPROVED(NotificationContext.BUYER),
    EXCHANGE_REJECTED(NotificationContext.BUYER),
    EXCHANGE_RESHIPPED(NotificationContext.BUYER),
    EXCHANGE_COMPLETED(NotificationContext.BUYER),
    PRODUCT_INQUIRY_ANSWERED(NotificationContext.BUYER),

    NEW_ORDER(NotificationContext.SELLER),
    CANCELLATION_REQUESTED(NotificationContext.SELLER),
    RETURN_REQUESTED(NotificationContext.SELLER),
    EXCHANGE_REQUESTED(NotificationContext.SELLER),
    PRODUCT_INQUIRY_CREATED(NotificationContext.SELLER),

    SELLER_APPLICATION_CREATED(NotificationContext.ADMIN);

    private final NotificationContext context;
}
