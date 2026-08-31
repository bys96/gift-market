package com.giftmarket.admin.service;

import com.giftmarket.admin.exception.AdminSellerManagementException;
import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.order.repository.SellerOrderItemSummaryProjection;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.product.repository.ProductRepository;
import com.giftmarket.product.repository.SellerProductCountProjection;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminSellerManagementServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long SELLER_ID = 10L;
    private static final Long USER_ID = 20L;

    @Mock UserRepository userRepository;
    @Mock SellerRepository sellerRepository;
    @Mock SellerApplicationRepository sellerApplicationRepository;
    @Mock ProductRepository productRepository;
    @Mock SellerOrderRepository sellerOrderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock User admin;

    private AdminSellerManagementService service;

    @BeforeEach
    void setUp() {
        service = new AdminSellerManagementService(
                userRepository, sellerRepository, sellerApplicationRepository,
                productRepository, sellerOrderRepository, orderItemRepository
        );
    }

    @Test
    void rejectsNullAdminId() {
        assertThatThrownBy(() -> service.getSellers(null, 0, 20, null, null))
                .isInstanceOf(AuthenticationException.class).hasMessage("인증이 필요합니다.");
        verify(sellerRepository, never()).findAdminSellers(any(), any(), any());
    }

    @Test
    void rejectsMissingAdmin() {
        given(userRepository.findById(ADMIN_ID)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.getSellers(ADMIN_ID, 0, 20, null, null))
                .isInstanceOf(AuthenticationException.class).hasMessage("사용자를 찾을 수 없습니다.");
    }

    @Test
    void rejectsNonAdmin() {
        given(userRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin));
        given(admin.getRole()).willReturn(UserRole.USER);
        assertThatThrownBy(() -> service.getSellers(ADMIN_ID, 0, 20, null, null))
                .isInstanceOf(AuthenticationException.class).hasMessage("관리자 권한이 필요합니다.");
    }

    @Test
    void passesTrimmedKeywordStatusPaginationAndStableSort() {
        givenAdmin();
        given(sellerRepository.findAdminSellers(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));

        service.getSellers(ADMIN_ID, 2, 20, "  gift  ", SellerStatus.ACTIVE);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(sellerRepository).findAdminSellers(eq("gift"), eq(SellerStatus.ACTIVE), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
        assertThat(pageable.getValue().getSort().getOrderFor("id").isDescending()).isTrue();
    }

    @Test
    void mapsOwnerStatusAndBatchProductCounts() {
        givenAdmin();
        Seller first = summarySeller(10L, SellerStatus.ACTIVE, "첫 상점");
        Seller second = summarySeller(11L, SellerStatus.SUSPENDED, "둘째 상점");
        SellerProductCountProjection count = org.mockito.Mockito.mock(SellerProductCountProjection.class);
        given(sellerRepository.findAdminSellers(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(first, second)));
        given(productRepository.countBySellerIdsAndStatus(List.of(10L, 11L), ProductStatus.ON_SALE))
                .willReturn(List.of(count));
        given(count.getSellerId()).willReturn(11L);
        given(count.getProductCount()).willReturn(7L);

        var response = service.getSellers(ADMIN_ID, 0, 20, " ", null);

        assertThat(response.content().get(0).onSaleProductCount()).isZero();
        assertThat(response.content().get(1).onSaleProductCount()).isEqualTo(7L);
        assertThat(response.content().get(1).status()).isEqualTo(SellerStatus.SUSPENDED);
        assertThat(response.content().get(1).userId()).isEqualTo(USER_ID);
        verify(sellerRepository).findAdminSellers(eq(null), eq(null), any());
    }

    @Test
    void throwsNotFoundForMissingSeller() {
        givenAdmin();
        given(sellerRepository.findWithUserById(SELLER_ID)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.getSeller(ADMIN_ID, SELLER_ID))
                .isInstanceOf(AdminSellerManagementException.class)
                .hasMessage("판매자를 찾을 수 없습니다.");
    }

    @ParameterizedTest
    @EnumSource(SellerStatus.class)
    void returnsEverySellerStatusWithOwnerCountsAndOptionalApplication(SellerStatus status) {
        givenAdmin();
        Seller seller = detailSeller(status);
        given(sellerRepository.findWithUserById(SELLER_ID)).willReturn(Optional.of(seller));
        given(sellerApplicationRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .willReturn(Optional.empty());
        given(productRepository.countBySellerIdAndDeletedAtIsNull(SELLER_ID)).willReturn(8L);
        given(productRepository.countBySellerIdAndStatusAndDeletedAtIsNull(SELLER_ID, ProductStatus.ON_SALE)).willReturn(5L);
        given(sellerOrderRepository.countBySellerId(SELLER_ID)).willReturn(12L);
        given(sellerOrderRepository.findRecentSellerOrders(eq(SELLER_ID), eq(SellerOrderStatus.PENDING_PAYMENT), any()))
                .willReturn(List.of());

        var response = service.getSeller(ADMIN_ID, SELLER_ID);

        assertThat(response.status()).isEqualTo(status);
        assertThat(response.owner().userId()).isEqualTo(USER_ID);
        assertThat(response.sellerApplication()).isNull();
        assertThat(response.activity().totalProducts()).isEqualTo(8L);
        assertThat(response.activity().onSaleProducts()).isEqualTo(5L);
        assertThat(response.activity().totalOrders()).isEqualTo(12L);
    }

    @Test
    void mapsLatestApplicationAndFiveMostRecentOrders() {
        givenAdmin();
        Seller seller = detailSeller(SellerStatus.ACTIVE);
        SellerApplication application = org.mockito.Mockito.mock(SellerApplication.class);
        SellerOrder sellerOrder = org.mockito.Mockito.mock(SellerOrder.class);
        Order order = org.mockito.Mockito.mock(Order.class);
        SellerOrderItemSummaryProjection summary = org.mockito.Mockito.mock(SellerOrderItemSummaryProjection.class);
        LocalDateTime orderedAt = LocalDateTime.of(2026, 8, 31, 12, 0);
        given(sellerRepository.findWithUserById(SELLER_ID)).willReturn(Optional.of(seller));
        given(sellerApplicationRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .willReturn(Optional.of(application));
        given(application.getId()).willReturn(30L);
        given(application.getStatus()).willReturn(SellerApplicationStatus.APPROVED);
        given(sellerOrderRepository.findRecentSellerOrders(eq(SELLER_ID), eq(SellerOrderStatus.PENDING_PAYMENT), any()))
                .willReturn(List.of(sellerOrder));
        given(sellerOrder.getId()).willReturn(40L);
        given(sellerOrder.getOrder()).willReturn(order);
        given(sellerOrder.getStatus()).willReturn(SellerOrderStatus.PAID);
        given(order.getId()).willReturn(50L);
        given(order.getOrderNumber()).willReturn("GM-50");
        given(order.getOrderedAt()).willReturn(orderedAt);
        given(orderItemRepository.summarizeBySellerOrderIds(List.of(40L))).willReturn(List.of(summary));
        given(summary.getSellerOrderId()).willReturn(40L);
        given(summary.getTotalProductAmount()).willReturn(60_000L);

        var response = service.getSeller(ADMIN_ID, SELLER_ID);

        assertThat(response.sellerApplication().applicationId()).isEqualTo(30L);
        assertThat(response.recentOrders()).singleElement().satisfies(recent -> {
            assertThat(recent.orderNumber()).isEqualTo("GM-50");
            assertThat(recent.totalProductAmount()).isEqualTo(60_000L);
        });
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(sellerOrderRepository).findRecentSellerOrders(
                eq(SELLER_ID), eq(SellerOrderStatus.PENDING_PAYMENT), pageable.capture()
        );
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    }

    private void givenAdmin() {
        given(userRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin));
        given(admin.getRole()).willReturn(UserRole.ADMIN);
    }

    private Seller summarySeller(Long id, SellerStatus status, String storeName) {
        Seller seller = org.mockito.Mockito.mock(Seller.class);
        User owner = org.mockito.Mockito.mock(User.class);
        given(seller.getId()).willReturn(id);
        given(seller.getStatus()).willReturn(status);
        given(seller.getStoreName()).willReturn(storeName);
        given(seller.getUser()).willReturn(owner);
        given(owner.getId()).willReturn(USER_ID);
        given(owner.getName()).willReturn("판매자 회원");
        return seller;
    }

    private Seller detailSeller(SellerStatus status) {
        Seller seller = summarySeller(SELLER_ID, status, "선물 상점");
        User owner = seller.getUser();
        given(owner.getRole()).willReturn(UserRole.SELLER);
        given(owner.getProvider()).willReturn(AuthProvider.GOOGLE);
        given(owner.getStatus()).willReturn(UserStatus.ACTIVE);
        return seller;
    }
}
