package com.giftmarket.admin.service;

import com.giftmarket.admin.dto.response.*;
import com.giftmarket.admin.exception.AdminOrderException;
import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.*;
import com.giftmarket.payment.entity.*;
import com.giftmarket.payment.repository.*;
import com.giftmarket.user.entity.*;
import com.giftmarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminOrderService {
    private static final Sort ORDER_SORT = Sort.by(Sort.Order.desc("orderedAt"), Sort.Order.desc("id"));
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final SellerOrderRepository sellerOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShipmentRepository shipmentRepository;
    private final OrderCancellationRepository orderCancellationRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ExchangeRequestRepository exchangeRequestRepository;
    private final PaymentCancellationRepository paymentCancellationRepository;

    @Transactional(readOnly = true)
    public AdminOrderPageResponse getOrders(Long adminId, int page, int size, String keyword,
                                             OrderStatus orderStatus, PaymentStatus paymentStatus,
                                             SellerOrderStatus sellerOrderStatus) {
        getAdmin(adminId);
        Page<Order> orders = orderRepository.findAdminOrders(normalize(keyword), orderStatus, paymentStatus,
                sellerOrderStatus, PageRequest.of(page, size, ORDER_SORT));
        List<Long> ids = orders.getContent().stream().map(Order::getId).toList();
        if (ids.isEmpty()) return AdminOrderPageResponse.from(orders, List.of());
        Map<Long, Payment> payments = paymentRepository.findLatestByOrderIds(ids).stream()
                .collect(Collectors.toMap(p -> p.getOrder().getId(), Function.identity()));
        Map<Long, AdminOrderItemSummaryProjection> items = orderItemRepository.summarizeAdminOrders(ids).stream()
                .collect(Collectors.toMap(AdminOrderItemSummaryProjection::getOrderId, Function.identity()));
        Map<Long, List<SellerOrderStatus>> statuses = sellerOrderRepository.findAllByOrderIdInOrderByOrderIdAscIdAsc(ids)
                .stream().collect(Collectors.groupingBy(so -> so.getOrder().getId(), LinkedHashMap::new,
                        Collectors.mapping(SellerOrder::getStatus, Collectors.toList())));
        List<AdminOrderSummaryResponse> content = orders.stream().map(o -> {
            var item = items.get(o.getId());
            return AdminOrderSummaryResponse.from(o, payments.get(o.getId()), item == null ? null : item.getRepresentativeProductName(),
                    item == null ? 0 : item.getProductTypeCount(), item == null ? 0 : item.getTotalItemCount(),
                    statuses.getOrDefault(o.getId(), List.of()));
        }).toList();
        return AdminOrderPageResponse.from(orders, content);
    }

    @Transactional(readOnly = true)
    public AdminOrderDetailResponse getOrder(Long adminId, Long orderId) {
        getAdmin(adminId);
        Order o = orderRepository.findAdminById(orderId)
                .orElseThrow(() -> new AdminOrderException("주문을 찾을 수 없습니다."));
        Payment payment = paymentRepository.findFirstByOrderIdOrderByIdDesc(orderId).orElse(null);
        List<SellerOrder> sellerOrders = sellerOrderRepository.findAllByOrderIdInOrderByOrderIdAscIdAsc(List.of(orderId));
        List<Long> sellerOrderIds = sellerOrders.stream().map(SellerOrder::getId).toList();
        Map<Long, List<OrderItem>> items = sellerOrderIds.isEmpty() ? Map.of() : orderItemRepository
                .findAllBySellerOrderIdInOrderBySellerOrderIdAscIdAsc(sellerOrderIds).stream()
                .collect(Collectors.groupingBy(i -> i.getSellerOrder().getId(), LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<Shipment>> shipments = sellerOrderIds.isEmpty() ? Map.of() : shipmentRepository
                .findAllBySellerOrderIdInOrderBySellerOrderIdAscIdAsc(sellerOrderIds).stream()
                .collect(Collectors.groupingBy(s -> s.getSellerOrder().getId(), LinkedHashMap::new, Collectors.toList()));
        List<AdminOrderDetailResponse.SellerOrderInfo> groups = sellerOrders.stream().map(so ->
                new AdminOrderDetailResponse.SellerOrderInfo(so.getId(), so.getSeller().getId(), so.getSeller().getStoreName(),
                        so.getSeller().getStatus(), so.getStatus(), so.getShippingCompany(), so.getTrackingNumber(),
                        so.getPreparedAt(), so.getShippedAt(), so.getDeliveredAt(),
                        items.getOrDefault(so.getId(), List.of()).stream().map(AdminOrderDetailResponse.Item::from).toList(),
                        shipments.getOrDefault(so.getId(), List.of()).stream().map(AdminOrderDetailResponse.ShipmentInfo::from).toList())).toList();
        long refundCount = payment == null ? 0 : paymentCancellationRepository.countByPaymentIdAndStatus(payment.getId(), PaymentCancellationStatus.SUCCEEDED);
        long refundAmount = payment == null ? 0 : paymentCancellationRepository.sumAmountByPaymentIdAndStatus(payment.getId(), PaymentCancellationStatus.SUCCEEDED);
        return new AdminOrderDetailResponse(o.getId(), o.getOrderNumber(), o.getStatus(), o.getOrderedAt(),
                o.getTotalProductAmount(), o.getTotalShippingFee(), o.getTotalAmount(), AdminOrderDetailResponse.Buyer.from(o.getUser()),
                new AdminOrderDetailResponse.Recipient(o.getRecipientName(), o.getRecipientPhone(), o.getPostalCode(), o.getAddress(), o.getAddressDetail()),
                AdminOrderDetailResponse.PaymentInfo.from(payment), groups,
                new AdminOrderDetailResponse.ClaimSummary(orderCancellationRepository.countByOrderId(orderId),
                        returnRequestRepository.countByOrderId(orderId), exchangeRequestRepository.countByOrderId(orderId)),
                new AdminOrderDetailResponse.RefundSummary(refundCount, refundAmount));
    }

    private String normalize(String keyword) { return keyword == null || keyword.isBlank() ? null : keyword.trim(); }
    private User getAdmin(Long id) {
        if (id == null) throw new AuthenticationException("인증이 필요합니다.");
        User user = userRepository.findById(id).orElseThrow(() -> new AuthenticationException("사용자를 찾을 수 없습니다."));
        if (user.getRole() != UserRole.ADMIN) throw new AuthenticationException("관리자 권한이 필요합니다.");
        return user;
    }
}
