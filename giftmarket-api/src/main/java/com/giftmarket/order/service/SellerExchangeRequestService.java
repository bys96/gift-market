package com.giftmarket.order.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.global.storage.service.StorageService;
import com.giftmarket.order.dto.response.ExchangeRequestImageResponse;
import com.giftmarket.order.dto.response.ExchangeRequestResponse;
import com.giftmarket.order.dto.response.SellerExchangeRequestPageResponse;
import com.giftmarket.order.dto.request.SellerExchangeInspectRequest;
import com.giftmarket.order.dto.request.SellerExchangeInspectionItemRequest;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.*;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.exception.SellerException;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.payment.entity.ExchangeShippingPaymentStatus;
import com.giftmarket.payment.repository.ExchangeShippingPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
public class SellerExchangeRequestService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_REASON_LENGTH = 500;
    private static final int PAYMENT_PENDING_HOURS = 24;
    private static final Set<ReturnRequestStatus> RETURN_HOLDING_STATUSES = Set.of(
            ReturnRequestStatus.REQUESTED, ReturnRequestStatus.APPROVED,
            ReturnRequestStatus.COLLECTING, ReturnRequestStatus.RECEIVED,
            ReturnRequestStatus.INSPECTED, ReturnRequestStatus.REFUNDING
    );
    private static final Set<ExchangeRequestStatus> EXCHANGE_HOLDING_STATUSES = Set.of(
            ExchangeRequestStatus.REQUESTED, ExchangeRequestStatus.APPROVED,
            ExchangeRequestStatus.PAYMENT_PENDING, ExchangeRequestStatus.COLLECTING,
            ExchangeRequestStatus.RECEIVED, ExchangeRequestStatus.INSPECTED,
            ExchangeRequestStatus.RESHIPPING
    );

    private final SellerRepository sellerRepository;
    private final OrderRepository orderRepository;
    private final SellerOrderRepository sellerOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ExchangeRequestRepository exchangeRequestRepository;
    private final ExchangeRequestItemRepository exchangeRequestItemRepository;
    private final ExchangeRequestImageRepository exchangeRequestImageRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ShipmentRepository shipmentRepository;
    private final ExchangeShippingPaymentRepository exchangeShippingPaymentRepository;
    private final OrderInventoryService orderInventoryService;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    public SellerExchangeRequestPageResponse getExchanges(
            Long userId, ExchangeRequestStatus status, int page, int size
    ) {
        Seller seller = getActiveSeller(userId);
        validatePage(page, size);
        Page<ExchangeRequest> requests = exchangeRequestRepository.findSellerExchanges(
                seller.getId(), status, PageRequest.of(page, size)
        );
        List<Long> ids = requests.getContent().stream().map(ExchangeRequest::getId).toList();
        Map<Long, List<ExchangeRequestItem>> items = ids.isEmpty() ? Map.of()
                : exchangeRequestItemRepository
                .findAllByExchangeRequestIdInOrderByExchangeRequestIdAscOrderItemIdAsc(ids)
                .stream().collect(Collectors.groupingBy(item -> item.getExchangeRequest().getId()));
        Map<Long, List<ExchangeRequestImage>> images = ids.isEmpty() ? Map.of()
                : exchangeRequestImageRepository
                .findAllByExchangeRequestIdInOrderByExchangeRequestIdAscSortOrderAsc(ids)
                .stream().collect(Collectors.groupingBy(image -> image.getExchangeRequest().getId()));
        return new SellerExchangeRequestPageResponse(
                requests.getContent().stream().map(request -> response(
                        request, items.getOrDefault(request.getId(), List.of()),
                        images.getOrDefault(request.getId(), List.of())
                )).toList(), requests.getNumber(), requests.getSize(), requests.getTotalElements(),
                requests.getTotalPages(), requests.isFirst(), requests.isLast()
        );
    }

    @Transactional(readOnly = true)
    public ExchangeRequestResponse getExchange(Long userId, Long exchangeRequestId) {
        Seller seller = getActiveSeller(userId);
        exchangeRequestRepository.findOwnership(exchangeRequestId, seller.getId())
                .orElseThrow(this::exchangeNotFound);
        ExchangeRequest request = exchangeRequestRepository.findById(exchangeRequestId)
                .orElseThrow(this::exchangeNotFound);
        validateSellerOwnership(request, seller.getId());
        return response(request, getItems(exchangeRequestId));
    }

    @Transactional
    public ExchangeRequestResponse approve(
            Long userId, Long exchangeRequestId, ExchangeResponsibility responsibility
    ) {
        LockedExchange locked = lock(userId, exchangeRequestId);
        ExchangeRequest request = locked.request();
        requireRequested(request);
        try {
            if (request.getReasonType() == ExchangeReasonType.OTHER) {
                if (responsibility == null) {
                    throw new SellerException("기타 교환 사유는 귀책 주체를 선택해야 합니다.");
                }
                request.confirmResponsibility(responsibility);
            } else if (responsibility != null) {
                throw new SellerException("기타 사유가 아닌 교환의 귀책 주체는 변경할 수 없습니다.");
            }

            validateAvailableQuantities(request, locked.items(), locked.lockedOrderItems());
            orderInventoryService.reserveExchangeTargets(locked.items());

            LocalDateTime now = currentTime();
            request.approve(now);
            if (request.getResponsibility() == ExchangeResponsibility.BUYER) {
                request.startPaymentPending(now, now.plusHours(PAYMENT_PENDING_HOURS));
            } else {
                request.startCollectingAfterReservation(now);
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new SellerException(exception.getMessage());
        }
        return response(request, locked.items());
    }

    @Transactional
    public ExchangeRequestResponse reject(Long userId, Long exchangeRequestId, String reason) {
        String normalized = requiredText(reason, MAX_REASON_LENGTH, "교환 거절 사유를 입력해주세요.");
        LockedExchange locked = lock(userId, exchangeRequestId);
        requireRequested(locked.request());
        try {
            locked.request().reject(normalized, currentTime());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new SellerException(exception.getMessage());
        }
        return response(locked.request(), locked.items());
    }

    @Transactional
    public ExchangeRequestResponse collect(
            Long userId, Long exchangeRequestId, String shippingCompany, String trackingNumber
    ) {
        String company = requiredText(shippingCompany, 100, "택배사를 입력해주세요.");
        String tracking = requiredText(trackingNumber, 100, "송장번호를 입력해주세요.");
        LockedExchangeBase locked = lockWorkflow(userId, exchangeRequestId);
        requireStatus(locked.request(), ExchangeRequestStatus.COLLECTING);
        validateReservation(locked.items());
        if (locked.request().getCollectionShipment() != null) {
            throw new SellerException("이미 교환 회수 배송이 시작되었습니다.");
        }
        Shipment shipment = Shipment.createShipped(
                locked.sellerOrder(), ShipmentType.EXCHANGE_COLLECTION, company, tracking, currentTime());
        shipmentRepository.save(shipment);
        try {
            locked.request().assignCollectionShipment(shipment);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new SellerException(exception.getMessage());
        }
        return response(locked.request(), locked.items());
    }

    @Transactional
    public ExchangeRequestResponse receive(Long userId, Long exchangeRequestId) {
        LockedExchangeBase locked = lockWorkflow(userId, exchangeRequestId);
        requireStatus(locked.request(), ExchangeRequestStatus.COLLECTING);
        Shipment shipment = validateCollectionShipment(locked.request(), locked.sellerOrder());
        Shipment lockedShipment = shipmentRepository.findByIdForUpdate(shipment.getId())
                .orElseThrow(this::exchangeNotFound);
        validateCollectionShipment(locked.request(), locked.sellerOrder(), lockedShipment);
        LocalDateTime now = currentTime();
        try {
            lockedShipment.deliver(now);
            locked.request().receive(now);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new SellerException(exception.getMessage());
        }
        return response(locked.request(), locked.items());
    }

    @Transactional
    public ExchangeRequestResponse inspect(
            Long userId, Long exchangeRequestId, SellerExchangeInspectRequest inspectRequest
    ) {
        Map<Long, ExchangeInspectionResult> results = normalizeInspection(inspectRequest);
        LockedExchange locked = lock(userId, exchangeRequestId);
        requireStatus(locked.request(), ExchangeRequestStatus.RECEIVED);
        validateReservation(locked.items());
        validateBuyerPayment(locked.request());
        Map<Long, ExchangeRequestItem> itemsByOrderItemId = locked.items().stream()
                .collect(Collectors.toMap(item -> item.getOrderItem().getId(), item -> item));
        if (!itemsByOrderItemId.keySet().equals(results.keySet())) {
            throw new SellerException("교환 요청의 모든 상품 검수 결과를 한 번에 입력해주세요.");
        }
        try {
            results.forEach((orderItemId, result) -> itemsByOrderItemId.get(orderItemId).inspect(result));
            orderInventoryService.restoreExchangeOriginalItems(locked.items());
            for (ExchangeRequestItem item : locked.items()) {
                if (item.getInspectionResult() == ExchangeInspectionResult.RESTOCKABLE) {
                    item.increaseRestockedQuantity(item.getQuantity());
                }
            }
            locked.request().completeInspection(currentTime());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new SellerException(exception.getMessage());
        }
        return response(locked.request(), locked.items());
    }

    private LockedExchange lock(Long userId, Long exchangeRequestId) {
        LockedExchangeBase base = lockWorkflow(userId, exchangeRequestId);
        Order order = base.order();
        SellerOrder sellerOrder = base.sellerOrder();
        ExchangeRequest request = base.request();
        List<ExchangeRequestItem> items = base.items();
        List<Long> itemIds = items.stream().map(item -> item.getOrderItem().getId()).sorted().toList();
        List<OrderItem> lockedItems = orderItemRepository.findAllByIdInForUpdate(itemIds);
        Map<Long, OrderItem> byId = lockedItems.stream()
                .collect(Collectors.toMap(OrderItem::getId, item -> item));
        if (byId.size() != itemIds.size()) throw new SellerException("교환 요청 상품 정보를 확인할 수 없습니다.");
        for (ExchangeRequestItem item : items) {
            OrderItem lockedItem = byId.get(item.getOrderItem().getId());
            if (lockedItem == null || !lockedItem.getOrder().getId().equals(order.getId())
                    || !lockedItem.getSellerOrder().getId().equals(sellerOrder.getId())) {
                throw new SellerException("교환 요청 상품 정보가 판매자 주문과 일치하지 않습니다.");
            }
        }
        return new LockedExchange(order, sellerOrder, request, items, lockedItems);
    }

    private LockedExchangeBase lockWorkflow(Long userId, Long exchangeRequestId) {
        Seller seller = getActiveSeller(userId);
        ExchangeRequestOwnershipProjection ownership = exchangeRequestRepository
                .findOwnership(exchangeRequestId, seller.getId()).orElseThrow(this::exchangeNotFound);
        Order order = orderRepository.findByIdForUpdate(ownership.getOrderId())
                .orElseThrow(this::exchangeNotFound);
        SellerOrder sellerOrder = sellerOrderRepository.findByIdAndSellerIdForUpdate(
                ownership.getSellerOrderId(), seller.getId()).orElseThrow(this::exchangeNotFound);
        ExchangeRequest request = exchangeRequestRepository.findByIdForUpdate(exchangeRequestId)
                .orElseThrow(this::exchangeNotFound);
        if (!request.getOrder().getId().equals(order.getId())
                || !request.getSellerOrder().getId().equals(sellerOrder.getId())) {
            throw exchangeNotFound();
        }
        List<ExchangeRequestItem> items = getItems(exchangeRequestId);
        if (items.isEmpty()) throw new SellerException("교환 요청 상품 정보를 확인할 수 없습니다.");
        for (ExchangeRequestItem item : items) {
            if (!item.getOrderItem().getOrder().getId().equals(order.getId())
                    || !item.getOrderItem().getSellerOrder().getId().equals(sellerOrder.getId())) {
                throw new SellerException("교환 요청 상품 정보가 판매자 주문과 일치하지 않습니다.");
            }
        }
        return new LockedExchangeBase(order, sellerOrder, request, items);
    }

    private void validateAvailableQuantities(
            ExchangeRequest request, List<ExchangeRequestItem> requestItems, List<OrderItem> lockedItems
    ) {
        List<Long> ids = lockedItems.stream().map(OrderItem::getId).toList();
        Map<Long, Long> returnHeld = returnRequestRepository
                .sumItemQuantitiesByStatuses(ids, RETURN_HOLDING_STATUSES).stream()
                .collect(Collectors.toMap(PendingReturnQuantityProjection::getOrderItemId,
                        PendingReturnQuantityProjection::getPendingQuantity));
        Map<Long, Long> otherExchangeHeld = exchangeRequestRepository
                .sumItemQuantitiesByStatusesExcludingRequest(
                        ids, request.getId(), EXCHANGE_HOLDING_STATUSES
                ).stream().collect(Collectors.toMap(PendingExchangeQuantityProjection::getOrderItemId,
                        PendingExchangeQuantityProjection::getPendingQuantity));
        Map<Long, ExchangeRequestItem> requestByOrderItem = requestItems.stream()
                .collect(Collectors.toMap(item -> item.getOrderItem().getId(), item -> item));
        for (OrderItem item : lockedItems) {
            long available = (long) item.getQuantity() - item.getCanceledQuantity()
                    - item.getReturnedQuantity() - item.getExchangedQuantity()
                    - returnHeld.getOrDefault(item.getId(), 0L)
                    - otherExchangeHeld.getOrDefault(item.getId(), 0L);
            ExchangeRequestItem requested = requestByOrderItem.get(item.getId());
            if (requested == null || requested.getQuantity() > available) {
                throw new SellerException("현재 교환 가능 수량이 부족하여 승인할 수 없습니다.");
            }
        }
    }

    private List<ExchangeRequestItem> getItems(Long id) {
        return exchangeRequestItemRepository.findAllByExchangeRequestIdOrderByOrderItemIdAsc(id);
    }

    private ExchangeRequestResponse response(ExchangeRequest request, List<ExchangeRequestItem> items) {
        return response(request, items,
                exchangeRequestImageRepository.findAllByExchangeRequestIdOrderBySortOrderAsc(request.getId()));
    }

    private ExchangeRequestResponse response(
            ExchangeRequest request, List<ExchangeRequestItem> items, List<ExchangeRequestImage> images
    ) {
        if (items.isEmpty()) throw new SellerException("교환 요청 상품 정보를 확인할 수 없습니다.");
        return ExchangeRequestResponse.from(request, items, images.stream()
                .map(image -> new ExchangeRequestImageResponse(
                        image.getId(), storageService.createReadUrl(image.getObjectKey()), image.getSortOrder()
                )).toList());
    }

    private Seller getActiveSeller(Long userId) {
        if (userId == null) throw new AuthenticationException("인증이 필요합니다.");
        Seller seller = sellerRepository.findByUserId(userId).orElseThrow(this::exchangeNotFound);
        if (seller.getStatus() != SellerStatus.ACTIVE) {
            throw new SellerException("활성 상태의 판매자만 교환 요청을 처리할 수 있습니다.");
        }
        return seller;
    }

    private void validateSellerOwnership(ExchangeRequest request, Long sellerId) {
        if (!request.getSellerOrder().getSeller().getId().equals(sellerId)) throw exchangeNotFound();
    }

    private void requireRequested(ExchangeRequest request) {
        if (request.getStatus() != ExchangeRequestStatus.REQUESTED) {
            throw new SellerException("현재 교환 상태에서는 요청을 처리할 수 없습니다.");
        }
    }

    private void requireStatus(ExchangeRequest request, ExchangeRequestStatus expected) {
        if (request.getStatus() != expected) {
            throw new SellerException("현재 교환 상태에서는 요청을 처리할 수 없습니다.");
        }
    }

    private void validateReservation(List<ExchangeRequestItem> items) {
        for (ExchangeRequestItem item : items) {
            if (item.getReservedQuantity() != item.getQuantity()
                    || item.getReleasedQuantity() != 0 || item.getConsumedQuantity() != 0
                    || item.getEffectiveReservedQuantity() != item.getQuantity()) {
                throw new SellerException("교환 target 재고 예약 상태를 확인해주세요.");
            }
        }
    }

    private Shipment validateCollectionShipment(ExchangeRequest request, SellerOrder sellerOrder) {
        Shipment shipment = request.getCollectionShipment();
        if (shipment == null) throw new SellerException("교환 회수 배송 정보를 찾을 수 없습니다.");
        return validateCollectionShipment(request, sellerOrder, shipment);
    }

    private Shipment validateCollectionShipment(
            ExchangeRequest request, SellerOrder sellerOrder, Shipment shipment
    ) {
        if (!shipment.getId().equals(request.getCollectionShipment().getId())
                || !shipment.getSellerOrder().getId().equals(sellerOrder.getId())
                || shipment.getType() != ShipmentType.EXCHANGE_COLLECTION
                || shipment.getStatus() != ShipmentStatus.SHIPPED) {
            throw new SellerException("교환 회수 배송 상태를 확인해주세요.");
        }
        return shipment;
    }

    private Map<Long, ExchangeInspectionResult> normalizeInspection(SellerExchangeInspectRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new SellerException("교환 검수 결과를 입력해주세요.");
        }
        Map<Long, ExchangeInspectionResult> results = new LinkedHashMap<>();
        for (SellerExchangeInspectionItemRequest item : request.items()) {
            if (item == null || item.orderItemId() == null || item.inspectionResult() == null) {
                throw new SellerException("교환 검수 결과를 확인해주세요.");
            }
            if (results.putIfAbsent(item.orderItemId(), item.inspectionResult()) != null) {
                throw new SellerException("중복된 교환 상품 검수 결과가 있습니다.");
            }
        }
        return results;
    }

    private void validateBuyerPayment(ExchangeRequest request) {
        if (request.getResponsibility() != ExchangeResponsibility.BUYER) return;
        if (exchangeShippingPaymentRepository.findByExchangeRequestId(request.getId())
                .filter(payment -> payment.getStatus() == ExchangeShippingPaymentStatus.SUCCEEDED)
                .isEmpty()) {
            throw new SellerException("구매자 귀책 교환 배송비 결제 상태를 확인해주세요.");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) throw new SellerException("페이지 정보를 확인해주세요.");
    }

    private String requiredText(String value, int maxLength, String message) {
        if (value == null || value.isBlank()) throw new SellerException(message);
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new SellerException(message);
        return normalized;
    }

    private SellerException exchangeNotFound() {
        return new SellerException("교환 요청 정보를 찾을 수 없습니다.");
    }

    LocalDateTime currentTime() { return LocalDateTime.now(); }

    private record LockedExchange(
            Order order, SellerOrder sellerOrder, ExchangeRequest request,
            List<ExchangeRequestItem> items, List<OrderItem> lockedOrderItems
    ) { }

    private record LockedExchangeBase(
            Order order, SellerOrder sellerOrder, ExchangeRequest request,
            List<ExchangeRequestItem> items
    ) { }
}
