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
public class ClaimNotificationEventListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ReturnRequestedEvent event) {
        createSafely(
                event.sellerUserId(), NotificationContext.SELLER,
                NotificationType.RETURN_REQUESTED,
                "새 반품 요청이 접수되었습니다.",
                "새로운 반품 요청을 확인해주세요.",
                "/seller/orders/returns/" + event.returnRequestId(),
                "returnRequested", event.returnRequestId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ReturnApprovedEvent event) {
        createBuyerNotification(
                event.buyerUserId(), NotificationType.RETURN_APPROVED,
                "반품 요청이 승인되었습니다.",
                "반품 요청이 승인되어 회수 절차가 진행됩니다.",
                event.orderId(), "returnApproved", event.returnRequestId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ReturnRejectedEvent event) {
        createBuyerNotification(
                event.buyerUserId(), NotificationType.RETURN_REJECTED,
                "반품 요청이 거절되었습니다.",
                "반품 요청 처리 결과를 주문 상세에서 확인해주세요.",
                event.orderId(), "returnRejected", event.returnRequestId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ReturnCompletedEvent event) {
        createBuyerNotification(
                event.buyerUserId(), NotificationType.RETURN_COMPLETED,
                "반품이 완료되었습니다.",
                "반품과 환불 처리가 완료되었습니다.",
                event.orderId(), "returnCompleted", event.returnRequestId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ExchangeRequestedEvent event) {
        createSafely(
                event.sellerUserId(), NotificationContext.SELLER,
                NotificationType.EXCHANGE_REQUESTED,
                "새 교환 요청이 접수되었습니다.",
                "새로운 교환 요청을 확인해주세요.",
                "/seller/orders/exchanges/" + event.exchangeRequestId(),
                "exchangeRequested", event.exchangeRequestId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ExchangeApprovedEvent event) {
        createBuyerNotification(
                event.buyerUserId(), NotificationType.EXCHANGE_APPROVED,
                "교환 요청이 승인되었습니다.",
                "교환 요청이 승인되어 후속 절차가 진행됩니다.",
                event.orderId(), "exchangeApproved", event.exchangeRequestId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ExchangeRejectedEvent event) {
        createBuyerNotification(
                event.buyerUserId(), NotificationType.EXCHANGE_REJECTED,
                "교환 요청이 거절되었습니다.",
                "교환 요청 처리 결과를 주문 상세에서 확인해주세요.",
                event.orderId(), "exchangeRejected", event.exchangeRequestId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ExchangeReshippedEvent event) {
        createBuyerNotification(
                event.buyerUserId(), NotificationType.EXCHANGE_RESHIPPED,
                "교환 상품이 재배송되었습니다.",
                "교환 상품 재배송이 시작되었습니다.",
                event.orderId(), "exchangeReshipped", event.exchangeRequestId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ExchangeCompletedEvent event) {
        createBuyerNotification(
                event.buyerUserId(), NotificationType.EXCHANGE_COMPLETED,
                "교환이 완료되었습니다.",
                "교환 상품 배송과 교환 처리가 완료되었습니다.",
                event.orderId(), "exchangeCompleted", event.exchangeRequestId()
        );
    }

    private void createBuyerNotification(
            Long userId,
            NotificationType type,
            String title,
            String message,
            Long orderId,
            String eventName,
            Long sourceId
    ) {
        createSafely(
                userId, NotificationContext.BUYER, type, title, message,
                "/my/orders/" + orderId, eventName, sourceId
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
                    userId, context, type, title, message, targetUrl
            );
        } catch (Exception exception) {
            log.error(
                    "알림 생성에 실패했습니다. event={}, sourceId={}, recipientUserId={}",
                    eventName, sourceId, userId, exception
            );
        }
    }
}
