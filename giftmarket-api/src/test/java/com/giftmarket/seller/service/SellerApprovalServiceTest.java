package com.giftmarket.seller.service;

import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerApplication;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.exception.SellerException;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SellerApprovalServiceTest {

    @Mock SellerRepository sellerRepository;

    @Test
    void approvesUserAndCreatesActiveSeller() {
        User applicant = org.mockito.Mockito.mock(User.class);
        User reviewer = org.mockito.Mockito.mock(User.class);
        SellerApplication application = SellerApplication.create(applicant, "상점", "소개");
        given(applicant.getRole()).willReturn(UserRole.USER);
        given(reviewer.getId()).willReturn(9L);
        given(sellerRepository.existsByUser(applicant)).willReturn(false);
        given(sellerRepository.save(org.mockito.ArgumentMatchers.any(Seller.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        new SellerApprovalService(sellerRepository).approve(application, reviewer);

        assertThat(application.getStatus().name()).isEqualTo("APPROVED");
        assertThat(application.getReviewedBy()).isEqualTo(9L);
        assertThat(application.getReviewedAt()).isNotNull();
        verify(applicant).changeRole(UserRole.SELLER);
        ArgumentCaptor<Seller> seller = ArgumentCaptor.forClass(Seller.class);
        verify(sellerRepository).save(seller.capture());
        assertThat(seller.getValue().getStatus()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(seller.getValue().getUser()).isSameAs(applicant);
    }

    @Test
    void keepsAdminRoleWhenSelfApprovalCreatesSeller() {
        User admin = org.mockito.Mockito.mock(User.class);
        SellerApplication application = SellerApplication.create(admin, "관리자 상점", null);
        given(admin.getRole()).willReturn(UserRole.ADMIN);
        given(admin.getId()).willReturn(1L);
        given(sellerRepository.existsByUser(admin)).willReturn(false);

        new SellerApprovalService(sellerRepository).approve(application, admin);

        assertThat(application.getStatus().name()).isEqualTo("APPROVED");
        assertThat(application.getReviewedBy()).isEqualTo(1L);
        verify(admin, never()).changeRole(org.mockito.ArgumentMatchers.any());
        verify(sellerRepository).save(org.mockito.ArgumentMatchers.any(Seller.class));
    }

    @Test
    void preventsDuplicateSellerCreationBeforeApproval() {
        User applicant = org.mockito.Mockito.mock(User.class);
        User reviewer = org.mockito.Mockito.mock(User.class);
        SellerApplication application = SellerApplication.create(applicant, "상점", null);
        given(sellerRepository.existsByUser(applicant)).willReturn(true);

        assertThatThrownBy(() -> new SellerApprovalService(sellerRepository)
                .approve(application, reviewer))
                .isInstanceOf(SellerException.class)
                .hasMessage("이미 등록된 판매자입니다.");

        assertThat(application.getStatus().name()).isEqualTo("PENDING");
        verify(sellerRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
