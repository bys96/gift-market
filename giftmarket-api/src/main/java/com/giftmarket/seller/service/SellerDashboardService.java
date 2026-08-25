package com.giftmarket.seller.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.order.entity.ExchangeRequestStatus;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.entity.ReturnRequestStatus;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.repository.ExchangeRequestRepository;
import com.giftmarket.order.repository.OrderCancellationRepository;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.order.repository.ReturnRequestRepository;
import com.giftmarket.order.repository.SellerOrderItemSummaryProjection;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.product.repository.ProductRepository;
import com.giftmarket.seller.dto.response.SellerDashboardResponse;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.exception.SellerException;
import com.giftmarket.seller.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SellerDashboardService {

    private static final int RECENT_ORDER_LIMIT = 5;
    private static final Set<SellerOrderStatus> ACTIONABLE_ORDER_STATUSES = Set.of(
            SellerOrderStatus.PAID,
            SellerOrderStatus.PREPARING,
            SellerOrderStatus.SHIPPED
    );

    private final SellerRepository sellerRepository;
    private final SellerOrderRepository sellerOrderRepository;
    private final OrderCancellationRepository orderCancellationRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ExchangeRequestRepository exchangeRequestRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional(readOnly = true)
    public SellerDashboardResponse getDashboard(Long userId) {
        Seller seller = getActiveSeller(userId);
        Long sellerId = seller.getId();

        long actionableOrders = sellerOrderRepository.countBySellerIdAndStatusIn(
                sellerId, ACTIONABLE_ORDER_STATUSES
        );
        long actionableCancellations = orderCancellationRepository
                .countBySellerOrderSellerIdAndRequiresSellerApprovalTrueAndStatus(
                        sellerId, OrderCancellationStatus.REQUESTED
                );

        SellerDashboardResponse.ReturnActions returnActions = returnActions(sellerId);
        SellerDashboardResponse.ExchangeActions exchangeActions = exchangeActions(sellerId);
        SellerDashboardResponse.ProductSummary productSummary = new SellerDashboardResponse.ProductSummary(
                productRepository.countBySellerIdAndStatusAndDeletedAtIsNull(
                        sellerId, ProductStatus.ON_SALE
                ),
                productRepository.countBySellerIdAndStatusAndDeletedAtIsNull(
                        sellerId, ProductStatus.SOLD_OUT
                )
        );

        return new SellerDashboardResponse(
                seller.getStoreName(),
                new SellerDashboardResponse.ActionRequired(
                        actionableOrders,
                        actionableCancellations,
                        returnActions,
                        exchangeActions
                ),
                productSummary,
                recentOrders(sellerId)
        );
    }

    private SellerDashboardResponse.ReturnActions returnActions(Long sellerId) {
        long approval = countReturns(sellerId, ReturnRequestStatus.REQUESTED);
        long collection = countReturns(sellerId, ReturnRequestStatus.APPROVED);
        long receiving = countReturns(sellerId, ReturnRequestStatus.COLLECTING);
        long inspection = countReturns(sellerId, ReturnRequestStatus.RECEIVED);
        return new SellerDashboardResponse.ReturnActions(
                approval + collection + receiving + inspection,
                approval,
                collection,
                receiving,
                inspection
        );
    }

    private SellerDashboardResponse.ExchangeActions exchangeActions(Long sellerId) {
        long approval = countExchanges(sellerId, ExchangeRequestStatus.REQUESTED);
        long collectionOrReceiving = countExchanges(sellerId, ExchangeRequestStatus.COLLECTING);
        long inspection = countExchanges(sellerId, ExchangeRequestStatus.RECEIVED);
        long outbound = countExchanges(sellerId, ExchangeRequestStatus.INSPECTED)
                + countExchanges(sellerId, ExchangeRequestStatus.RESHIPPING);
        return new SellerDashboardResponse.ExchangeActions(
                approval + collectionOrReceiving + inspection + outbound,
                approval,
                collectionOrReceiving,
                inspection,
                outbound
        );
    }

    private long countReturns(Long sellerId, ReturnRequestStatus status) {
        return returnRequestRepository.countBySellerOrderSellerIdAndStatus(sellerId, status);
    }

    private long countExchanges(Long sellerId, ExchangeRequestStatus status) {
        return exchangeRequestRepository.countBySellerOrderSellerIdAndStatus(sellerId, status);
    }

    private List<SellerDashboardResponse.RecentOrder> recentOrders(Long sellerId) {
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

        return orders.stream().map(order -> toRecentOrder(order, summaries)).toList();
    }

    private SellerDashboardResponse.RecentOrder toRecentOrder(
            SellerOrder sellerOrder,
            Map<Long, SellerOrderItemSummaryProjection> summaries
    ) {
        SellerOrderItemSummaryProjection summary = summaries.get(sellerOrder.getId());
        if (summary == null) {
            throw new SellerException("판매자 주문 상품 정보를 확인할 수 없습니다.");
        }
        return new SellerDashboardResponse.RecentOrder(
                sellerOrder.getId(),
                sellerOrder.getOrder().getId(),
                sellerOrder.getOrder().getOrderNumber(),
                sellerOrder.getOrder().getOrderedAt(),
                summary.getRepresentativeProductName(),
                Math.max(0, summary.getProductTypeCount() - 1),
                summary.getTotalQuantity(),
                summary.getTotalProductAmount(),
                sellerOrder.getStatus()
        );
    }

    private Seller getActiveSeller(Long userId) {
        if (userId == null) {
            throw new AuthenticationException("인증이 필요합니다.");
        }
        Seller seller = sellerRepository.findByUserId(userId)
                .orElseThrow(() -> new SellerException("판매자 정보를 찾을 수 없습니다."));
        if (seller.getStatus() != SellerStatus.ACTIVE) {
            throw new SellerException("활성 상태의 판매자만 대시보드를 조회할 수 있습니다.");
        }
        return seller;
    }
}
