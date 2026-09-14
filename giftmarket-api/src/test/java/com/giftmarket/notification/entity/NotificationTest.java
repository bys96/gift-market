package com.giftmarket.notification.entity;

import com.giftmarket.user.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class NotificationTest {

    private final User user = mock(User.class);

    @Test
    void marksAsReadIdempotently() {
        Notification notification = notification();

        notification.markAsRead();
        var firstReadAt = notification.getReadAt();
        notification.markAsRead();

        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isEqualTo(firstReadAt);
    }

    @Test
    void rejectsTypeFromDifferentContext() {
        assertThatThrownBy(() -> Notification.create(
                user,
                NotificationContext.SELLER,
                NotificationType.ORDER_SHIPPED,
                "배송 시작",
                "상품 배송이 시작됐습니다.",
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createsWithOptionalBusinessReference() {
        Notification notification = Notification.create(
                user,
                NotificationContext.ADMIN,
                NotificationType.SELLER_APPLICATION_CREATED,
                "판매자 신청",
                "새 판매자 신청",
                "/admin/seller-applications",
                NotificationReferenceType.SELLER_APPLICATION,
                42L
        );

        assertThat(notification.getReferenceType())
                .isEqualTo(NotificationReferenceType.SELLER_APPLICATION);
        assertThat(notification.getReferenceId()).isEqualTo(42L);
    }

    @Test
    void legacyCreateKeepsBusinessReferenceNull() {
        Notification notification = notification();

        assertThat(notification.getReferenceType()).isNull();
        assertThat(notification.getReferenceId()).isNull();
    }

    private Notification notification() {
        return Notification.create(
                user,
                NotificationContext.BUYER,
                NotificationType.ORDER_SHIPPED,
                "배송 시작",
                "상품 배송이 시작됐습니다.",
                "/my/orders/1"
        );
    }
}
