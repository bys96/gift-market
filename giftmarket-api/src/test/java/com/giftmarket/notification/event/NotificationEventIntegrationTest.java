package com.giftmarket.notification.event;

import com.giftmarket.notification.entity.Notification;
import com.giftmarket.notification.entity.NotificationContext;
import com.giftmarket.notification.entity.NotificationType;
import com.giftmarket.notification.repository.NotificationRepository;
import com.giftmarket.notification.service.NotificationService;
import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.transaction.TestTransaction;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DataJpaTest(showSql = false, properties = {
        "spring.config.location=optional:classpath:/notification-test.properties",
        "spring.datasource.url=jdbc:h2:mem:notification-events;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        NotificationService.class,
        NotificationEventListener.class,
        CommerceNotificationEventListener.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class NotificationEventIntegrationTest {

    @Autowired ApplicationEventPublisher eventPublisher;
    @Autowired NotificationRepository notificationRepository;
    @Autowired UserRepository userRepository;
    @Autowired EntityManager entityManager;

    @Test
    void productInquiryCreatedNotificationIsSavedOnlyAfterCommit() {
        User sellerUser = saveUser("seller");
        entityManager.flush();

        eventPublisher.publishEvent(new ProductInquiryCreatedEvent(
                sellerUser.getId(),
                31L,
                "Birthday gift"
        ));

        assertThat(notificationRepository.countByUserIdAndContextAndReadAtIsNull(
                sellerUser.getId(),
                NotificationContext.SELLER
        )).isZero();

        commitTransaction();

        Notification notification = singleNotification(
                sellerUser.getId(),
                NotificationContext.SELLER
        );
        assertThat(notification.getType()).isEqualTo(NotificationType.PRODUCT_INQUIRY_CREATED);
        assertThat(notification.getTitle()).isEqualTo("새 상품 문의가 등록되었습니다.");
        assertThat(notification.getMessage()).isEqualTo("Birthday gift에 새로운 문의가 등록되었습니다.");
        assertThat(notification.getTargetUrl()).isEqualTo("/seller/inquiries/31");
    }

    @Test
    void productInquiryAnsweredNotificationTargetsBuyerProductInquirySection() {
        User buyer = saveUser("buyer");
        entityManager.flush();

        eventPublisher.publishEvent(new ProductInquiryAnsweredEvent(
                buyer.getId(),
                32L,
                12L,
                "Anniversary gift"
        ));
        commitTransaction();

        Notification notification = singleNotification(
                buyer.getId(),
                NotificationContext.BUYER
        );
        assertThat(notification.getType()).isEqualTo(NotificationType.PRODUCT_INQUIRY_ANSWERED);
        assertThat(notification.getTitle()).isEqualTo("상품 문의에 답변이 등록되었습니다.");
        assertThat(notification.getMessage()).isEqualTo("Anniversary gift 문의에 판매자 답변이 등록되었습니다.");
        assertThat(notification.getTargetUrl()).isEqualTo("/products/12#product-inquiries");
    }

    @Test
    void sellerApplicationNotificationIsCreatedForEveryActiveAdminOnly() {
        User firstAdmin = saveAdmin("admin-one");
        User secondAdmin = saveAdmin("admin-two");
        User nonAdmin = saveUser("buyer");
        User suspendedAdmin = saveAdmin("suspended-admin");
        suspendedAdmin.suspend();
        entityManager.flush();

        eventPublisher.publishEvent(new SellerApplicationCreatedEvent(41L));
        commitTransaction();

        assertAdminApplicationNotification(firstAdmin);
        assertAdminApplicationNotification(secondAdmin);
        assertThat(notificationRepository.countByUserIdAndContextAndReadAtIsNull(
                nonAdmin.getId(),
                NotificationContext.ADMIN
        )).isZero();
        assertThat(notificationRepository.countByUserIdAndContextAndReadAtIsNull(
                suspendedAdmin.getId(),
                NotificationContext.ADMIN
        )).isZero();
    }

    @Test
    void commerceEventsCreateNotificationsWithExpectedContextTypeAndTarget() {
        User seller = saveUser("commerce-seller");
        User buyer = saveUser("commerce-buyer");
        entityManager.flush();

        eventPublisher.publishEvent(new NewOrderCreatedEvent(
                seller.getId(),
                71L,
                "GM-ORDER-71"
        ));
        eventPublisher.publishEvent(new OrderShippedEvent(
                buyer.getId(),
                72L,
                73L,
                "선물 상점"
        ));
        commitTransaction();

        Notification sellerNotification = singleNotification(
                seller.getId(),
                NotificationContext.SELLER
        );
        assertThat(sellerNotification.getType()).isEqualTo(NotificationType.NEW_ORDER);
        assertThat(sellerNotification.getTargetUrl()).isEqualTo("/seller/orders/71");

        Notification buyerNotification = singleNotification(
                buyer.getId(),
                NotificationContext.BUYER
        );
        assertThat(buyerNotification.getType()).isEqualTo(NotificationType.ORDER_SHIPPED);
        assertThat(buyerNotification.getMessage()).isEqualTo("선물 상점 상품이 발송되었습니다.");
        assertThat(buyerNotification.getTargetUrl()).isEqualTo("/my/orders/72");
    }

    @Test
    void cancellationEventsUseExpectedRecipientTypeAndTarget() {
        User seller = saveUser("cancellation-seller");
        User buyer = saveUser("cancellation-buyer");
        entityManager.flush();

        eventPublisher.publishEvent(new CancellationRequestedEvent(
                seller.getId(),
                81L,
                "GM-ORDER-81"
        ));
        eventPublisher.publishEvent(new BuyerCancellationCompletedEvent(
                buyer.getId(),
                82L,
                83L
        ));
        eventPublisher.publishEvent(new SellerOrderCancelledEvent(
                buyer.getId(),
                84L,
                85L
        ));
        commitTransaction();

        Notification sellerNotification = singleNotification(
                seller.getId(),
                NotificationContext.SELLER
        );
        assertThat(sellerNotification.getType())
                .isEqualTo(NotificationType.CANCELLATION_REQUESTED);
        assertThat(sellerNotification.getTargetUrl())
                .isEqualTo("/seller/orders/cancellations/81");

        var buyerNotifications = notificationRepository.findAllByUserIdAndContext(
                buyer.getId(),
                NotificationContext.BUYER,
                PageRequest.of(0, 10)
        ).getContent();
        assertThat(buyerNotifications).extracting(Notification::getType)
                .containsExactlyInAnyOrder(
                        NotificationType.CANCELLATION_COMPLETED,
                        NotificationType.ORDER_CANCELLED_BY_SELLER
                );
        assertThat(buyerNotifications).extracting(Notification::getTargetUrl)
                .containsExactlyInAnyOrder("/my/orders/83", "/my/orders/85");
    }

    @Test
    void rolledBackTransactionDoesNotCreateNotification() {
        User sellerUser = saveUser("rollback-seller");
        entityManager.flush();
        Long sellerUserId = sellerUser.getId();

        eventPublisher.publishEvent(new NewOrderCreatedEvent(
                sellerUserId,
                51L,
                "Rollback gift"
        ));

        TestTransaction.flagForRollback();
        TestTransaction.end();

        assertThat(notificationRepository.countByUserIdAndContextAndReadAtIsNull(
                sellerUserId,
                NotificationContext.SELLER
        )).isZero();
    }

    @Test
    void notificationFailureDoesNotUndoCommittedBusinessData() {
        User committedUser = saveUser("committed-business-user");
        entityManager.flush();
        Long committedUserId = committedUser.getId();

        eventPublisher.publishEvent(new NewOrderCreatedEvent(
                Long.MAX_VALUE,
                61L,
                "Missing recipient gift"
        ));

        assertThatCode(this::commitTransaction).doesNotThrowAnyException();
        assertThat(userRepository.existsById(committedUserId)).isTrue();
        assertThat(notificationRepository.count()).isZero();
    }

    private void assertAdminApplicationNotification(User admin) {
        Notification notification = singleNotification(
                admin.getId(),
                NotificationContext.ADMIN
        );
        assertThat(notification.getType()).isEqualTo(NotificationType.SELLER_APPLICATION_CREATED);
        assertThat(notification.getTargetUrl()).isEqualTo("/admin/seller-applications");
    }

    private Notification singleNotification(
            Long userId,
            NotificationContext context
    ) {
        var notifications = notificationRepository.findAllByUserIdAndContext(
                userId,
                context,
                PageRequest.of(0, 10)
        );
        assertThat(notifications.getTotalElements()).isEqualTo(1L);
        return notifications.getContent().getFirst();
    }

    private void commitTransaction() {
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }

    private User saveAdmin(String prefix) {
        User admin = saveUser(prefix);
        admin.changeRole(UserRole.ADMIN);
        return admin;
    }

    private User saveUser(String prefix) {
        String unique = UUID.randomUUID().toString();
        return userRepository.save(User.createOAuthUser(
                prefix + "-" + unique + "@example.test",
                prefix,
                null,
                AuthProvider.GOOGLE,
                unique
        ));
    }
}
