package com.giftmarket.notification.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.notification.dto.response.NotificationPageResponse;
import com.giftmarket.notification.dto.response.NotificationResponse;
import com.giftmarket.notification.dto.response.NotificationUnreadCountResponse;
import com.giftmarket.notification.entity.Notification;
import com.giftmarket.notification.entity.NotificationContext;
import com.giftmarket.notification.entity.NotificationType;
import com.giftmarket.notification.exception.NotificationException;
import com.giftmarket.notification.repository.NotificationRepository;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final Sort NOTIFICATION_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SellerRepository sellerRepository;

    public NotificationPageResponse getNotifications(
            Long userId,
            NotificationContext context,
            int page,
            int size
    ) {
        validateContextAccess(userId, context);

        return NotificationPageResponse.from(
                notificationRepository.findAllByUserIdAndContext(
                        userId,
                        context,
                        PageRequest.of(page, size, NOTIFICATION_SORT)
                ).map(NotificationResponse::from)
        );
    }

    public NotificationUnreadCountResponse getUnreadCount(
            Long userId,
            NotificationContext context
    ) {
        validateContextAccess(userId, context);

        return new NotificationUnreadCountResponse(
                notificationRepository.countByUserIdAndContextAndReadAtIsNull(
                        userId,
                        context
                )
        );
    }

    @Transactional
    public void markAsRead(
            Long userId,
            NotificationContext context,
            Long notificationId
    ) {
        validateContextAccess(userId, context);

        Notification notification = notificationRepository
                .findByIdAndUserIdAndContext(notificationId, userId, context)
                .orElseThrow(this::notificationNotFound);

        notification.markAsRead();
    }

    @Transactional
    public int markAllAsRead(Long userId, NotificationContext context) {
        validateContextAccess(userId, context);

        return notificationRepository.markAllAsRead(
                userId,
                context,
                LocalDateTime.now()
        );
    }

    @Transactional
    public Notification create(
            Long userId,
            NotificationContext context,
            NotificationType type,
            String title,
            String message,
            String targetUrl
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotificationException(
                        "알림 수신자를 찾을 수 없습니다."
                ));

        return notificationRepository.save(Notification.create(
                user,
                context,
                type,
                title,
                message,
                targetUrl
        ));
    }

    private void validateContextAccess(
            Long userId,
            NotificationContext context
    ) {
        if (userId == null) {
            throw new AuthenticationException("인증이 필요합니다.");
        }
        if (context == null) {
            throw new NotificationException("알림 context를 확인해주세요.");
        }

        switch (context) {
            case BUYER -> {
            }
            case SELLER -> validateActiveSeller(userId);
            case ADMIN -> validateAdmin(userId);
        }
    }

    private void validateActiveSeller(Long userId) {
        boolean activeSeller = sellerRepository.findByUserId(userId)
                .filter(seller -> seller.getStatus() == SellerStatus.ACTIVE)
                .isPresent();

        if (!activeSeller) {
            throw new NotificationException(
                    "활성 판매자 정보를 찾을 수 없습니다."
            );
        }
    }

    private void validateAdmin(Long userId) {
        boolean admin = userRepository.findById(userId)
                .filter(user -> user.getRole() == UserRole.ADMIN)
                .isPresent();

        if (!admin) {
            throw new AuthenticationException("관리자 권한이 필요합니다.");
        }
    }

    private NotificationException notificationNotFound() {
        return new NotificationException("알림을 찾을 수 없습니다.");
    }
}
