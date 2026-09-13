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
