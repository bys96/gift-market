package com.giftmarket.admin.service;

import com.giftmarket.admin.dto.response.AdminUserDetailResponse;
import com.giftmarket.admin.dto.response.AdminUserPageResponse;
import com.giftmarket.admin.dto.response.AdminUserSummaryResponse;
import com.giftmarket.admin.exception.AdminUserException;
import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.inquiry.repository.ProductInquiryRepository;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.review.repository.ReviewRepository;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerApplication;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.repository.SellerApplicationRepository;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.entity.UserStatus;
import com.giftmarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final Sort USER_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );

    private final UserRepository userRepository;
    private final SellerRepository sellerRepository;
    private final SellerApplicationRepository sellerApplicationRepository;
    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;
    private final ProductInquiryRepository productInquiryRepository;

    @Transactional(readOnly = true)
    public AdminUserPageResponse getUsers(
            Long adminUserId,
            int page,
            int size,
            String keyword,
            UserRole role,
            AuthProvider provider,
            UserStatus status
    ) {
        getAdmin(adminUserId);

        String normalizedKeyword = normalizeKeyword(keyword);
        var userPage = userRepository.findAdminUsers(
                normalizedKeyword,
                role,
                provider,
                status,
                PageRequest.of(page, size, USER_SORT)
        );
        List<Long> userIds = userPage.getContent().stream().map(User::getId).toList();
        Set<Long> activeSellerUserIds = activeSellerUserIds(userIds);
        List<AdminUserSummaryResponse> content = userPage.getContent().stream()
                .map(user -> AdminUserSummaryResponse.from(
                        user,
                        activeSellerUserIds.contains(user.getId())
                ))
                .toList();

        return AdminUserPageResponse.from(userPage, content);
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUser(Long adminUserId, Long userId) {
        getAdmin(adminUserId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AdminUserException("회원을 찾을 수 없습니다."));
        Seller seller = sellerRepository.findByUserId(userId).orElse(null);
        SellerApplication application = sellerApplicationRepository
                .findFirstByUserIdOrderByCreatedAtDescIdDesc(userId)
                .orElse(null);

        return AdminUserDetailResponse.from(
                user,
                seller,
                application,
                orderRepository.countByUserId(userId),
                reviewRepository.countByUserIdAndDeletedAtIsNull(userId),
                productInquiryRepository.countByUserIdAndDeletedAtIsNull(userId)
        );
    }

    private Set<Long> activeSellerUserIds(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        return sellerRepository.findUserIdsByUserIdInAndStatus(userIds, SellerStatus.ACTIVE)
                .stream()
                .collect(Collectors.toSet());
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private User getAdmin(Long adminUserId) {
        if (adminUserId == null) {
            throw new AuthenticationException("인증이 필요합니다.");
        }

        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new AuthenticationException("사용자를 찾을 수 없습니다."));

        if (admin.getRole() != UserRole.ADMIN) {
            throw new AuthenticationException("관리자 권한이 필요합니다.");
        }

        return admin;
    }
}
