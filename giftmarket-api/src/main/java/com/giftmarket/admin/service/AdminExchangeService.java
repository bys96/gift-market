package com.giftmarket.admin.service;

import com.giftmarket.admin.dto.response.*;
import com.giftmarket.admin.exception.AdminExchangeException;
import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.*;
import com.giftmarket.payment.repository.ExchangeShippingPaymentRepository;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminExchangeService {
    private static final Sort SORT = Sort.by(Sort.Order.desc("requestedAt"), Sort.Order.desc("id"));
    private final UserRepository users;
    private final ExchangeRequestRepository exchanges;
    private final ExchangeRequestItemRepository items;
    private final ExchangeShippingPaymentRepository payments;

    @Transactional(readOnly = true)
    public AdminExchangePageResponse getExchanges(Long adminId, int page, int size, String keyword,
                                                   ExchangeRequestStatus status, ExchangeResponsibility responsibility) {
        validateAdmin(adminId);
        var result = exchanges.findAdminExchanges(normalize(keyword), status, responsibility,
                PageRequest.of(page, size, SORT));
        var ids = result.stream().map(ExchangeRequest::getId).toList();
        if (ids.isEmpty()) return AdminExchangePageResponse.from(result, List.of());

        var summaries = items.summarizeAdminExchanges(ids).stream().collect(Collectors.toMap(
                AdminExchangeItemSummaryProjection::getExchangeId, Function.identity()));
        var shippingPayments = payments.findAllByExchangeRequestIdIn(ids).stream().collect(Collectors.toMap(
                payment -> payment.getExchangeRequest().getId(), Function.identity()));
        var content = result.stream().map(exchange -> {
            var summary = summaries.get(exchange.getId());
            return AdminExchangeSummaryResponse.from(exchange,
                    summary == null ? null : summary.getRepresentativeProductName(),
                    summary == null ? 0 : summary.getProductTypeCount(),
                    summary == null ? 0 : summary.getRequestedQuantity(),
                    shippingPayments.get(exchange.getId()));
        }).toList();
        return AdminExchangePageResponse.from(result, content);
    }

    @Transactional(readOnly = true)
    public AdminExchangeDetailResponse getExchange(Long adminId, Long exchangeId) {
        validateAdmin(adminId);
        var exchange = exchanges.findAdminById(exchangeId)
                .orElseThrow(() -> new AdminExchangeException("교환 요청을 찾을 수 없습니다."));
        var exchangeItems = items.findAdminByExchangeRequestIdOrderByIdAsc(exchangeId);
        var shippingPayment = payments.findByExchangeRequestId(exchangeId).orElse(null);
        var order = exchange.getOrder();
        var buyer = order.getUser();
        var sellerOrder = exchange.getSellerOrder();
        var seller = sellerOrder.getSeller();
        return new AdminExchangeDetailResponse(
                exchange.getId(), exchange.getStatus(), exchange.getReasonType(), exchange.getReason(),
                exchange.getResponsibility(), exchange.getRejectedReason(), exchange.getRequestedAt(),
                exchange.getApprovedAt(), exchange.getPaymentPendingAt(), exchange.getPaymentDueAt(),
                exchange.getCollectingAt(), exchange.getReceivedAt(), exchange.getInspectedAt(),
                exchange.getReshippingAt(), exchange.getCompletedAt(), exchange.getRejectedAt(),
                exchange.getCanceledAt(), exchange.getFailedAt(),
                new AdminExchangeDetailResponse.OrderInfo(order.getId(), order.getOrderNumber(), order.getStatus(), order.getOrderedAt()),
                new AdminExchangeDetailResponse.Buyer(buyer.getId(), buyer.getName(), buyer.getEmail()),
                new AdminExchangeDetailResponse.SellerInfo(sellerOrder.getId(), sellerOrder.getStatus(), seller.getId(), seller.getStoreName()),
                exchangeItems.stream().map(AdminExchangeDetailResponse.Item::from).toList(),
                AdminExchangeDetailResponse.ShipmentInfo.from(exchange.getCollectionShipment()),
                AdminExchangeDetailResponse.ShipmentInfo.from(exchange.getOutboundShipment()),
                AdminExchangeDetailResponse.ShippingPayment.from(shippingPayment));
    }

    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private void validateAdmin(Long adminId) {
        if (adminId == null) throw new AuthenticationException("인증이 필요합니다.");
        var user = users.findById(adminId).orElseThrow(() -> new AuthenticationException("사용자를 찾을 수 없습니다."));
        if (user.getRole() != UserRole.ADMIN) throw new AuthenticationException("관리자 권한이 필요합니다.");
    }
}
