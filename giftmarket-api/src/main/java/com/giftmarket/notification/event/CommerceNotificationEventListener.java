package com.giftmarket.notification.event;

import com.giftmarket.notification.entity.NotificationContext;
import com.giftmarket.notification.entity.NotificationType;
import com.giftmarket.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommerceNotificationEventListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(NewOrderCreatedEvent event) {
        createSafely(
                event.sellerUserId(),
                NotificationContext.SELLER,
                NotificationType.NEW_ORDER,
                "새 주문이 접수되었습니다.",
                "주문번호 " + event.orderNumber() + " 주문이 접수되었습니다.",
                "/seller/orders/" + event.sellerOrderId(),
                "newOrderCreated",
                event.sellerOrderId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderShippedEvent event) {
        createSafely(
                event.buyerUserId(),
                NotificationContext.BUYER,
                NotificationType.ORDER_SHIPPED,
                "상품이 발송되었습니다.",
                event.storeName() + " 상품이 발송되었습니다.",
                "/my/orders/" + event.orderId(),
                "orderShipped",
                event.sellerOrderId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(CancellationRequestedEvent event) {
        createSafely(
                event.sellerUserId(),
                NotificationContext.SELLER,
                NotificationType.CANCELLATION_REQUESTED,
                "새 취소 요청이 접수되었습니다.",
                "주문번호 " + event.orderNumber() + "의 취소 요청을 확인해주세요.",
                "/seller/orders/cancellations/" + event.cancellationId(),
                "cancellationRequested",
                event.cancellationId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(BuyerCancellationCompletedEvent event) {
        createSafely(
                event.buyerUserId(),
                NotificationContext.BUYER,
                NotificationType.CANCELLATION_COMPLETED,
                "주문 취소가 완료되었습니다.",
                "요청하신 주문 취소와 환불이 완료되었습니다.",
                "/my/orders/" + event.orderId(),
                "buyerCancellationCompleted",
                event.cancellationId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(SellerOrderCancelledEvent event) {
        createSafely(
                event.buyerUserId(),
                NotificationContext.BUYER,
                NotificationType.ORDER_CANCELLED_BY_SELLER,
                "판매자가 주문을 취소했습니다.",
                "판매자 취소로 주문 환불이 완료되었습니다.",
                "/my/orders/" + event.orderId(),
                "sellerOrderCancelled",
                event.cancellationId()
        );
    }

    private void createSafely(
            Long userId,
            NotificationContext context,
            NotificationType type,
            String title,
            String message,
            String targetUrl,
            String eventName,
            Long sourceId
    ) {
        try {
            notificationService.create(
                    userId,
                    context,
                    type,
                    title,
                    message,
                    targetUrl
            );
        } catch (Exception exception) {
            log.error(
                    "알림 생성에 실패했습니다. event={}, sourceId={}, recipientUserId={}",
                    eventName,
                    sourceId,
                    userId,
                    exception
            );
        }
    }
}
