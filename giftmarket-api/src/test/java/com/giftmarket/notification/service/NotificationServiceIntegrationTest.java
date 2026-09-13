package com.giftmarket.notification.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.notification.entity.Notification;
import com.giftmarket.notification.entity.NotificationContext;
import com.giftmarket.notification.entity.NotificationType;
import com.giftmarket.notification.exception.NotificationException;
import com.giftmarket.notification.repository.NotificationRepository;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false, properties = {
        "spring.config.location=optional:classpath:/notification-test.properties",
        "spring.datasource.url=jdbc:h2:mem:notification;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(NotificationService.class)
class NotificationServiceIntegrationTest {

    @Autowired NotificationService notificationService;
    @Autowired NotificationRepository notificationRepository;
    @Autowired UserRepository userRepository;
    @Autowired SellerRepository sellerRepository;
    @Autowired EntityManager entityManager;

    private User buyer;
    private User otherBuyer;
    private User sellerUser;
    private User admin;

    @BeforeEach
    void setUp() {
        buyer = saveUser("buyer");
        otherBuyer = saveUser("other");
        sellerUser = saveUser("seller");
        sellerRepository.save(Seller.create(sellerUser, "알림 상점", "테스트"));
        admin = saveUser("admin");
        admin.changeRole(UserRole.ADMIN);
        entityManager.flush();
    }

    @Test
    void returnsOnlyRequestedOwnerAndContextInNewestOrder() {
        Notification olderBuyer = saveNotification(
                buyer,
                NotificationContext.BUYER,
                NotificationType.ORDER_SHIPPED,
                "이전 알림"
        );
        Notification newerBuyer = saveNotification(
                buyer,
                NotificationContext.BUYER,
                NotificationType.RETURN_APPROVED,
                "최신 알림"
        );
        saveNotification(
                otherBuyer,
                NotificationContext.BUYER,
                NotificationType.EXCHANGE_APPROVED,
                "다른 사용자"
        );
        Notification sellerNotification = saveNotification(
                sellerUser,
                NotificationContext.SELLER,
                NotificationType.NEW_ORDER,
                "판매자 알림"
        );
        Notification adminNotification = saveNotification(
                admin,
                NotificationContext.ADMIN,
                NotificationType.SELLER_APPLICATION_CREATED,
                "관리자 알림"
        );

        assertThat(notificationService.getNotifications(
                buyer.getId(), NotificationContext.BUYER, 0, 20
        ).content()).extracting("id")
                .containsExactly(newerBuyer.getId(), olderBuyer.getId());
        assertThat(notificationService.getNotifications(
                sellerUser.getId(), NotificationContext.SELLER, 0, 20
        ).content()).extracting("id")
                .containsExactly(sellerNotification.getId());
        assertThat(notificationService.getNotifications(
                admin.getId(), NotificationContext.ADMIN, 0, 20
        ).content()).extracting("id")
                .containsExactly(adminNotification.getId());
    }

    @Test
    void countsUnreadNotificationsOnlyWithinOwnerAndContext() {
        saveNotification(
                buyer,
                NotificationContext.BUYER,
                NotificationType.ORDER_SHIPPED,
                "안 읽은 알림"
        );
        Notification read = Notification.create(
                buyer,
                NotificationContext.BUYER,
                NotificationType.CANCELLATION_COMPLETED,
                "읽은 알림",
                "알림 내용",
                null
        );
        read.markAsRead();
        notificationRepository.save(read);
        saveNotification(
                buyer,
                NotificationContext.SELLER,
                NotificationType.NEW_ORDER,
                "다른 context"
        );

        assertThat(notificationService.getUnreadCount(
                buyer.getId(), NotificationContext.BUYER
        ).unreadCount()).isEqualTo(1L);
    }

    @Test
    void marksOneAsReadAndRepeatedRequestKeepsOriginalReadTime() {
        Notification notification = saveNotification(
                buyer,
                NotificationContext.BUYER,
                NotificationType.ORDER_SHIPPED,
                "개별 읽음"
        );

        notificationService.markAsRead(
                buyer.getId(), NotificationContext.BUYER, notification.getId()
        );
        entityManager.flush();
        var firstReadAt = notification.getReadAt();

        notificationService.markAsRead(
                buyer.getId(), NotificationContext.BUYER, notification.getId()
        );
        entityManager.flush();

        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isEqualTo(firstReadAt);
    }

    @Test
    void marksAllUnreadNotificationsOnlyWithinOwnerAndContext() {
        saveNotification(
                buyer,
                NotificationContext.BUYER,
                NotificationType.ORDER_SHIPPED,
                "전체 읽음 1"
        );
        saveNotification(
                buyer,
                NotificationContext.BUYER,
                NotificationType.RETURN_COMPLETED,
                "전체 읽음 2"
        );
        saveNotification(
                buyer,
                NotificationContext.SELLER,
                NotificationType.NEW_ORDER,
                "유지할 알림"
        );

        assertThat(notificationService.markAllAsRead(
                buyer.getId(), NotificationContext.BUYER
        )).isEqualTo(2);
        assertThat(notificationService.getUnreadCount(
                buyer.getId(), NotificationContext.BUYER
        ).unreadCount()).isZero();
        assertThat(notificationRepository
                .countByUserIdAndContextAndReadAtIsNull(
                        buyer.getId(), NotificationContext.SELLER
                )).isEqualTo(1L);
    }

    @Test
    void cannotReadAnotherUsersOrAnotherContextsNotification() {
        Notification otherUsers = saveNotification(
                otherBuyer,
                NotificationContext.BUYER,
                NotificationType.ORDER_SHIPPED,
                "다른 사용자"
        );
        Notification anotherContext = saveNotification(
                buyer,
                NotificationContext.SELLER,
                NotificationType.NEW_ORDER,
                "다른 context"
        );

        assertThatThrownBy(() -> notificationService.markAsRead(
                buyer.getId(),
                NotificationContext.BUYER,
                otherUsers.getId()
        )).isInstanceOf(NotificationException.class);
        assertThatThrownBy(() -> notificationService.markAsRead(
                buyer.getId(),
                NotificationContext.BUYER,
                anotherContext.getId()
        )).isInstanceOf(NotificationException.class);
        assertThat(otherUsers.isRead()).isFalse();
        assertThat(anotherContext.isRead()).isFalse();
    }

    @Test
    void rejectsSellerContextWithoutActiveSellerAndAdminContextWithoutAdminRole() {
        User inactiveSellerUser = saveUser("inactive-seller");
        Seller inactiveSeller = Seller.create(
                inactiveSellerUser,
                "비활성 상점",
                "테스트"
        );
        inactiveSeller.suspend();
        sellerRepository.save(inactiveSeller);

        assertThatThrownBy(() -> notificationService.getNotifications(
                inactiveSellerUser.getId(),
                NotificationContext.SELLER,
                0,
                20
        )).isInstanceOf(NotificationException.class);
        assertThatThrownBy(() -> notificationService.getNotifications(
                buyer.getId(),
                NotificationContext.ADMIN,
                0,
                20
        )).isInstanceOf(AuthenticationException.class);
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

    private Notification saveNotification(
            User user,
            NotificationContext context,
            NotificationType type,
            String title
    ) {
        return notificationRepository.saveAndFlush(Notification.create(
                user,
                context,
                type,
                title,
                "알림 내용",
                "/notifications/target"
        ));
    }
}
