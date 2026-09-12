package com.giftmarket.order.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.order.dto.request.SellerOrderShipRequest;
import com.giftmarket.order.dto.request.SellerOrderCancelRequest;
import com.giftmarket.order.dto.response.SellerOrderCancelValidationResponse;
import com.giftmarket.order.dto.response.OrderCancellationResponse;
import com.giftmarket.order.dto.response.SellerOrderDetailResponse;
import com.giftmarket.order.dto.response.SellerOrderCancellationSummaryResponse;
import com.giftmarket.order.dto.response.SellerOrderListItemResponse;
import com.giftmarket.order.dto.response.SellerOrderPageResponse;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.entity.OrderCancellation;
import com.giftmarket.order.entity.OrderCancellationItem;
import com.giftmarket.order.entity.ExchangeRequestStatus;
import com.giftmarket.order.entity.ReturnRequestStatus;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.entity.Shipment;
import com.giftmarket.order.entity.ShipmentType;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.order.repository.OrderCancellationRepository;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.order.repository.SellerOrderItemSummaryProjection;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.order.repository.ShipmentRepository;
import com.giftmarket.order.repository.OrderCancellationItemRepository;
import com.giftmarket.order.repository.ReturnRequestRepository;
import com.giftmarket.order.repository.ExchangeRequestRepository;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.exception.SellerException;
import com.giftmarket.seller.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SellerOrderManagementService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 100;

    private final SellerRepository sellerRepository;
    private final SellerOrderRepository sellerOrderRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderCancellationRepository orderCancellationRepository;
    private final OrderCancellationItemRepository orderCancellationItemRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ExchangeRequestRepository exchangeRequestRepository;
    private final ShipmentRepository shipmentRepository;

    private static final Set<OrderCancellationStatus> SHIPPING_BLOCKING_CANCELLATION_STATUSES =
            Set.of(
                    OrderCancellationStatus.REQUESTED,
                    OrderCancellationStatus.PROCESSING
            );

    private static final Set<OrderCancellationStatus> ACTIVE_CANCELLATION_STATUSES = Set.of(
            OrderCancellationStatus.REQUESTED,
            OrderCancellationStatus.PROCESSING
    );
    private static final Set<ReturnRequestStatus> ACTIVE_RETURN_STATUSES = Set.of(
            ReturnRequestStatus.REQUESTED,
            ReturnRequestStatus.APPROVED,
            ReturnRequestStatus.COLLECTING,
            ReturnRequestStatus.RECEIVED,
            ReturnRequestStatus.INSPECTED,
            ReturnRequestStatus.REFUNDING
    );
    private static final Set<ExchangeRequestStatus> ACTIVE_EXCHANGE_STATUSES = Set.of(
            ExchangeRequestStatus.REQUESTED,
            ExchangeRequestStatus.APPROVED,
            ExchangeRequestStatus.PAYMENT_PENDING,
            ExchangeRequestStatus.COLLECTING,
            ExchangeRequestStatus.RECEIVED,
            ExchangeRequestStatus.INSPECTED,
            ExchangeRequestStatus.RESHIPPING
    );

    @Transactional(readOnly = true)
    public SellerOrderPageResponse getSellerOrders(
            Long userId,
            SellerOrderStatus status,
            String keyword,
            int page,
            int size
    ) {
        Seller seller = getActiveSeller(userId);
        validateListRequest(status, page, size);
        String normalizedKeyword = normalizeKeyword(keyword);

        Page<SellerOrder> sellerOrderPage = sellerOrderRepository.findSellerOrders(
                seller.getId(),
                SellerOrderStatus.PENDING_PAYMENT,
                status,
                normalizedKeyword,
                PageRequest.of(
                        page,
                        size,
                        Sort.by(Sort.Direction.DESC, "createdAt")
                )
        );

        List<Long> sellerOrderIds = sellerOrderPage.getContent().stream()
                .map(SellerOrder::getId)
                .toList();
        Map<Long, SellerOrderItemSummaryProjection> summaries = sellerOrderIds.isEmpty()
                ? Map.of()
                : orderItemRepository.summarizeBySellerOrderIds(sellerOrderIds)
                        .stream()
                        .collect(Collectors.toMap(
                                SellerOrderItemSummaryProjection::getSellerOrderId,
                                summary -> summary
                        ));
        Map<Long, Shipment> originalShipments = getOriginalShipments(sellerOrderIds);

        List<SellerOrderListItemResponse> orders = sellerOrderPage.getContent()
                .stream()
                .map(sellerOrder -> {
                    SellerOrderItemSummaryProjection summary =
                            summaries.get(sellerOrder.getId());
                    if (summary == null) {
                        throw new SellerException("판매자 주문 상품 정보를 확인할 수 없습니다.");
                    }
                    return SellerOrderListItemResponse.from(
                            sellerOrder,
                            originalShipments.get(sellerOrder.getId()),
                            summary
                    );
                })
                .toList();

        return new SellerOrderPageResponse(
                orders,
                sellerOrderPage.getNumber(),
                sellerOrderPage.getSize(),
                sellerOrderPage.getTotalElements(),
                sellerOrderPage.getTotalPages(),
                sellerOrderPage.isFirst(),
                sellerOrderPage.isLast()
        );
    }

    @Transactional(readOnly = true)
    public SellerOrderDetailResponse getSellerOrder(
            Long userId,
            Long sellerOrderId
    ) {
        Seller seller = getActiveSeller(userId);
        SellerOrder sellerOrder = sellerOrderRepository
                .findByIdAndSellerId(sellerOrderId, seller.getId())
                .filter(value -> value.getStatus() != SellerOrderStatus.PENDING_PAYMENT)
                .orElseThrow(this::sellerOrderNotFound);

        return detail(sellerOrder);
    }

    @Transactional
    public SellerOrderDetailResponse prepare(
            Long userId,
            Long sellerOrderId
    ) {
        return transition(
                userId,
                sellerOrderId,
                sellerOrder -> sellerOrder.prepare(LocalDateTime.now())
        );
    }

    @Transactional(readOnly = true)
    public SellerOrderCancelValidationResponse validateCancel(
            Long userId,
            Long sellerOrderId,
            SellerOrderCancelRequest request
    ) {
        validateCancelReason(request);
        Seller seller = getActiveSellerForCancellation(userId);
        SellerOrder sellerOrder = sellerOrderRepository
                .findByIdAndSellerId(sellerOrderId, seller.getId())
                .orElseThrow(this::sellerOrderNotFound);

        if (sellerOrder.getStatus() != SellerOrderStatus.PAID
                && sellerOrder.getStatus() != SellerOrderStatus.PREPARING) {
            throw new SellerException("결제 완료 또는 상품 준비 중인 주문만 취소 요청할 수 있습니다.");
        }

        return SellerOrderCancelValidationResponse.validated(
                sellerOrder.getId(), sellerOrder.getStatus()
        );
    }

    @Transactional
    public OrderCancellationResponse createCancel(
            Long userId,
            Long sellerOrderId,
            SellerOrderCancelRequest request
    ) {
        return createCancelForExecution(userId, sellerOrderId, request).cancellation();
    }

    @Transactional
    public SellerOrderCancellationCreateResult createCancelForExecution(
            Long userId,
            Long sellerOrderId,
            SellerOrderCancelRequest request
    ) {
        validateCancelReason(request);
        String clientRequestKey = normalizeClientRequestKey(request.clientRequestKey());
        String reason = request.cancelReason().trim();
        Seller seller = getActiveSellerForCancellation(userId);

        Optional<OrderCancellationResponse> existing = findExistingSellerCancellation(
                seller, sellerOrderId, clientRequestKey, reason
        );
        if (existing.isPresent()) {
            return new SellerOrderCancellationCreateResult(existing.get(), false);
        }

        Long orderId = sellerOrderRepository.findOrderIdByIdAndSellerId(sellerOrderId, seller.getId())
                .orElseThrow(this::sellerOrderNotFound);
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(this::sellerOrderNotFound);
        SellerOrder sellerOrder = sellerOrderRepository
                .findByIdAndSellerIdForUpdate(sellerOrderId, seller.getId())
                .orElseThrow(this::sellerOrderNotFound);

        existing = findExistingSellerCancellation(seller, sellerOrderId, clientRequestKey, reason);
        if (existing.isPresent()) {
            return new SellerOrderCancellationCreateResult(existing.get(), false);
        }
        validateCancelableSellerOrderStatus(sellerOrder);
        validateCancellationConflicts(sellerOrderId);

        List<OrderItem> orderItems = orderItemRepository.findAllBySellerOrderIdForUpdate(sellerOrderId);
        if (orderItems.isEmpty()) {
            throw new SellerException("판매자 주문 상품 정보를 확인할 수 없습니다.");
        }
        List<OrderItem> cancellableItems = orderItems.stream()
                .filter(item -> item.getRemainingQuantity() > 0)
                .toList();
        if (cancellableItems.isEmpty()) {
            throw new SellerException("취소 가능한 주문 상품이 없습니다.");
        }

        OrderCancellation cancellation = orderCancellationRepository.saveAndFlush(
                OrderCancellation.createSellerRequested(
                        order, sellerOrder, clientRequestKey, reason, LocalDateTime.now()
                )
        );
        List<OrderCancellationItem> cancellationItems = cancellableItems.stream()
                .map(item -> OrderCancellationItem.create(
                        cancellation, item, item.getRemainingQuantity()
                ))
                .toList();
        orderCancellationItemRepository.saveAll(cancellationItems);

        return new SellerOrderCancellationCreateResult(
                OrderCancellationResponse.from(cancellation, cancellationItems), true
        );
    }

    @Transactional(readOnly = true)
    public OrderCancellationResponse getSellerCancellation(
            Long userId,
            Long sellerOrderId,
            Long cancellationId
    ) {
        Seller seller = getActiveSellerForCancellation(userId);
        OrderCancellation cancellation = orderCancellationRepository.findById(cancellationId)
                .orElseThrow(this::sellerOrderNotFound);
        if (cancellation.getRequesterType()
                != com.giftmarket.order.entity.OrderCancellationRequesterType.SELLER
                || !cancellation.getSellerOrder().getId().equals(sellerOrderId)
                || !cancellation.getSellerOrder().getSeller().getId().equals(seller.getId())) {
            throw sellerOrderNotFound();
        }
        return OrderCancellationResponse.from(
                cancellation,
                orderCancellationItemRepository
                        .findAllByOrderCancellationIdOrderByIdAsc(cancellationId)
        );
    }

    @Transactional
    public SellerOrderDetailResponse ship(
            Long userId,
            Long sellerOrderId,
            SellerOrderShipRequest request
    ) {
        String shippingCompany = request.shippingCompany().trim();
        String trackingNumber = request.trackingNumber().trim();
        return transition(
                userId,
                sellerOrderId,
                sellerOrder -> {
                    if (orderCancellationRepository
                            .existsBySellerOrderIdAndStatusIn(
                                    sellerOrder.getId(),
                                    SHIPPING_BLOCKING_CANCELLATION_STATUSES
                            )) {
                        throw new SellerException(
                                "처리 중인 취소 요청이 있어 배송을 시작할 수 없습니다."
                        );
                    }
                    if (shipmentRepository.existsBySellerOrderIdAndType(
                            sellerOrder.getId(), ShipmentType.ORIGINAL_OUTBOUND)) {
                        throw new SellerException("이미 최초 배송 송장이 등록되었습니다.");
                    }
                    LocalDateTime shippedAt = LocalDateTime.now();
                    Shipment shipment = Shipment.createShipped(
                            sellerOrder,
                            ShipmentType.ORIGINAL_OUTBOUND,
                            shippingCompany,
                            trackingNumber,
                            shippedAt
                    );
                    shipmentRepository.save(shipment);
                    sellerOrder.markShipped(shipment.getShippedAt());
                    synchronizeLegacyShippingSnapshot(sellerOrder, shipment);
                }
        );
    }

    @Transactional
    public SellerOrderDetailResponse deliver(
            Long userId,
            Long sellerOrderId
    ) {
        return transition(
                userId,
                sellerOrderId,
                sellerOrder -> {
                    Shipment shipment = shipmentRepository
                            .findBySellerOrderIdAndType(
                                    sellerOrder.getId(),
                                    ShipmentType.ORIGINAL_OUTBOUND
                            )
                            .orElseGet(() -> createLegacyOriginalShipment(sellerOrder));
                    LocalDateTime deliveredAt = LocalDateTime.now();
                    shipment.deliver(deliveredAt);
                    sellerOrder.markDelivered(shipment.getDeliveredAt());
                    synchronizeLegacyShippingSnapshot(sellerOrder, shipment);
                }
        );
    }

    private Shipment createLegacyOriginalShipment(SellerOrder sellerOrder) {
        if (sellerOrder.getShippingCompany() == null
                || sellerOrder.getTrackingNumber() == null
                || sellerOrder.getShippedAt() == null) {
            throw new SellerException("최초 배송 송장 정보를 찾을 수 없습니다.");
        }
        Shipment shipment = Shipment.createShipped(
                sellerOrder,
                ShipmentType.ORIGINAL_OUTBOUND,
                sellerOrder.getShippingCompany(),
                sellerOrder.getTrackingNumber(),
                sellerOrder.getShippedAt()
        );
        shipmentRepository.save(shipment);
        return shipment;
    }

    private void synchronizeLegacyShippingSnapshot(
            SellerOrder sellerOrder,
            Shipment shipment
    ) {
        sellerOrder.synchronizeLegacyShippingSnapshot(
                shipment.getShippingCompany(),
                shipment.getTrackingNumber(),
                shipment.getShippedAt(),
                shipment.getDeliveredAt()
        );
    }

    private SellerOrderDetailResponse transition(
            Long userId,
            Long sellerOrderId,
            Consumer<SellerOrder> transition
    ) {
        Seller seller = getActiveSeller(userId);
        Long orderId = sellerOrderRepository
                .findOrderIdByIdAndSellerId(sellerOrderId, seller.getId())
                .orElseThrow(this::sellerOrderNotFound);

        orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(this::sellerOrderNotFound);
        SellerOrder sellerOrder = sellerOrderRepository
                .findByIdAndSellerIdForUpdate(sellerOrderId, seller.getId())
                .orElseThrow(this::sellerOrderNotFound);

        try {
            transition.accept(sellerOrder);
        } catch (IllegalStateException exception) {
            throw new SellerException(exception.getMessage());
        }
        return detail(sellerOrder);
    }

    private SellerOrderDetailResponse detail(SellerOrder sellerOrder) {
        List<OrderItem> items = orderItemRepository
                .findAllBySellerOrderIdOrderByIdAsc(sellerOrder.getId());
        if (items.isEmpty()) {
            throw new SellerException("판매자 주문 상품 정보를 확인할 수 없습니다.");
        }
        List<SellerOrderCancellationSummaryResponse> cancellations =
                orderCancellationRepository
                        .findAllBySellerOrderIdOrderByRequestedAtDescIdDesc(
                                sellerOrder.getId()
                        )
                        .stream()
                        .map(SellerOrderCancellationSummaryResponse::from)
                        .toList();
        Shipment originalShipment = shipmentRepository
                .findBySellerOrderIdAndType(
                        sellerOrder.getId(), ShipmentType.ORIGINAL_OUTBOUND
                )
                .orElse(null);
        return SellerOrderDetailResponse.from(
                sellerOrder, originalShipment, items, cancellations
        );
    }

    private Map<Long, Shipment> getOriginalShipments(List<Long> sellerOrderIds) {
        if (sellerOrderIds.isEmpty()) {
            return Map.of();
        }
        return shipmentRepository.findAllBySellerOrderIdInAndType(
                        sellerOrderIds, ShipmentType.ORIGINAL_OUTBOUND
                ).stream()
                .collect(Collectors.toMap(
                        shipment -> shipment.getSellerOrder().getId(),
                        shipment -> shipment
                ));
    }

    private Seller getActiveSeller(Long userId) {
        if (userId == null) {
            throw new AuthenticationException("인증이 필요합니다.");
        }
        Seller seller = sellerRepository.findByUserId(userId)
                .orElseThrow(() -> new SellerException("판매자 정보를 찾을 수 없습니다."));
        if (seller.getStatus() != SellerStatus.ACTIVE
                && seller.getStatus() != SellerStatus.SALES_SUSPENDED) {
            throw new SellerException("활성 상태의 판매자만 주문을 관리할 수 있습니다.");
        }
        return seller;
    }

    private Seller getActiveSellerForCancellation(Long userId) {
        if (userId == null) {
            throw new AuthenticationException("인증이 필요합니다.");
        }
        Seller seller = sellerRepository.findByUserId(userId)
                .orElseThrow(() -> new SellerException("판매자 정보를 찾을 수 없습니다."));
        if (seller.getStatus() != SellerStatus.ACTIVE) {
            throw new SellerException("활성 상태의 판매자만 주문 취소를 요청할 수 있습니다.");
        }
        return seller;
    }

    private Optional<OrderCancellationResponse> findExistingSellerCancellation(
            Seller seller,
            Long sellerOrderId,
            String clientRequestKey,
            String reason
    ) {
        return orderCancellationRepository.findByClientRequestKey(clientRequestKey)
                .map(cancellation -> {
                    if (cancellation.getRequesterType()
                            != com.giftmarket.order.entity.OrderCancellationRequesterType.SELLER
                            || !cancellation.getSellerOrder().getId().equals(sellerOrderId)
                            || !cancellation.getSellerOrder().getSeller().getId().equals(seller.getId())
                            || !cancellation.getReason().equals(reason)) {
                        throw new SellerException("이미 사용된 취소 요청 키입니다.");
                    }
                    List<OrderCancellationItem> items = orderCancellationItemRepository
                            .findAllByOrderCancellationIdOrderByIdAsc(cancellation.getId());
                    return OrderCancellationResponse.from(cancellation, items);
                });
    }

    private void validateCancelableSellerOrderStatus(SellerOrder sellerOrder) {
        if (sellerOrder.getStatus() != SellerOrderStatus.PAID
                && sellerOrder.getStatus() != SellerOrderStatus.PREPARING) {
            throw new SellerException("결제 완료 또는 상품 준비 중인 주문만 취소 요청할 수 있습니다.");
        }
    }

    private void validateCancellationConflicts(Long sellerOrderId) {
        if (orderCancellationRepository.existsBySellerOrderIdAndStatusIn(
                sellerOrderId, ACTIVE_CANCELLATION_STATUSES
        )) {
            throw new SellerException("진행 중인 취소 요청이 있어 새 취소 요청을 생성할 수 없습니다.");
        }
        if (returnRequestRepository.existsBySellerOrderIdAndStatusIn(
                sellerOrderId, ACTIVE_RETURN_STATUSES
        )) {
            throw new SellerException("진행 중인 반품 요청이 있어 취소 요청을 생성할 수 없습니다.");
        }
        if (exchangeRequestRepository.existsBySellerOrderIdAndStatusIn(
                sellerOrderId, ACTIVE_EXCHANGE_STATUSES
        )) {
            throw new SellerException("진행 중인 교환 요청이 있어 취소 요청을 생성할 수 없습니다.");
        }
    }

    private String normalizeClientRequestKey(String clientRequestKey) {
        if (clientRequestKey == null) {
            throw new SellerException("취소 요청 키를 입력해 주세요.");
        }
        String normalized = clientRequestKey.trim();
        try {
            if (!UUID.fromString(normalized).toString().equalsIgnoreCase(normalized)) {
                throw new IllegalArgumentException();
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new SellerException("취소 요청 키는 UUID 형식이어야 합니다.");
        }
    }

    private void validateCancelReason(SellerOrderCancelRequest request) {
        if (request == null || request.cancelReason() == null
                || request.cancelReason().isBlank()) {
            throw new SellerException("취소 사유를 입력해 주세요.");
        }
    }

    private void validateListRequest(
            SellerOrderStatus status,
            int page,
            int size
    ) {
        if (status == SellerOrderStatus.PENDING_PAYMENT) {
            throw new SellerException("결제 대기 주문은 판매자 주문관리에서 조회할 수 없습니다.");
        }
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new SellerException("페이지 정보를 확인해주세요.");
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new SellerException("검색어는 100자 이내로 입력해주세요.");
        }
        return normalized;
    }

    private SellerException sellerOrderNotFound() {
        return new SellerException("판매자 주문 정보를 찾을 수 없습니다.");
    }
}
