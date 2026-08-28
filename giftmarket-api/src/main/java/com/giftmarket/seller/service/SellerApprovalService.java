package com.giftmarket.seller.service;

import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerApplication;
import com.giftmarket.seller.exception.SellerException;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SellerApprovalService {

    private final SellerRepository sellerRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public Seller approve(
            SellerApplication application,
            User reviewer
    ) {
        User applicant = application.getUser();

        if (sellerRepository.existsByUser(applicant)) {
            throw new SellerException("이미 등록된 판매자입니다.");
        }

        application.approve(reviewer.getId());

        if (applicant.getRole() == UserRole.USER) {
            applicant.changeRole(UserRole.SELLER);
        }

        return sellerRepository.save(
                Seller.create(
                        applicant,
                        application.getStoreName(),
                        application.getIntroduction()
                )
        );
    }
}
