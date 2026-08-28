package com.giftmarket.admin.service;

import com.giftmarket.seller.dto.request.SellerApplicationRejectRequest;
import com.giftmarket.seller.entity.SellerApplication;
import com.giftmarket.seller.entity.SellerApplicationStatus;
import com.giftmarket.seller.exception.SellerException;
import com.giftmarket.seller.repository.SellerApplicationRepository;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.seller.service.SellerApprovalService;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminSellerServiceTest {

    private static final Long ADMIN_ID = 1L;

    @Mock
    private SellerApplicationRepository applicationRepository;

    @Mock
    private SellerRepository sellerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SellerApprovalService sellerApprovalService;

    private AdminSellerService service;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new AdminSellerService(
                applicationRepository,
                userRepository,
                sellerApprovalService
        );
        admin = mock(User.class);
        given(userRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin));
        given(admin.getRole()).willReturn(UserRole.ADMIN);
    }

    @Test
    void pagesPendingApplicationsNewestFirstWithStableIdOrder() {
        SellerApplication application = application(101L);
        given(applicationRepository.findAllByStatus(
                eq(SellerApplicationStatus.PENDING),
                any(Pageable.class)
        )).willReturn(new PageImpl<>(
                List.of(application),
                PageRequest.of(1, 10),
                24
        ));

        var response = service.getPendingApplications(ADMIN_ID, 1, 10);

        assertThat(response.content()).hasSize(1);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(24);
        assertThat(response.totalPages()).isEqualTo(3);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(applicationRepository).findAllByStatus(
                eq(SellerApplicationStatus.PENDING),
                pageable.capture()
        );
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt"))
                .isNotNull()
                .extracting(org.springframework.data.domain.Sort.Order::isDescending)
                .isEqualTo(true);
        assertThat(pageable.getValue().getSort().getOrderFor("id"))
                .isNotNull()
                .extracting(org.springframework.data.domain.Sort.Order::isDescending)
                .isEqualTo(true);
    }

    @Test
    void rejectsInvalidAdminPageSize() {
        assertThatThrownBy(() -> service.getPendingApplications(ADMIN_ID, -1, 10))
                .isInstanceOf(SellerException.class)
                .hasMessage("페이지 번호는 0 이상이어야 합니다.");
        assertThatThrownBy(() -> service.getPendingApplications(ADMIN_ID, 0, 101))
                .isInstanceOf(SellerException.class)
                .hasMessage("페이지 크기는 1 이상 100 이하이어야 합니다.");

        verify(applicationRepository, never())
                .findAllByStatus(any(), any());
    }

    @Test
    void canReloadPendingPageAfterApprovalAndRejection() {
        given(admin.getId()).willReturn(ADMIN_ID);
        SellerApplication approved = application(201L);
        SellerApplication rejected = application(202L);
        given(applicationRepository.findByIdForUpdate(201L))
                .willReturn(Optional.of(approved));
        given(applicationRepository.findByIdForUpdate(202L))
                .willReturn(Optional.of(rejected));
        given(applicationRepository.findAllByStatus(
                eq(SellerApplicationStatus.PENDING),
                any(Pageable.class)
        )).willReturn(new PageImpl<>(List.of()));

        service.approve(ADMIN_ID, 201L);
        service.reject(
                ADMIN_ID,
                202L,
                new SellerApplicationRejectRequest(" 정보 보완 필요 ")
        );
        var refreshed = service.getPendingApplications(ADMIN_ID, 0, 10);

        assertThat(refreshed.content()).isEmpty();
        verify(sellerApprovalService).approve(approved, admin);
        verify(rejected).reject(ADMIN_ID, "정보 보완 필요");
        verify(applicationRepository).findAllByStatus(
                eq(SellerApplicationStatus.PENDING),
                any(Pageable.class)
        );
    }

    private SellerApplication application(Long id) {
        SellerApplication application = mock(SellerApplication.class);
        User applicant = mock(User.class);
        given(application.getId()).willReturn(id);
        given(application.getStatus()).willReturn(SellerApplicationStatus.PENDING);
        given(application.getUser()).willReturn(applicant);
        given(applicant.getId()).willReturn(id + 1_000);
        return application;
    }
}
