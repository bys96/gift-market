package com.giftmarket.seller.service;

import com.giftmarket.seller.dto.request.SellerApplicationCreateRequest;
import com.giftmarket.seller.entity.SellerApplication;
import com.giftmarket.seller.entity.SellerApplicationStatus;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.exception.SellerException;
import com.giftmarket.seller.repository.SellerApplicationRepository;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SellerServiceTest {

    @Mock SellerApplicationRepository applicationRepository;
    @Mock SellerRepository sellerRepository;
    @Mock UserRepository userRepository;
    @Mock SellerApprovalService approvalService;
    @Mock User user;
    private SellerService service;

    @BeforeEach
    void setUp() {
        service = new SellerService(
                applicationRepository,
                sellerRepository,
                userRepository,
                approvalService
        );
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        lenient().when(sellerRepository.existsByUser(user)).thenReturn(false);
        lenient().when(applicationRepository.existsByUserAndStatus(
                user, SellerApplicationStatus.PENDING
        )).thenReturn(false);
        lenient().when(applicationRepository.save(any(SellerApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void normalUserApplicationRemainsPending() {
        given(user.getRole()).willReturn(UserRole.USER);

        var response = service.apply(1L, request());

        assertThat(response.status()).isEqualTo(SellerApplicationStatus.PENDING);
        verify(approvalService, never()).approve(any(), any());
    }

    @Test
    void adminApplicationIsApprovedImmediatelyFromBackendResult() {
        given(user.getRole()).willReturn(UserRole.ADMIN);
        given(user.getId()).willReturn(1L);
        doAnswer(invocation -> {
            SellerApplication application = invocation.getArgument(0);
            application.approve(1L);
            return null;
        }).when(approvalService).approve(any(SellerApplication.class), any(User.class));

        var response = service.apply(1L, request());

        assertThat(response.status()).isEqualTo(SellerApplicationStatus.APPROVED);
        assertThat(response.reviewedAt()).isNotNull();
        verify(approvalService).approve(any(SellerApplication.class), org.mockito.ArgumentMatchers.same(user));
    }

    @Test
    void rejectsDuplicatePendingApplicationAndExistingSeller() {
        given(user.getRole()).willReturn(UserRole.USER);
        given(sellerRepository.existsByUser(user)).willReturn(true);
        assertThatThrownBy(() -> service.apply(1L, request()))
                .isInstanceOf(SellerException.class).hasMessage("이미 등록된 판매자입니다.");

        given(sellerRepository.existsByUser(user)).willReturn(false);
        given(applicationRepository.existsByUserAndStatus(user, SellerApplicationStatus.PENDING))
                .willReturn(true);
        assertThatThrownBy(() -> service.apply(1L, request()))
                .isInstanceOf(SellerException.class).hasMessage("이미 심사 중인 판매자 신청이 있습니다.");
    }

    @Test
    void propagatesAutoApprovalFailureSoTransactionalApplyCanRollBack() {
        given(user.getRole()).willReturn(UserRole.ADMIN);
        given(approvalService.approve(any(), any())).willThrow(new IllegalStateException("save failed"));

        assertThatThrownBy(() -> service.apply(1L, request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("save failed");
    }

    @Test
    void sellerLookupReturnsExistingInactiveRowAndNullWhenUnregistered() {
        com.giftmarket.seller.entity.Seller seller = org.mockito.Mockito.mock(com.giftmarket.seller.entity.Seller.class);
        given(sellerRepository.findByUserId(1L)).willReturn(Optional.of(seller));
        given(seller.getStatus()).willReturn(SellerStatus.SUSPENDED);
        assertThat(service.getMySeller(1L).getStatus()).isEqualTo(SellerStatus.SUSPENDED);

        given(sellerRepository.findByUserId(1L)).willReturn(Optional.empty());
        assertThat(service.getMySeller(1L)).isNull();
    }

    private SellerApplicationCreateRequest request() {
        return new SellerApplicationCreateRequest(" 상점 ", " 소개 ");
    }
}
