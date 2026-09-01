package com.giftmarket.admin.service;

import com.giftmarket.admin.entity.AdminActionLog;
import com.giftmarket.admin.entity.AdminActionType;
import com.giftmarket.admin.exception.AdminUserException;
import com.giftmarket.admin.exception.AdminUserOperationException;
import com.giftmarket.admin.repository.AdminActionLogRepository;
import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.auth.repository.RefreshTokenRepository;
import com.giftmarket.inquiry.repository.ProductInquiryRepository;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.review.repository.ReviewRepository;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerApplication;
import com.giftmarket.seller.entity.SellerApplicationStatus;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.repository.SellerApplicationRepository;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.user.entity.AuthProvider;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.entity.UserStatus;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long USER_ID = 10L;

    @Mock UserRepository userRepository;
    @Mock SellerRepository sellerRepository;
    @Mock SellerApplicationRepository sellerApplicationRepository;
    @Mock OrderRepository orderRepository;
    @Mock ReviewRepository reviewRepository;
    @Mock ProductInquiryRepository productInquiryRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock AdminActionLogRepository adminActionLogRepository;
    @Mock User admin;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(
                userRepository,
                sellerRepository,
                sellerApplicationRepository,
                orderRepository,
                reviewRepository,
                productInquiryRepository,
                refreshTokenRepository,
                adminActionLogRepository
        );
    }

    @Test
    void rejectsNullAdminId() {
        assertThatThrownBy(() -> service.getUsers(null, 0, 20, null, null, null, null))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("인증이 필요합니다.");
        verify(userRepository, never()).findAdminUsers(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsMissingAdmin() {
        given(userRepository.findById(ADMIN_ID)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.getUsers(ADMIN_ID, 0, 20, null, null, null, null))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("사용자를 찾을 수 없습니다.");
    }

    @Test
    void rejectsNonAdmin() {
        given(userRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin));
        given(admin.getRole()).willReturn(UserRole.USER);
        assertThatThrownBy(() -> service.getUsers(ADMIN_ID, 0, 20, null, null, null, null))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("관리자 권한이 필요합니다.");
    }

    @Test
    void passesTrimmedKeywordFiltersPaginationAndNewestSort() {
        givenAdmin();
        given(userRepository.findAdminUsers(any(), any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(2, 20), 0));

        service.getUsers(
                ADMIN_ID, 2, 20, "  gift@example.com  ",
                UserRole.USER, AuthProvider.GOOGLE, UserStatus.ACTIVE
        );

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAdminUsers(
                eq("gift@example.com"), eq(UserRole.USER), eq(AuthProvider.GOOGLE),
                eq(UserStatus.ACTIVE), pageable.capture()
        );
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
        assertThat(pageable.getValue().getSort().getOrderFor("id").isDescending()).isTrue();
    }

    @Test
    void mapsSummaryAndActiveSellerWithoutPerUserQueries() {
        givenAdmin();
        User first = summaryUser(10L, "첫 회원");
        User second = summaryUser(11L, "둘째 회원");
        given(userRepository.findAdminUsers(any(), any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(first, second)));
        given(sellerRepository.findUserIdsByUserIdInAndStatus(
                List.of(10L, 11L), SellerStatus.ACTIVE
        )).willReturn(List.of(11L));

        var response = service.getUsers(ADMIN_ID, 0, 20, "   ", null, null, null);

        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).activeSeller()).isFalse();
        assertThat(response.content().get(1).activeSeller()).isTrue();
        assertThat(response.content().get(1).name()).isEqualTo("둘째 회원");
        verify(userRepository).findAdminUsers(eq(null), eq(null), eq(null), eq(null), any());
    }

    @Test
    void throwsNotFoundForMissingDetailUser() {
        givenAdmin();
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUser(ADMIN_ID, USER_ID))
                .isInstanceOf(AdminUserException.class)
                .hasMessage("회원을 찾을 수 없습니다.");
        verify(orderRepository, never()).countByUserId(any());
    }

    @Test
    void returnsRegularUserDetailAndActivityCounts() {
        givenAdmin();
        User user = detailUser();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(sellerRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(sellerApplicationRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .willReturn(Optional.empty());
        given(orderRepository.countByUserId(USER_ID)).willReturn(3L);
        given(reviewRepository.countByUserIdAndDeletedAtIsNull(USER_ID)).willReturn(2L);
        given(productInquiryRepository.countByUserIdAndDeletedAtIsNull(USER_ID)).willReturn(1L);

        var response = service.getUser(ADMIN_ID, USER_ID);

        assertThat(response.id()).isEqualTo(USER_ID);
        assertThat(response.seller()).isNull();
        assertThat(response.latestSellerApplication()).isNull();
        assertThat(response.activity().orders()).isEqualTo(3L);
        assertThat(response.activity().reviews()).isEqualTo(2L);
        assertThat(response.activity().inquiries()).isEqualTo(1L);
    }

    @Test
    void returnsSellerAndLatestApplicationDetail() {
        givenAdmin();
        User user = detailUser();
        Seller seller = org.mockito.Mockito.mock(Seller.class);
        SellerApplication application = org.mockito.Mockito.mock(SellerApplication.class);
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 30, 9, 0);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(sellerRepository.findByUserId(USER_ID)).willReturn(Optional.of(seller));
        given(sellerApplicationRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .willReturn(Optional.of(application));
        given(seller.getId()).willReturn(30L);
        given(seller.getStoreName()).willReturn("선물 상점");
        given(seller.getStatus()).willReturn(SellerStatus.ACTIVE);
        given(seller.getCreatedAt()).willReturn(createdAt);
        given(application.getId()).willReturn(40L);
        given(application.getStoreName()).willReturn("선물 상점");
        given(application.getStatus()).willReturn(SellerApplicationStatus.APPROVED);
        given(application.getCreatedAt()).willReturn(createdAt);

        var response = service.getUser(ADMIN_ID, USER_ID);

        assertThat(response.seller().sellerId()).isEqualTo(30L);
        assertThat(response.seller().status()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(response.latestSellerApplication().applicationId()).isEqualTo(40L);
        assertThat(response.latestSellerApplication().status()).isEqualTo(SellerApplicationStatus.APPROVED);
    }

    @Test
    void suspendsActiveUserDeletesRefreshTokenAndWritesTrimmedLog() {
        givenAdmin();
        User user = statusChangeUser(UserRole.USER, UserStatus.ACTIVE);
        given(user.getId()).willReturn(USER_ID);
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));

        service.suspendUser(ADMIN_ID, USER_ID, "  운영 정책 위반  ");

        verify(user).suspend();
        verify(refreshTokenRepository).deleteByUserId(USER_ID);
        ArgumentCaptor<AdminActionLog> logCaptor = ArgumentCaptor.forClass(AdminActionLog.class);
        verify(adminActionLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getAdminUserId()).isEqualTo(ADMIN_ID);
        assertThat(logCaptor.getValue().getActionType()).isEqualTo(AdminActionType.USER_SUSPENDED);
        assertThat(logCaptor.getValue().getTargetId()).isEqualTo(USER_ID);
        assertThat(logCaptor.getValue().getReason()).isEqualTo("운영 정책 위반");
    }

    @Test
    void reactivatesSuspendedUserAndWritesLogWithoutIssuingRefreshToken() {
        givenAdmin();
        User user = statusChangeUser(UserRole.USER, UserStatus.SUSPENDED);
        given(user.getId()).willReturn(USER_ID);
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));

        service.reactivateUser(ADMIN_ID, USER_ID, "정지 해제 승인");

        verify(user).activate();
        verify(refreshTokenRepository, never()).deleteByUserId(any());
        ArgumentCaptor<AdminActionLog> logCaptor = ArgumentCaptor.forClass(AdminActionLog.class);
        verify(adminActionLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getActionType()).isEqualTo(AdminActionType.USER_REACTIVATED);
    }

    @Test
    void rejectsChangingOwnStatusWithoutLog() {
        givenAdmin();
        given(userRepository.findByIdForUpdate(ADMIN_ID)).willReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.suspendUser(ADMIN_ID, ADMIN_ID, "사유"))
                .isInstanceOf(AdminUserOperationException.class);
        verify(adminActionLogRepository, never()).save(any());
    }

    @Test
    void rejectsChangingAnotherAdminWithoutLog() {
        givenAdmin();
        User user = org.mockito.Mockito.mock(User.class);
        given(user.getRole()).willReturn(UserRole.ADMIN);
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.suspendUser(ADMIN_ID, USER_ID, "사유"))
                .isInstanceOf(AdminUserOperationException.class);
        verify(adminActionLogRepository, never()).save(any());
    }

    @Test
    void rejectsSuspendingWithdrawnUserWithoutLog() {
        assertSuspendRejected(UserStatus.WITHDRAWN);
    }

    @Test
    void rejectsReactivatingWithdrawnUserWithoutLog() {
        assertReactivateRejected(UserStatus.WITHDRAWN);
    }

    @Test
    void rejectsSuspendingAlreadySuspendedUserWithoutLog() {
        assertSuspendRejected(UserStatus.SUSPENDED);
    }

    @Test
    void rejectsReactivatingAlreadyActiveUserWithoutLog() {
        assertReactivateRejected(UserStatus.ACTIVE);
    }

    private void givenAdmin() {
        given(userRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin));
        given(admin.getRole()).willReturn(UserRole.ADMIN);
    }

    private User summaryUser(Long id, String name) {
        User user = org.mockito.Mockito.mock(User.class);
        given(user.getId()).willReturn(id);
        given(user.getName()).willReturn(name);
        return user;
    }

    private User detailUser() {
        User user = org.mockito.Mockito.mock(User.class);
        given(user.getId()).willReturn(USER_ID);
        given(user.getName()).willReturn("일반 회원");
        given(user.getEmail()).willReturn("user@example.com");
        given(user.getRole()).willReturn(UserRole.USER);
        given(user.getProvider()).willReturn(AuthProvider.KAKAO);
        given(user.getStatus()).willReturn(UserStatus.ACTIVE);
        return user;
    }

    private User statusChangeUser(UserRole role, UserStatus status) {
        User user = org.mockito.Mockito.mock(User.class);
        given(user.getRole()).willReturn(role);
        given(user.getStatus()).willReturn(status);
        return user;
    }

    private void assertSuspendRejected(UserStatus status) {
        givenAdmin();
        User user = statusChangeUser(UserRole.USER, status);
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.suspendUser(ADMIN_ID, USER_ID, "사유"))
                .isInstanceOf(AdminUserOperationException.class);
        verify(adminActionLogRepository, never()).save(any());
        verify(refreshTokenRepository, never()).deleteByUserId(any());
    }

    private void assertReactivateRejected(UserStatus status) {
        givenAdmin();
        User user = statusChangeUser(UserRole.USER, status);
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.reactivateUser(ADMIN_ID, USER_ID, "사유"))
                .isInstanceOf(AdminUserOperationException.class);
        verify(adminActionLogRepository, never()).save(any());
    }
}
