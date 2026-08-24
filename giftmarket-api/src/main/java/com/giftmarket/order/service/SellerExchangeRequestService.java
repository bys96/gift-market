package com.giftmarket.order.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.global.storage.service.StorageService;
import com.giftmarket.order.dto.response.ExchangeRequestImageResponse;
import com.giftmarket.order.dto.response.ExchangeRequestResponse;
import com.giftmarket.order.dto.response.SellerExchangeRequestPageResponse;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.*;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.exception.SellerException;
import com.giftmarket.seller.repository.SellerRepository;
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

    private LockedExchange lock(Long userId, Long exchangeRequestId) {
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
}
