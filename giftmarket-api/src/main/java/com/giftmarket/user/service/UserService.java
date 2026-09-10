package com.giftmarket.user.service;

import com.giftmarket.auth.dto.LoginUserResponse;
import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.user.dto.UpdateMyProfileRequest;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserStatus;
import com.giftmarket.user.exception.UserException;
import com.giftmarket.user.repository.UserRepository;
import com.giftmarket.address.repository.AddressRepository;
import com.giftmarket.auth.repository.RefreshTokenRepository;
import com.giftmarket.cart.repository.CartItemRepository;
import com.giftmarket.order.entity.ExchangeRequestStatus;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.order.entity.ReturnRequestStatus;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.repository.ExchangeRequestRepository;
import com.giftmarket.order.repository.OrderCancellationRepository;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.order.repository.ReturnRequestRepository;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.wishlist.repository.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.giftmarket.global.storage.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final StorageService storageService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AddressRepository addressRepository;
    private final CartItemRepository cartItemRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final OrderRepository orderRepository;
    private final OrderCancellationRepository orderCancellationRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ExchangeRequestRepository exchangeRequestRepository;
    private final SellerRepository sellerRepository;
    private final SellerOrderRepository sellerOrderRepository;

    private static final Set<OrderStatus> ACTIVE_ORDER_STATUSES = Set.of(
            OrderStatus.PENDING_PAYMENT, OrderStatus.ORDERED, OrderStatus.PAID
    );
    private static final Set<OrderCancellationStatus> ACTIVE_CANCELLATION_STATUSES = Set.of(
            OrderCancellationStatus.REQUESTED, OrderCancellationStatus.PROCESSING
    );
    private static final Set<ReturnRequestStatus> ACTIVE_RETURN_STATUSES = Set.of(
            ReturnRequestStatus.REQUESTED, ReturnRequestStatus.APPROVED,
            ReturnRequestStatus.COLLECTING, ReturnRequestStatus.RECEIVED,
            ReturnRequestStatus.INSPECTED, ReturnRequestStatus.REFUNDING
    );
    private static final Set<ExchangeRequestStatus> ACTIVE_EXCHANGE_STATUSES = Set.of(
            ExchangeRequestStatus.REQUESTED, ExchangeRequestStatus.APPROVED,
            ExchangeRequestStatus.PAYMENT_PENDING, ExchangeRequestStatus.COLLECTING,
            ExchangeRequestStatus.RECEIVED, ExchangeRequestStatus.INSPECTED,
            ExchangeRequestStatus.RESHIPPING
    );
    private static final Set<SellerOrderStatus> ACTIVE_SELLER_ORDER_STATUSES = Set.of(
            SellerOrderStatus.PAID, SellerOrderStatus.PREPARING, SellerOrderStatus.SHIPPED
    );

    @Transactional
    public LoginUserResponse updateMyProfile(
            Long userId,
            UpdateMyProfileRequest request
    ) {
        if (userId == null) {
            throw new AuthenticationException(
                    "인증이 필요합니다."
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException(
                        "사용자를 찾을 수 없습니다."
                ));

        user.updateName(request.trimmedName());

        String newProfileImageKey =
                request.trimmedProfileImageUrl();

        if (newProfileImageKey != null) {
            validateOwnedProfileImageKey(userId, newProfileImageKey);

            String previousProfileImageKey =
                    user.getProfileImageUrl();

            user.updateProfileImage(newProfileImageKey);

            if (!newProfileImageKey.equals(previousProfileImageKey)) {
                registerProfileImageCleanup(
                        userId,
                        previousProfileImageKey,
                        newProfileImageKey
                );
            }
        }

        return LoginUserResponse.from(user);
    }

    @Transactional
    public void withdraw(Long userId) {
        if (userId == null) {
            throw new AuthenticationException("인증이 필요합니다.");
        }

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AuthenticationException("사용자를 찾을 수 없습니다."));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UserException("현재 탈퇴할 수 없는 회원 상태입니다.");
        }
        if (orderRepository.existsByUserIdAndStatusIn(userId, ACTIVE_ORDER_STATUSES)
                || orderCancellationRepository.existsByOrderUserIdAndStatusIn(userId, ACTIVE_CANCELLATION_STATUSES)
                || returnRequestRepository.existsByOrderUserIdAndStatusIn(userId, ACTIVE_RETURN_STATUSES)
                || exchangeRequestRepository.existsByOrderUserIdAndStatusIn(userId, ACTIVE_EXCHANGE_STATUSES)) {
            throw new UserException("진행 중인 주문 또는 클레임이 있어 탈퇴할 수 없습니다.");
        }

        Seller seller = sellerRepository.findByUserIdForUpdate(userId).orElse(null);
        if (seller != null) {
            Long sellerId = seller.getId();
            if (sellerOrderRepository.existsBySellerIdAndStatusIn(sellerId, ACTIVE_SELLER_ORDER_STATUSES)
                    || orderCancellationRepository.existsBySellerIdAndStatusIn(sellerId, ACTIVE_CANCELLATION_STATUSES)
                    || returnRequestRepository.existsBySellerIdAndStatusIn(sellerId, ACTIVE_RETURN_STATUSES)
                    || exchangeRequestRepository.existsBySellerIdAndStatusIn(sellerId, ACTIVE_EXCHANGE_STATUSES)) {
                throw new UserException("처리해야 할 판매 주문 또는 클레임이 있어 탈퇴할 수 없습니다.");
            }
            seller.withdraw();
        }

        String previousProfileImageKey = user.getProfileImageUrl();
        user.withdrawAndAnonymize("withdrawn-" + userId + "-" + UUID.randomUUID());
        refreshTokenRepository.deleteByUserId(userId);
        addressRepository.deleteAllByUserId(userId);
        cartItemRepository.deleteAllByUserId(userId);
        wishlistItemRepository.deleteAllByUserId(userId);
        registerWithdrawnProfileImageCleanup(userId, previousProfileImageKey);
    }

    private void registerWithdrawnProfileImageCleanup(Long userId, String profileImageKey) {
        if (!isOwnedProfileImageKey(userId, profileImageKey)) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    deleteManagedProfileObject(userId, profileImageKey);
                }
            }
        });
    }

    private void registerProfileImageCleanup(
            Long userId,
            String previousProfileImageKey,
            String newProfileImageKey
    ) {
        log.info(
                "프로필 이미지 정리 등록. previous={}, new={}",
                previousProfileImageKey,
                newProfileImageKey
        );

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCompletion(int status) {
                        log.info(
                                "프로필 이미지 트랜잭션 완료. status={}, previous={}, new={}",
                                status,
                                previousProfileImageKey,
                                newProfileImageKey
                        );

                        if (status == STATUS_COMMITTED) {
                            log.info(
                                    "DB 커밋 성공: 기존 프로필 이미지 삭제. objectKey={}",
                                    previousProfileImageKey
                            );

                            deleteManagedProfileObject(
                                    userId,
                                    previousProfileImageKey
                            );
                            return;
                        }

                        log.info(
                                "DB 롤백: 새 프로필 이미지 삭제. objectKey={}",
                                newProfileImageKey
                        );

                        deleteManagedProfileObject(
                                userId,
                                newProfileImageKey
                        );
                    }
                }
        );
    }

    private void deleteManagedProfileObject(
            Long userId,
            String objectKey
    ) {
        if (!isOwnedProfileImageKey(userId, objectKey)) {
            return;
        }

        try {
            storageService.deleteObject(objectKey);
        } catch (Exception exception) {
            // DB 트랜잭션 결과에는 영향을 주지 않고 로그만 남긴다.
            log.error(
                    "프로필 이미지 삭제 실패. objectKey={}",
                    objectKey,
                    exception
            );
        }
    }

    private void validateOwnedProfileImageKey(
            Long userId,
            String objectKey
    ) {
        if (!isOwnedProfileImageKey(userId, objectKey)) {
            throw new IllegalArgumentException(
                    "본인이 업로드한 프로필 이미지만 사용할 수 있습니다."
            );
        }
    }

    private boolean isOwnedProfileImageKey(
            Long userId,
            String objectKey
    ) {
        if (userId == null || objectKey == null || objectKey.isBlank()) {
            return false;
        }

        String expectedPrefix = "profiles/" + userId + "/";

        if (!objectKey.startsWith(expectedPrefix)) {
            return false;
        }

        String fileName = objectKey.substring(expectedPrefix.length());

        return !fileName.isBlank()
                && !fileName.contains("/")
                && !fileName.contains("\\")
                && !fileName.contains("..");
    }
}
