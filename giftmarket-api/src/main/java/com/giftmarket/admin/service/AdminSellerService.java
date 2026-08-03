package com.giftmarket.admin.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.seller.dto.request.SellerApplicationRejectRequest;
import com.giftmarket.seller.dto.response.SellerApplicationResponse;
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

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminSellerService {

    private final SellerApplicationRepository sellerApplicationRepository;
    private final SellerRepository sellerRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<SellerApplicationResponse> getPendingApplications(
            Long adminUserId
    ) {
        getAdmin(adminUserId);

        return sellerApplicationRepository
                .findAllByStatusOrderByCreatedAtAsc(
                        SellerApplicationStatus.PENDING
                )
                .stream()
                .map(SellerApplicationResponse::from)
                .toList();
    }

    @Transactional
    public SellerApplicationResponse approve(
            Long adminUserId,
            Long applicationId
    ) {
        User admin = getAdmin(adminUserId);

        SellerApplication application =
                getPendingApplication(applicationId);

        User applicant = application.getUser();

        if (sellerRepository.existsByUser(applicant)) {
            throw new SellerException(
                    "이미 등록된 판매자입니다."
            );
        }

        application.approve(admin.getId());
        applicant.changeRole(UserRole.SELLER);

        Seller seller = Seller.create(
                applicant,
                application.getStoreName(),
                application.getIntroduction()
        );

        sellerRepository.save(seller);

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