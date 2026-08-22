package com.giftmarket.order.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.global.storage.service.StorageService;
import com.giftmarket.order.dto.request.ReturnRequestCreateRequest;
import com.giftmarket.order.dto.request.ReturnRequestItemRequest;
import com.giftmarket.order.dto.response.ReturnRequestResponse;
import com.giftmarket.order.dto.response.ReturnRequestImageResponse;
import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.ReturnRequest;
import com.giftmarket.order.entity.ReturnRequestItem;
import com.giftmarket.order.entity.ReturnRequestImage;
import com.giftmarket.order.entity.ReturnRequestStatus;
import com.giftmarket.order.entity.ReturnResponsibility;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.entity.Shipment;
import com.giftmarket.order.entity.ShipmentType;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.order.repository.PendingReturnQuantityProjection;
import com.giftmarket.order.repository.ReturnRequestItemRepository;
import com.giftmarket.order.repository.ReturnRequestImageRepository;
import com.giftmarket.order.repository.ReturnRequestRepository;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.order.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
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
public class ReturnRequestService {

    private static final Set<ReturnRequestStatus> QUANTITY_HOLDING_STATUSES = Set.of(
            ReturnRequestStatus.REQUESTED,
            ReturnRequestStatus.APPROVED,
            ReturnRequestStatus.COLLECTING,
            ReturnRequestStatus.RECEIVED,
            ReturnRequestStatus.INSPECTED,
            ReturnRequestStatus.REFUNDING
    );
    private static final int BUYER_RETURN_DAYS = 7;
    private static final int MAX_REASON_LENGTH = 500;
    private static final int MAX_ITEM_COUNT = 100;
    private static final int MAX_IMAGE_COUNT = 5;

    private final OrderRepository orderRepository;
    private final SellerOrderRepository sellerOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShipmentRepository shipmentRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnRequestItemRepository returnRequestItemRepository;
    private final ReturnRequestImageRepository returnRequestImageRepository;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    public ReturnRequestResponse getOwned(Long userId, Long returnRequestId) {
        validateAuthenticated(userId);
        ReturnRequest returnRequest = returnRequestRepository.findById(returnRequestId)
                .orElseThrow(this::orderNotFound);
        if (!returnRequest.getOrder().getUser().getId().equals(userId)) {
            throw orderNotFound();
        }
        return toResponse(returnRequest);
    }

