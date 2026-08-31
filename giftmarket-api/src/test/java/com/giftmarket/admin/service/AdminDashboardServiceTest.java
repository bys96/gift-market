package com.giftmarket.admin.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.order.entity.ExchangeRequestStatus;
import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.entity.ReturnRequestStatus;
import com.giftmarket.order.repository.ExchangeRequestRepository;
import com.giftmarket.order.repository.OrderCancellationRepository;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.order.repository.ReturnRequestRepository;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.product.repository.ProductRepository;
import com.giftmarket.seller.entity.SellerApplication;
import com.giftmarket.seller.entity.SellerApplicationStatus;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.repository.SellerApplicationRepository;
import com.giftmarket.seller.repository.SellerRepository;
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
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    private static final Long ADMIN_ID = 1L;

    @Mock UserRepository userRepository;
    @Mock SellerRepository sellerRepository;
    @Mock SellerApplicationRepository sellerApplicationRepository;
    @Mock ProductRepository productRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderCancellationRepository orderCancellationRepository;
    @Mock ReturnRequestRepository returnRequestRepository;
    @Mock ExchangeRequestRepository exchangeRequestRepository;
    @Mock User admin;

    private AdminDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardService(
                userRepository,
                sellerRepository,
                sellerApplicationRepository,
                productRepository,
                orderRepository,
                orderCancellationRepository,
                returnRequestRepository,
                exchangeRequestRepository
        );
    }

    @Test
    void rejectsNullAdminId() {
        assertThatThrownBy(() -> service.getDashboard(null))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("인증이 필요합니다.");

        verify(userRepository, never()).findById(any());
    }

    @Test
    void rejectsMissingUser() {
        given(userRepository.findById(ADMIN_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDashboard(ADMIN_ID))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("사용자를 찾을 수 없습니다.");
    }

    @Test
    void rejectsNonAdminUser() {
        given(userRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin));
        given(admin.getRole()).willReturn(UserRole.USER);

        assertThatThrownBy(() -> service.getDashboard(ADMIN_ID))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("관리자 권한이 필요합니다.");

        verify(sellerApplicationRepository, never()).countByStatus(any());
    }

    @Test
    void returnsOperationalCountsForAdmin() {
        givenAdmin();
        givenEmptyRecentLists();
        given(sellerApplicationRepository.countByStatus(SellerApplicationStatus.PENDING)).willReturn(2L);
        given(orderCancellationRepository.countByStatus(OrderCancellationStatus.REQUESTED)).willReturn(3L);
        given(returnRequestRepository.countByStatus(ReturnRequestStatus.REQUESTED)).willReturn(4L);
        given(exchangeRequestRepository.countByStatus(ExchangeRequestStatus.REQUESTED)).willReturn(5L);
        given(userRepository.countByStatusNot(UserStatus.WITHDRAWN)).willReturn(100L);
        given(sellerRepository.countByStatus(SellerStatus.ACTIVE)).willReturn(20L);
        given(productRepository.countByStatusAndDeletedAtIsNull(ProductStatus.ON_SALE)).willReturn(50L);
        given(orderRepository.count()).willReturn(80L);

        var response = service.getDashboard(ADMIN_ID);

        assertThat(response.actionCenter().pendingSellerApplications()).isEqualTo(2L);
        assertThat(response.actionCenter().pendingCancellations()).isEqualTo(3L);
        assertThat(response.actionCenter().pendingReturns()).isEqualTo(4L);
        assertThat(response.actionCenter().pendingExchanges()).isEqualTo(5L);
        assertThat(response.summary().totalUsers()).isEqualTo(100L);
        assertThat(response.summary().activeSellers()).isEqualTo(20L);
        assertThat(response.summary().sellingProducts()).isEqualTo(50L);
        assertThat(response.summary().totalOrders()).isEqualTo(80L);
    }

    @Test
    void requestsAtMostFiveRecentItemsInNewestFirstOrder() {
        givenAdmin();
        givenEmptyRecentLists();

        service.getDashboard(ADMIN_ID);

        ArgumentCaptor<Pageable> orderPageable = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<Pageable> applicationPageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findAllBy(orderPageable.capture());
        verify(sellerApplicationRepository).findAllBy(applicationPageable.capture());

        assertRecentPage(orderPageable.getValue());
        assertRecentPage(applicationPageable.getValue());
    }

    @Test
    void mapsRecentOrdersAndSellerApplications() {
        givenAdmin();
        Order order = org.mockito.Mockito.mock(Order.class);
        SellerApplication application = org.mockito.Mockito.mock(SellerApplication.class);
        User applicant = org.mockito.Mockito.mock(User.class);
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 31, 10, 0);

        given(order.getId()).willReturn(10L);
        given(order.getOrderNumber()).willReturn("GM-10");
        given(order.getTotalAmount()).willReturn(25_000L);
        given(order.getCreatedAt()).willReturn(createdAt);
        given(application.getId()).willReturn(20L);
        given(application.getStoreName()).willReturn("선물 상점");
        given(application.getStatus()).willReturn(SellerApplicationStatus.PENDING);
        given(application.getCreatedAt()).willReturn(createdAt);
        given(application.getUser()).willReturn(applicant);
        given(applicant.getName()).willReturn("관리 대상 사용자");
        given(orderRepository.findAllBy(any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(order)));
        given(sellerApplicationRepository.findAllBy(any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(application)));

        var response = service.getDashboard(ADMIN_ID);

        assertThat(response.recentOrders()).singleElement().satisfies(recent -> {
            assertThat(recent.id()).isEqualTo(10L);
            assertThat(recent.orderNumber()).isEqualTo("GM-10");
            assertThat(recent.createdAt()).isEqualTo(createdAt);
        });
        assertThat(response.recentSellerApplications()).singleElement().satisfies(recent -> {
            assertThat(recent.id()).isEqualTo(20L);
            assertThat(recent.applicantName()).isEqualTo("관리 대상 사용자");
            assertThat(recent.createdAt()).isEqualTo(createdAt);
        });
    }

    private void givenAdmin() {
        given(userRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin));
        given(admin.getRole()).willReturn(UserRole.ADMIN);
    }

    private void givenEmptyRecentLists() {
        given(orderRepository.findAllBy(any(Pageable.class))).willReturn(new PageImpl<>(List.of()));
        given(sellerApplicationRepository.findAllBy(any(Pageable.class))).willReturn(new PageImpl<>(List.of()));
    }

    private void assertRecentPage(Pageable pageable) {
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort().getOrderFor("createdAt").isDescending()).isTrue();
        assertThat(pageable.getSort().getOrderFor("id").isDescending()).isTrue();
    }
}
