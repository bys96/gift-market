package com.giftmarket.admin.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.seller.dto.request.SellerApplicationRejectRequest;
import com.giftmarket.seller.dto.response.SellerApplicationResponse;
import com.giftmarket.seller.dto.response.SellerApplicationPageResponse;
import com.giftmarket.seller.entity.SellerApplication;
import com.giftmarket.seller.entity.SellerApplicationStatus;
import com.giftmarket.seller.exception.SellerException;
import com.giftmarket.seller.repository.SellerApplicationRepository;
import com.giftmarket.seller.service.SellerApprovalService;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
@RequiredArgsConstructor
public class AdminSellerService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SellerApplicationRepository sellerApplicationRepository;
    private final UserRepository userRepository;
    private final SellerApprovalService sellerApprovalService;

    @Transactional(readOnly = true)
    public SellerApplicationPageResponse getPendingApplications(
            Long adminUserId,
            int page,
            int size
    ) {
        getAdmin(adminUserId);

        if (page < 0) {
            throw new SellerException("페이지 번호는 0 이상이어야 합니다.");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new SellerException("페이지 크기는 1 이상 100 이하이어야 합니다.");
        }

        var pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        return SellerApplicationPageResponse.from(
                sellerApplicationRepository
                        .findAllByStatus(
                                SellerApplicationStatus.PENDING,
                                pageable
                        )
                        .map(SellerApplicationResponse::from)
        );
    }

    @Transactional
    public SellerApplicationResponse approve(
            Long adminUserId,
            Long applicationId
    ) {
        User admin = getAdmin(adminUserId);

        SellerApplication application =
                getPendingApplication(applicationId);

        sellerApprovalService.approve(application, admin);

        return SellerApplicationResponse.from(application);
    }

    @Transactional
    public SellerApplicationResponse reject(
            Long adminUserId,
            Long applicationId,
            SellerApplicationRejectRequest request
    ) {
        User admin = getAdmin(adminUserId);

        SellerApplication application =
                getPendingApplication(applicationId);

        application.reject(
                admin.getId(),
                request.trimmedRejectionReason()
        );

        return SellerApplicationResponse.from(application);
    }

    private User getAdmin(Long adminUserId) {
        if (adminUserId == null) {
            throw new AuthenticationException(
                    "인증이 필요합니다."
            );
        }

        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new AuthenticationException(
                        "사용자를 찾을 수 없습니다."
                ));

        if (admin.getRole() != UserRole.ADMIN) {
            throw new AuthenticationException(
                    "관리자 권한이 필요합니다."
            );
        }

        return admin;
    }

    private SellerApplication getPendingApplication(
            Long applicationId
    ) {
        SellerApplication application =
                sellerApplicationRepository
                        .findByIdForUpdate(applicationId)
                        .orElseThrow(() -> new SellerException(
                                "판매자 신청을 찾을 수 없습니다."
                        ));

        if (application.getStatus()
                != SellerApplicationStatus.PENDING) {
            throw new SellerException(
                    "이미 처리된 판매자 신청입니다."
            );
        }

        return application;
    }
}