    @Transactional(readOnly = true)
    public List<ReturnRequestResponse> getAllOwned(Long userId, Long orderId) {
        validateAuthenticated(userId);
        orderRepository.findByIdAndUserId(orderId, userId).orElseThrow(this::orderNotFound);
        List<ReturnRequest> requests = returnRequestRepository
                .findAllByOrderIdOrderByRequestedAtDescIdDesc(orderId);
        if (requests.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ReturnRequestItem>> itemsByRequestId = returnRequestItemRepository
                .findAllByReturnRequestIdInOrderByReturnRequestIdAscOrderItemIdAsc(
                        requests.stream().map(ReturnRequest::getId).toList()
                )
                .stream()
                .collect(Collectors.groupingBy(item -> item.getReturnRequest().getId()));
        Map<Long, List<ReturnRequestImage>> imagesByRequestId = returnRequestImageRepository
                .findAllByReturnRequestIdInOrderByReturnRequestIdAscSortOrderAsc(
                        requests.stream().map(ReturnRequest::getId).toList()
                ).stream().collect(Collectors.groupingBy(image -> image.getReturnRequest().getId()));
        return requests.stream()
                .map(request -> ReturnRequestResponse.from(
                        request,
                        itemsByRequestId.getOrDefault(request.getId(), List.of()),
                        imageResponses(imagesByRequestId.getOrDefault(request.getId(), List.of()))
                ))
                .toList();
    }

    @Transactional
    public ReturnRequestResponse create(
            Long userId,
            Long orderId,
            Long sellerOrderId,
            ReturnRequestCreateRequest request
    ) {
        validateAuthenticated(userId);
        NormalizedRequest normalized = normalize(request);
        validateImageOwnership(userId, normalized.imageObjectKeys());

        Optional<ReturnRequestResponse> existing = findExisting(
                userId, orderId, sellerOrderId, normalized
        );
        if (existing.isPresent()) {
            return existing.get();
        }

        Order order = orderRepository.findByIdAndUserIdForUpdate(orderId, userId)
                .orElseThrow(this::orderNotFound);

        existing = findExisting(userId, orderId, sellerOrderId, normalized);
        if (existing.isPresent()) {
            return existing.get();
        }

        SellerOrder sellerOrder = sellerOrderRepository
                .findByIdAndOrderIdForUpdate(sellerOrderId, orderId)
                .orElseThrow(this::orderNotFound);
        validateDelivered(sellerOrder);

        List<Long> orderItemIds = normalized.quantities().keySet().stream().sorted().toList();
        List<OrderItem> orderItems = orderItemRepository.findAllByIdInForUpdate(orderItemIds);
        validateOrderItems(order, sellerOrder, orderItems, orderItemIds);

        Shipment outbound = shipmentRepository.findBySellerOrderIdAndType(
                        sellerOrderId, ShipmentType.ORIGINAL_OUTBOUND
                )
                .orElseThrow(() -> new OrderException("배송완료 시각을 확인할 수 없어 반품을 요청할 수 없습니다."));
        validateReturnPeriod(normalized.reasonType().defaultResponsibility(), outbound.getDeliveredAt());

        Map<Long, Long> heldQuantities = heldQuantities(orderItemIds);
        validateAvailableQuantities(orderItems, normalized.quantities(), heldQuantities);

        ReturnRequest returnRequest;
        try {
            returnRequest = returnRequestRepository.saveAndFlush(ReturnRequest.createRequested(
                    order, sellerOrder, normalized.clientRequestKey(), normalized.reasonType(),
                    normalized.reason(), normalized.collectionRecipientName(),
                    normalized.collectionPhone(), normalized.collectionPostalCode(),
                    normalized.collectionAddress(), normalized.collectionAddressDetail(),
                    currentTime()
            ));
        } catch (DataIntegrityViolationException exception) {
            throw new OrderException("이미 사용된 반품 요청 키입니다.");
        }

        Map<Long, OrderItem> orderItemsById = orderItems.stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));
        List<ReturnRequestItem> returnItems = orderItemIds.stream()
                .map(id -> ReturnRequestItem.create(
                        returnRequest, orderItemsById.get(id), normalized.quantities().get(id)
                ))
                .toList();
        returnRequestItemRepository.saveAll(returnItems);
        List<ReturnRequestImage> returnImages = new java.util.ArrayList<>();
        for (int index = 0; index < normalized.imageObjectKeys().size(); index++) {
            returnImages.add(ReturnRequestImage.create(
                    returnRequest, normalized.imageObjectKeys().get(index), index
            ));
        }
        if (!returnImages.isEmpty()) returnRequestImageRepository.saveAll(returnImages);
        return ReturnRequestResponse.from(returnRequest, returnItems, imageResponses(returnImages));
    }

    private ReturnRequestResponse toResponse(ReturnRequest request) {
        return ReturnRequestResponse.from(
                request,
                returnRequestItemRepository.findAllByReturnRequestIdOrderByIdAsc(request.getId()),
                imageResponses(returnRequestImageRepository
                        .findAllByReturnRequestIdOrderBySortOrderAsc(request.getId()))
        );
    }

    private Optional<ReturnRequestResponse> findExisting(
            Long userId,
            Long orderId,
            Long sellerOrderId,
            NormalizedRequest requested
    ) {
        return returnRequestRepository.findByClientRequestKey(requested.clientRequestKey())
                .map(existing -> {
                    if (!existing.getOrder().getUser().getId().equals(userId)) {
                        throw new OrderException("이미 사용된 반품 요청 키입니다.");
                    }
                    List<ReturnRequestItem> items = returnRequestItemRepository
                            .findAllByReturnRequestIdOrderByIdAsc(existing.getId());
                    Map<Long, Integer> quantities = items.stream().collect(Collectors.toMap(
                            item -> item.getOrderItem().getId(), ReturnRequestItem::getQuantity
                    ));
                    List<ReturnRequestImage> images = returnRequestImageRepository
                            .findAllByReturnRequestIdOrderBySortOrderAsc(existing.getId());
                    List<String> imageObjectKeys = images.stream()
                            .map(ReturnRequestImage::getObjectKey).toList();
                    if (!existing.getOrder().getId().equals(orderId)
                            || !existing.getSellerOrder().getId().equals(sellerOrderId)
                            || existing.getReasonType() != requested.reasonType()
                            || !existing.getReason().equals(requested.reason())
                            || !existing.getCollectionRecipientName().equals(requested.collectionRecipientName())
                            || !existing.getCollectionPhone().equals(requested.collectionPhone())
                            || !existing.getCollectionPostalCode().equals(requested.collectionPostalCode())
                            || !existing.getCollectionAddress().equals(requested.collectionAddress())
                            || !java.util.Objects.equals(existing.getCollectionAddressDetail(), requested.collectionAddressDetail())
                            || !quantities.equals(requested.quantities())
                            || !imageObjectKeys.equals(requested.imageObjectKeys())) {
                        throw new OrderException("반품 요청 키가 최초 요청과 다른 내용으로 재사용되었습니다.");
                    }
                    return ReturnRequestResponse.from(existing, items, imageResponses(images));
                });
    }

    private void validateDelivered(SellerOrder sellerOrder) {
        if (sellerOrder.getStatus() != SellerOrderStatus.DELIVERED) {
            throw new OrderException("배송완료된 판매자 주문만 반품을 요청할 수 있습니다.");
        }
    }

    private void validateReturnPeriod(
            ReturnResponsibility responsibility,
            LocalDateTime deliveredAt
    ) {
        if (deliveredAt == null) {
            throw new OrderException("배송완료 시각을 확인할 수 없어 반품을 요청할 수 없습니다.");
        }
        if (responsibility == ReturnResponsibility.BUYER
                && currentTime().isAfter(deliveredAt.plusDays(BUYER_RETURN_DAYS))) {
            throw new OrderException("구매자 귀책 반품 가능 기간이 지났습니다.");
        }
        // SELLER와 OTHER의 기간은 운영 정책이 확정될 때 별도 정책으로 제한한다.
    }

    private void validateOrderItems(
            Order order,
            SellerOrder sellerOrder,
            List<OrderItem> orderItems,
            List<Long> requestedIds
    ) {
        if (orderItems.size() != requestedIds.size()) {
            throw new OrderException("반품할 주문 상품 정보를 확인할 수 없습니다.");
        }
        for (OrderItem item : orderItems) {
            if (!item.getOrder().getId().equals(order.getId())
                    || !item.getSellerOrder().getId().equals(sellerOrder.getId())) {
                throw new OrderException("같은 판매자 주문의 상품만 함께 반품할 수 있습니다.");
            }
        }
    }

    private Map<Long, Long> heldQuantities(Collection<Long> orderItemIds) {
        return returnRequestRepository.sumItemQuantitiesByStatuses(
                        orderItemIds, QUANTITY_HOLDING_STATUSES
                )
                .stream()
                .collect(Collectors.toMap(
                        PendingReturnQuantityProjection::getOrderItemId,
                        PendingReturnQuantityProjection::getPendingQuantity
                ));
    }

    private void validateAvailableQuantities(
            List<OrderItem> items,
            Map<Long, Integer> requested,
            Map<Long, Long> held
    ) {
        for (OrderItem item : items) {
            long available = (long) item.getQuantity()
                    - item.getCanceledQuantity()
                    - item.getReturnedQuantity()
                    - held.getOrDefault(item.getId(), 0L);
            if (requested.get(item.getId()) > available) {
                throw new OrderException("활성 반품 요청을 포함한 반품 가능 수량을 초과했습니다.");
            }
        }
        // Exchange 도입 시 활성 교환 요청 수량도 같은 transaction 안에서 차감한다.
    }

    private NormalizedRequest normalize(ReturnRequestCreateRequest request) {
        if (request == null) {
            throw new OrderException("반품 요청 정보를 확인해주세요.");
        }
        String key = normalizeRequestKey(request.clientRequestKey());
        String reason = requiredText(request.reason(), MAX_REASON_LENGTH, "반품 사유를 입력해주세요.");
        if (request.reasonType() == null) {
            throw new OrderException("반품 사유 유형을 선택해주세요.");
        }
        Map<Long, Integer> quantities = normalizeItems(request.items());
        List<String> imageObjectKeys = normalizeImageObjectKeys(request.imageObjectKeys());
        return new NormalizedRequest(
                key, request.reasonType(), reason,
                requiredText(request.collectionRecipientName(), 100, "회수 수령인 이름을 입력해주세요."),
                requiredText(request.collectionPhone(), 30, "회수 연락처를 입력해주세요."),
                requiredText(request.collectionPostalCode(), 20, "회수 우편번호를 입력해주세요."),
                requiredText(request.collectionAddress(), 255, "회수 주소를 입력해주세요."),
                nullableText(request.collectionAddressDetail(), 255), quantities, imageObjectKeys
        );
    }

    private List<String> normalizeImageObjectKeys(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        if (values.size() > MAX_IMAGE_COUNT) {
            throw new OrderException("반품 증빙 이미지는 최대 5장까지 첨부할 수 있습니다.");
        }
        List<String> normalized = values.stream()
                .map(value -> requiredText(value, 500, "반품 이미지 정보를 확인해주세요."))
                .toList();
        if (Set.copyOf(normalized).size() != normalized.size()) {
            throw new OrderException("같은 반품 이미지를 중복 첨부할 수 없습니다.");
        }
        return normalized;
    }

    private void validateImageOwnership(Long userId, List<String> objectKeys) {
        String requiredPrefix = "returns/" + userId + "/";
        if (objectKeys.stream().anyMatch(key -> !key.startsWith(requiredPrefix))) {
            throw new OrderException("반품 요청에 사용할 수 없는 이미지입니다.");
        }
    }

    private List<ReturnRequestImageResponse> imageResponses(List<ReturnRequestImage> images) {
        return images.stream().map(image -> new ReturnRequestImageResponse(
                image.getId(), storageService.createReadUrl(image.getObjectKey()), image.getSortOrder()
        )).toList();
    }

    private Map<Long, Integer> normalizeItems(List<ReturnRequestItemRequest> items) {
        if (items == null || items.isEmpty() || items.size() > MAX_ITEM_COUNT) {
            throw new OrderException("반품할 주문 상품을 1개 이상 100개 이하로 선택해주세요.");
        }
        Map<Long, Integer> normalized = new LinkedHashMap<>();
        for (ReturnRequestItemRequest item : items) {
            if (item == null || item.orderItemId() == null || item.orderItemId() <= 0
                    || item.quantity() == null || item.quantity() <= 0) {
                throw new OrderException("반품 상품과 수량을 다시 확인해주세요.");
            }
            if (normalized.putIfAbsent(item.orderItemId(), item.quantity()) != null) {
                throw new OrderException("같은 주문 상품이 반품 요청에 중복되었습니다.");
            }
        }
        return normalized;
    }

    private String normalizeRequestKey(String value) {
        String normalized = requiredText(value, 100, "반품 요청 키가 필요합니다.");
        try {
            if (!UUID.fromString(normalized).toString().equalsIgnoreCase(normalized)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new OrderException("반품 요청 키는 UUID 형식이어야 합니다.");
        }
        return normalized;
    }

    private String requiredText(String value, int maxLength, String message) {
        if (value == null || value.isBlank()) {
            throw new OrderException(message);
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new OrderException(message);
        }
        return normalized;
    }

    private String nullableText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new OrderException("회수 상세 주소를 확인해주세요.");
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

    LocalDateTime currentTime() {
        return LocalDateTime.now();
    }

    private record NormalizedRequest(
            String clientRequestKey,
            com.giftmarket.order.entity.ReturnReasonType reasonType,
            String reason,
            String collectionRecipientName,
            String collectionPhone,
            String collectionPostalCode,
            String collectionAddress,
            String collectionAddressDetail,
            Map<Long, Integer> quantities,
            List<String> imageObjectKeys
    ) {
    }
}
