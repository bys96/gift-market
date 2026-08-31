package com.giftmarket.admin.service;

import com.giftmarket.admin.dto.response.AdminSellerDetailResponse;
import com.giftmarket.admin.dto.response.AdminSellerPageResponse;
import com.giftmarket.admin.dto.response.AdminSellerSummaryResponse;
import com.giftmarket.admin.exception.AdminSellerManagementException;
import com.giftmarket.auth.exception.AuthenticationException;
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
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.repository.SellerApplicationRepository;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminSellerManagementService {

    private static final int RECENT_ORDER_LIMIT = 5;
    private static final Sort SELLER_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );

    private final UserRepository userRepository;
    private final SellerRepository sellerRepository;
    private final SellerApplicationRepository sellerApplicationRepository;
    private final ProductRepository productRepository;
    private final SellerOrderRepository sellerOrderRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional(readOnly = true)
    public AdminSellerPageResponse getSellers(
            Long adminUserId,
            int page,
            int size,
            String keyword,
            SellerStatus status
    ) {
        getAdmin(adminUserId);
        var sellerPage = sellerRepository.findAdminSellers(
                normalizeKeyword(keyword),
                status,
                PageRequest.of(page, size, SELLER_SORT)
        );
        List<Long> sellerIds = sellerPage.getContent().stream().map(Seller::getId).toList();
        Map<Long, Long> onSaleCounts = onSaleProductCounts(sellerIds);
        List<AdminSellerSummaryResponse> content = sellerPage.getContent().stream()
                .map(seller -> AdminSellerSummaryResponse.from(
                        seller,
                        onSaleCounts.getOrDefault(seller.getId(), 0L)
                ))
                .toList();
        return AdminSellerPageResponse.from(sellerPage, content);
    }

    @Transactional(readOnly = true)
    public AdminSellerDetailResponse getSeller(Long adminUserId, Long sellerId) {
        getAdmin(adminUserId);
        Seller seller = sellerRepository.findWithUserById(sellerId)
                .orElseThrow(() -> new AdminSellerManagementException("판매자를 찾을 수 없습니다."));
        SellerApplication application = sellerApplicationRepository
                .findFirstByUserIdOrderByCreatedAtDescIdDesc(seller.getUser().getId())
                .orElse(null);

        return AdminSellerDetailResponse.from(
                seller,
                application,
                productRepository.countBySellerIdAndDeletedAtIsNull(sellerId),
                productRepository.countBySellerIdAndStatusAndDeletedAtIsNull(sellerId, ProductStatus.ON_SALE),
                sellerOrderRepository.countBySellerId(sellerId),
                recentOrders(sellerId)
        );
    }

    private Map<Long, Long> onSaleProductCounts(List<Long> sellerIds) {
        if (sellerIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.countBySellerIdsAndStatus(sellerIds, ProductStatus.ON_SALE)
                .stream()
                .collect(Collectors.toMap(
                        SellerProductCountProjection::getSellerId,
                        SellerProductCountProjection::getProductCount
                ));
    }

    private List<AdminSellerDetailResponse.RecentOrder> recentOrders(Long sellerId) {
        List<SellerOrder> orders = sellerOrderRepository.findRecentSellerOrders(
                sellerId,
                SellerOrderStatus.PENDING_PAYMENT,
                PageRequest.of(0, RECENT_ORDER_LIMIT)
        );
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> sellerOrderIds = orders.stream().map(SellerOrder::getId).toList();
        Map<Long, SellerOrderItemSummaryProjection> summaries = orderItemRepository
                .summarizeBySellerOrderIds(sellerOrderIds)
                .stream()
                .collect(Collectors.toMap(
                        SellerOrderItemSummaryProjection::getSellerOrderId,
                        Function.identity()
                ));
        return orders.stream()
                .map(order -> {
                    SellerOrderItemSummaryProjection summary = summaries.get(order.getId());
                    if (summary == null) {
                        throw new IllegalStateException("판매자 주문 상품 정보를 확인할 수 없습니다.");
                    }
                    return AdminSellerDetailResponse.RecentOrder.from(order, summary);
                })
                .toList();
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    private User getAdmin(Long adminUserId) {
        if (adminUserId == null) {
            throw new AuthenticationException("인증이 필요합니다.");
        }
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new AuthenticationException("사용자를 찾을 수 없습니다."));
        if (admin.getRole() != UserRole.ADMIN) {
            throw new AuthenticationException("관리자 권한이 필요합니다.");
        }
        return admin;
    }
}
