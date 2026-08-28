package com.giftmarket.seller.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.seller.dto.request.SellerApplicationCreateRequest;
import com.giftmarket.seller.dto.response.SellerApplicationResponse;
import com.giftmarket.seller.dto.response.SellerResponse;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerApplication;
import com.giftmarket.seller.entity.SellerApplicationStatus;
import com.giftmarket.seller.exception.SellerException;
import com.giftmarket.seller.repository.SellerApplicationRepository;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class SellerService {

    private final SellerApplicationRepository sellerApplicationRepository;
    private final SellerRepository sellerRepository;
    private final UserRepository userRepository;
    private final SellerApprovalService sellerApprovalService;

    @Transactional
    public SellerApplicationResponse apply(
            Long userId,
            SellerApplicationCreateRequest request
    ) {
        User user = getAuthenticatedUser(userId);

        if (user.getRole() != UserRole.USER
                && user.getRole() != UserRole.ADMIN) {
            throw new SellerException(
                    "판매자 등록 신청이 가능한 계정이 아닙니다."
            );
        }

        if (sellerRepository.existsByUser(user)) {
            throw new SellerException(
                    "이미 등록된 판매자입니다."
            );
        }

        boolean pendingApplicationExists =
                sellerApplicationRepository.existsByUserAndStatus(
                        user,
                        SellerApplicationStatus.PENDING
                );

        if (pendingApplicationExists) {
            throw new SellerException(
                    "이미 심사 중인 판매자 신청이 있습니다."
            );
        }

        SellerApplication application =
                SellerApplication.create(
                        user,
                        request.storeName().trim(),
                        trimToNull(request.introduction())
                );

        SellerApplication savedApplication =
                sellerApplicationRepository.save(application);

        if (user.getRole() == UserRole.ADMIN) {
            sellerApprovalService.approve(savedApplication, user);
        }

        return SellerApplicationResponse.from(savedApplication);
    }

    @Transactional(readOnly = true)
    public SellerApplicationResponse getMyLatestApplication(Long userId) {
        User user = getAuthenticatedUser(userId);

        return sellerApplicationRepository
                .findFirstByUserOrderByCreatedAtDesc(user)
                .map(SellerApplicationResponse::from)
                .orElse(null);
    }

    private User getAuthenticatedUser(Long userId) {
        if (userId == null) {
            throw new AuthenticationException(
                    "인증이 필요합니다."
            );
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException(
                        "사용자를 찾을 수 없습니다."
                ));
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    @Transactional(readOnly = true)
    public SellerResponse getMySeller(Long userId) {
        getAuthenticatedUser(userId);
        return sellerRepository.findByUserId(userId)
                .map(SellerResponse::from)
                .orElse(null);
    }
}
