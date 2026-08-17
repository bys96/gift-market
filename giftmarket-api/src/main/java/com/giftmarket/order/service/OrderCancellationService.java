package com.giftmarket.order.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.order.dto.request.OrderCancellationCreateRequest;
import com.giftmarket.order.dto.request.OrderCancellationItemRequest;
import com.giftmarket.order.dto.response.OrderCancellationResponse;
import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderCancellation;
import com.giftmarket.order.entity.OrderCancellationItem;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.order.repository.OrderCancellationItemRepository;
import com.giftmarket.order.repository.OrderCancellationRepository;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.order.repository.PendingCancellationQuantityProjection;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentStatus;
import com.giftmarket.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderCancellationService {

    private static final Set<OrderCancellationStatus> QUANTITY_HOLDING_STATUSES = Set.of(
            OrderCancellationStatus.REQUESTED,
            OrderCancellationStatus.PROCESSING
    );
    private static final int MAX_REASON_LENGTH = 500;
    private static final int MAX_ITEM_COUNT = 100;

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final SellerOrderRepository sellerOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderCancellationRepository orderCancellationRepository;
    private final OrderCancellationItemRepository orderCancellationItemRepository;

    @Transactional(readOnly = true)
    public OrderCancellationResponse getOwned(Long userId, Long orderId, Long cancellationId) {
        validateAuthenticated(userId);
        OrderCancellation cancellation = orderCancellationRepository.findById(cancellationId)
                .orElseThrow(this::orderNotFound);
        if (!cancellation.getOrder().getId().equals(orderId)
                || !cancellation.getOrder().getUser().getId().equals(userId)) {
            throw orderNotFound();
        }
        return OrderCancellationResponse.from(cancellation,
                orderCancellationItemRepository.findAllByOrderCancellationIdOrderByIdAsc(cancellationId));
    }

    @Transactional(readOnly = true)
    public List<OrderCancellationResponse> getAllOwned(Long userId, Long orderId) {
        validateAuthenticated(userId);
        orderRepository.findByIdAndUserId(orderId, userId).orElseThrow(this::orderNotFound);
        List<OrderCancellation> cancellations = orderCancellationRepository
                .findAllByOrderIdOrderByRequestedAtDescIdDesc(orderId);
        if (cancellations.isEmpty()) return List.of();
        Map<Long, List<OrderCancellationItem>> itemsByCancellationId = orderCancellationItemRepository
                .findAllByOrderCancellationIdInOrderByOrderCancellationIdAscOrderItemIdAsc(
                        cancellations.stream().map(OrderCancellation::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(item -> item.getOrderCancellation().getId()));
        return cancellations.stream()
                .map(cancellation -> OrderCancellationResponse.from(cancellation,
                        itemsByCancellationId.getOrDefault(cancellation.getId(), List.of())))
                .toList();
    }

    @Transactional
    public OrderCancellationResponse create(
            Long userId,
            Long orderId,
            OrderCancellationCreateRequest request
    ) {
        validateAuthenticated(userId);
        String clientRequestKey = normalizeRequestKey(request.clientRequestKey());
        String reason = normalizeReason(request.reason());
        Map<Long, Integer> requestedQuantities = normalizeItems(request.items());

        Optional<OrderCancellationResponse> existing = findExisting(
                userId,
                orderId,
                request.sellerOrderId(),
                clientRequestKey,
                reason,
                requestedQuantities
        );
        if (existing.isPresent()) {
            return existing.get();
        }

        Payment payment = paymentRepository
                .findFirstByOrderIdAndOrderUserIdOrderByIdDesc(orderId, userId)
                .flatMap(value -> paymentRepository.findByIdAndOrderUserIdForUpdate(
                        value.getId(),
                        userId
                ))
                .orElseThrow(this::orderNotFound);
        if (!payment.isRefundableState()) {
            throw new OrderException("결제가 완료된 주문만 상품 취소를 요청할 수 있습니다.");
        }

        Order order = orderRepository.findByIdAndUserIdForUpdate(orderId, userId)
                .orElseThrow(this::orderNotFound);
        validateOrderStatus(order);

        existing = findExisting(
                userId,
                orderId,
                request.sellerOrderId(),
                clientRequestKey,
                reason,
                requestedQuantities
        );
        if (existing.isPresent()) {
            return existing.get();
        }

        SellerOrder sellerOrder = sellerOrderRepository.findByIdAndOrderIdForUpdate(
                        request.sellerOrderId(),
                        orderId
                )
                .orElseThrow(() -> new OrderException("취소할 판매자 주문 정보를 확인할 수 없습니다."));
        validateSellerOrderStatus(sellerOrder);

        List<Long> orderItemIds = requestedQuantities.keySet().stream().sorted().toList();
        List<OrderItem> orderItems = orderItemRepository.findAllByIdInForUpdate(orderItemIds);
        validateOrderItems(order, sellerOrder, orderItems, orderItemIds);

        Map<Long, Long> pendingQuantities = pendingQuantities(orderItemIds);
        validateAvailableQuantities(orderItems, requestedQuantities, pendingQuantities);

        OrderCancellation cancellation;
        try {
            cancellation = orderCancellationRepository.saveAndFlush(
                    OrderCancellation.createRequested(
                            order,
                            sellerOrder,
                            clientRequestKey,
                            reason,
                            sellerOrder.getStatus() == SellerOrderStatus.PREPARING,
                            LocalDateTime.now()
                    )
            );
        } catch (DataIntegrityViolationException exception) {
            throw new OrderException("이미 사용된 취소 요청 키입니다.");
        }
        Map<Long, OrderItem> orderItemsById = orderItems.stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));
        List<OrderCancellationItem> cancellationItems = orderItemIds.stream()
                .map(orderItemId -> OrderCancellationItem.create(
                        cancellation,
                        orderItemsById.get(orderItemId),
                        requestedQuantities.get(orderItemId)
                ))
                .toList();
        orderCancellationItemRepository.saveAll(cancellationItems);

        return OrderCancellationResponse.from(cancellation, cancellationItems);
    }

    private Optional<OrderCancellationResponse> findExisting(
            Long userId,
            Long orderId,
            Long sellerOrderId,
            String clientRequestKey,
            String reason,
            Map<Long, Integer> requestedQuantities
    ) {
        return orderCancellationRepository.findByClientRequestKey(clientRequestKey)
                .map(cancellation -> {
                    if (!cancellation.getOrder().getUser().getId().equals(userId)) {
                        throw new OrderException("이미 사용된 취소 요청 키입니다.");
                    }
                    List<OrderCancellationItem> items = orderCancellationItemRepository
                            .findAllByOrderCancellationIdOrderByIdAsc(cancellation.getId());
                    validateIdempotentRequest(
                            cancellation,
                            items,
                            orderId,
                            sellerOrderId,
                            reason,
                            requestedQuantities
                    );
                    return OrderCancellationResponse.from(cancellation, items);
                });
    }

    private void validateIdempotentRequest(
            OrderCancellation cancellation,
            List<OrderCancellationItem> items,
            Long orderId,
            Long sellerOrderId,
            String reason,
            Map<Long, Integer> requestedQuantities
    ) {
        Map<Long, Integer> existingQuantities = items.stream()
                .collect(Collectors.toMap(
                        item -> item.getOrderItem().getId(),
                        OrderCancellationItem::getQuantity
                ));
        if (!cancellation.getOrder().getId().equals(orderId)
                || !cancellation.getSellerOrder().getId().equals(sellerOrderId)
                || !cancellation.getReason().equals(reason)
                || !existingQuantities.equals(requestedQuantities)) {
            throw new OrderException("취소 요청 키가 최초 요청과 다른 내용으로 재사용되었습니다.");
        }
    }

    private void validateOrderStatus(Order order) {
        if (order.getStatus() != OrderStatus.PAID) {
            throw new OrderException("결제가 완료된 주문만 상품 취소를 요청할 수 있습니다.");
        }
    }

    private void validateSellerOrderStatus(SellerOrder sellerOrder) {
        if (sellerOrder.getStatus() != SellerOrderStatus.PAID
                && sellerOrder.getStatus() != SellerOrderStatus.PREPARING) {
            throw new OrderException("현재 배송 상태에서는 상품 취소를 요청할 수 없습니다.");
        }
    }

    private void validateOrderItems(
            Order order,
            SellerOrder sellerOrder,
            List<OrderItem> orderItems,
            List<Long> requestedIds
    ) {
        if (orderItems.size() != requestedIds.size()) {
            throw new OrderException("취소할 주문 상품 정보를 확인할 수 없습니다.");
        }
        for (OrderItem orderItem : orderItems) {
            if (!orderItem.getOrder().getId().equals(order.getId())
                    || !orderItem.getSellerOrder().getId().equals(sellerOrder.getId())) {
                throw new OrderException("같은 판매자 주문의 상품만 함께 취소할 수 있습니다.");
            }
        }
    }

    private Map<Long, Long> pendingQuantities(Collection<Long> orderItemIds) {
        return orderCancellationRepository.sumItemQuantitiesByStatuses(
                        orderItemIds,
                        QUANTITY_HOLDING_STATUSES
                )
                .stream()
                .collect(Collectors.toMap(
                        PendingCancellationQuantityProjection::getOrderItemId,
                        PendingCancellationQuantityProjection::getPendingQuantity
                ));
    }

    private void validateAvailableQuantities(
            List<OrderItem> orderItems,
            Map<Long, Integer> requestedQuantities,
            Map<Long, Long> pendingQuantities
    ) {
        for (OrderItem orderItem : orderItems) {
            long availableQuantity = (long) orderItem.getQuantity()
                    - orderItem.getCanceledQuantity()
                    - pendingQuantities.getOrDefault(orderItem.getId(), 0L);
            if (requestedQuantities.get(orderItem.getId()) > availableQuantity) {
                throw new OrderException("취소 가능 수량을 초과한 상품이 있습니다.");
            }
        }
    }

    private Map<Long, Integer> normalizeItems(List<OrderCancellationItemRequest> items) {
        if (items == null || items.isEmpty() || items.size() > MAX_ITEM_COUNT) {
            throw new OrderException("취소할 주문 상품을 1개 이상 선택해주세요.");
        }
        Map<Long, Integer> normalized = new LinkedHashMap<>();
        for (OrderCancellationItemRequest item : items) {
            if (item == null || item.orderItemId() == null || item.orderItemId() <= 0
                    || item.quantity() == null || item.quantity() <= 0) {
                throw new OrderException("취소 상품과 수량을 다시 확인해주세요.");
            }
            if (normalized.putIfAbsent(item.orderItemId(), item.quantity()) != null) {
                throw new OrderException("같은 주문 상품이 취소 요청에 중복되었습니다.");
            }
        }
        return normalized;
    }

    private String normalizeRequestKey(String clientRequestKey) {
        if (clientRequestKey == null) {
            throw new OrderException("취소 요청 키가 필요합니다.");
        }
        String normalized = clientRequestKey.trim();
        try {
            if (!UUID.fromString(normalized).toString().equalsIgnoreCase(normalized)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new OrderException("취소 요청 키는 UUID 형식이어야 합니다.");
        }
        return normalized;
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new OrderException("취소 사유를 입력해주세요.");
        }
        String normalized = reason.trim();
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw new OrderException("취소 사유는 500자 이내로 입력해주세요.");
        }
        return normalized;
    }

    private void validateAuthenticated(Long userId) {
        if (userId == null) {
            throw new AuthenticationException("인증이 필요합니다.");
        }
    }

    private OrderException orderNotFound() {
        return new OrderException("주문 정보를 찾을 수 없습니다.");
    }
}
