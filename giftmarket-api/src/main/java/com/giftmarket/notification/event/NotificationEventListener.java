package com.giftmarket.notification.event;

import com.giftmarket.notification.entity.NotificationContext;
import com.giftmarket.notification.entity.NotificationReferenceType;
import com.giftmarket.notification.entity.NotificationType;
import com.giftmarket.notification.service.NotificationService;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.entity.UserStatus;
import com.giftmarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ProductInquiryCreatedEvent event) {
        createSafely(
                event.sellerUserId(),
                NotificationContext.SELLER,
                NotificationType.PRODUCT_INQUIRY_CREATED,
                "새 상품 문의가 등록되었습니다.",
                event.productName() + "에 새로운 문의가 등록되었습니다.",
                "/seller/inquiries/" + event.inquiryId(),
                NotificationReferenceType.PRODUCT_INQUIRY,
                event.inquiryId(),
                "productInquiryCreated",
                event.inquiryId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ProductInquiryAnsweredEvent event) {
        createSafely(
                event.buyerUserId(),
                NotificationContext.BUYER,
                NotificationType.PRODUCT_INQUIRY_ANSWERED,
                "상품 문의에 답변이 등록되었습니다.",
                event.productName() + " 문의에 판매자 답변이 등록되었습니다.",
                "/products/" + event.productId() + "#product-inquiries",
                NotificationReferenceType.PRODUCT_INQUIRY,
                event.inquiryId(),
                "productInquiryAnswered",
                event.inquiryId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ProductInquiryAnswerUpdatedEvent event) {
        createSafely(
                event.buyerUserId(),
                NotificationContext.BUYER,
                NotificationType.PRODUCT_INQUIRY_ANSWER_UPDATED,
                "상품 문의 답변이 수정되었습니다",
                event.productName() + " 문의의 판매자 답변이 수정되었습니다.",
                "/products/" + event.productId() + "#product-inquiries",
                NotificationReferenceType.PRODUCT_INQUIRY,
                event.inquiryId(),
                "productInquiryAnswerUpdated",
                event.inquiryId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(SellerApplicationCreatedEvent event) {
        try {
            for (User admin : userRepository.findAllByRoleAndStatus(
                    UserRole.ADMIN,
                    UserStatus.ACTIVE
            )) {
                createSafely(
                        admin.getId(),
                        NotificationContext.ADMIN,
                        NotificationType.SELLER_APPLICATION_CREATED,
                        "새 판매자 신청이 접수되었습니다.",
                        "새로운 판매자 신청이 접수되었습니다.",
                        "/admin/seller-applications",
                        NotificationReferenceType.SELLER_APPLICATION,
                        event.applicationId(),
                        "sellerApplicationCreated",
                        event.applicationId()
                );
            }
        } catch (Exception exception) {
            log.error(
                    "판매자 신청 알림 수신자 조회에 실패했습니다. applicationId={}",
                    event.applicationId(),
                    exception
            );
        }
    }

    private void createSafely(
            Long userId,
            NotificationContext context,
            NotificationType type,
            String title,
            String message,
            String targetUrl,
            NotificationReferenceType referenceType,
            Long referenceId,
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
                    targetUrl,
                    referenceType,
                    referenceId
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
