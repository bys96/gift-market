package com.giftmarket.order.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.global.storage.service.StorageService;
import com.giftmarket.order.dto.request.ExchangeRequestCreateRequest;
import com.giftmarket.order.dto.request.ExchangeRequestItemRequest;
import com.giftmarket.order.dto.response.ExchangeRequestImageResponse;
import com.giftmarket.order.dto.response.ExchangeRequestResponse;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.order.repository.*;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductOptionValue;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.product.entity.ProductVariant;
import com.giftmarket.product.entity.ProductVariantOptionValue;
import com.giftmarket.product.repository.ProductVariantOptionValueRepository;
import com.giftmarket.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExchangeRequestService {

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
    private static final int BUYER_EXCHANGE_DAYS = 7;
    private static final int MAX_ITEM_COUNT = 100;
    private static final int MAX_IMAGE_COUNT = 5;

    private final OrderRepository orderRepository;
    private final SellerOrderRepository sellerOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShipmentRepository shipmentRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ExchangeRequestRepository exchangeRequestRepository;
    private final ExchangeRequestItemRepository exchangeRequestItemRepository;
    private final ExchangeRequestImageRepository exchangeRequestImageRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductVariantOptionValueRepository productVariantOptionValueRepository;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    public ExchangeRequestResponse getOwned(Long userId, Long exchangeRequestId) {
        validateAuthenticated(userId);
        ExchangeRequest request = exchangeRequestRepository.findById(exchangeRequestId)
                .orElseThrow(this::orderNotFound);
        if (!request.getOrder().getUser().getId().equals(userId)) throw orderNotFound();
        return toResponse(request);
    }

    @Transactional(readOnly = true)
    public List<ExchangeRequestResponse> getAllOwned(Long userId, Long orderId) {
        validateAuthenticated(userId);
        orderRepository.findByIdAndUserId(orderId, userId).orElseThrow(this::orderNotFound);
        List<ExchangeRequest> requests = exchangeRequestRepository
                .findAllByOrderIdOrderByRequestedAtDescIdDesc(orderId);
        if (requests.isEmpty()) return List.of();
        List<Long> ids = requests.stream().map(ExchangeRequest::getId).toList();
        Map<Long, List<ExchangeRequestItem>> items = exchangeRequestItemRepository
                .findAllByExchangeRequestIdInOrderByExchangeRequestIdAscOrderItemIdAsc(ids)
                .stream().collect(Collectors.groupingBy(item -> item.getExchangeRequest().getId()));
        Map<Long, List<ExchangeRequestImage>> images = exchangeRequestImageRepository
                .findAllByExchangeRequestIdInOrderByExchangeRequestIdAscSortOrderAsc(ids)
                .stream().collect(Collectors.groupingBy(image -> image.getExchangeRequest().getId()));
        return requests.stream().map(request -> ExchangeRequestResponse.from(
                request, items.getOrDefault(request.getId(), List.of()),
                imageResponses(images.getOrDefault(request.getId(), List.of()))
        )).toList();
    }

    @Transactional
    public ExchangeRequestResponse create(
            Long userId, Long orderId, Long sellerOrderId, ExchangeRequestCreateRequest request
    ) {
        validateAuthenticated(userId);
        NormalizedRequest normalized = normalize(request);
        validateImageOwnership(userId, normalized.imageObjectKeys());

        Optional<ExchangeRequestResponse> existing = findExisting(userId, orderId, sellerOrderId, normalized);
        if (existing.isPresent()) return existing.get();

        Order order = orderRepository.findByIdAndUserIdForUpdate(orderId, userId)
                .orElseThrow(this::orderNotFound);
        existing = findExisting(userId, orderId, sellerOrderId, normalized);
        if (existing.isPresent()) return existing.get();

        SellerOrder sellerOrder = sellerOrderRepository.findByIdAndOrderIdForUpdate(sellerOrderId, orderId)
                .orElseThrow(this::orderNotFound);
        if (sellerOrder.getStatus() != SellerOrderStatus.DELIVERED) {
            throw new OrderException("배송완료된 판매자 주문만 교환을 요청할 수 있습니다.");
        }

        List<Long> itemIds = normalized.items().keySet().stream().sorted().toList();
        List<OrderItem> orderItems = orderItemRepository.findAllByIdInForUpdate(itemIds);
        validateOrderItems(order, sellerOrder, orderItems, itemIds);

        Shipment outbound = shipmentRepository.findBySellerOrderIdAndType(
                sellerOrderId, ShipmentType.ORIGINAL_OUTBOUND
        ).orElseThrow(() -> new OrderException("배송완료 시각을 확인할 수 없어 교환을 요청할 수 없습니다."));
        validateExchangePeriod(normalized.reasonType().defaultResponsibility(), outbound);

        Map<Long, Long> returnHeld = returnRequestRepository
                .sumItemQuantitiesByStatuses(itemIds, RETURN_HOLDING_STATUSES).stream()
                .collect(Collectors.toMap(PendingReturnQuantityProjection::getOrderItemId,
                        PendingReturnQuantityProjection::getPendingQuantity));
        Map<Long, Long> exchangeHeld = exchangeRequestRepository
                .sumItemQuantitiesByStatuses(itemIds, EXCHANGE_HOLDING_STATUSES).stream()
                .collect(Collectors.toMap(PendingExchangeQuantityProjection::getOrderItemId,
                        PendingExchangeQuantityProjection::getPendingQuantity));
        validateAvailableQuantities(orderItems, normalized.items(), returnHeld, exchangeHeld);

        Map<Long, ProductVariant> targetVariants = loadAndValidateTargets(orderItems, normalized.items());
        Map<Long, String> optionSnapshots = optionSnapshots(targetVariants.values());

        ExchangeRequest exchangeRequest;
        try {
            exchangeRequest = exchangeRequestRepository.saveAndFlush(ExchangeRequest.createRequested(
                    order, sellerOrder, normalized.clientRequestKey(), normalized.reasonType(), normalized.reason(),
                    normalized.collectionRecipientName(), normalized.collectionPhone(),
                    normalized.collectionPostalCode(), normalized.collectionAddress(),
                    normalized.collectionAddressDetail(), normalized.reshippingRecipientName(),
                    normalized.reshippingPhone(), normalized.reshippingPostalCode(),
                    normalized.reshippingAddress(), normalized.reshippingAddressDetail(), currentTime()
            ));
        } catch (DataIntegrityViolationException exception) {
            throw new OrderException("이미 사용된 교환 요청 키입니다.");
        }

        Map<Long, OrderItem> byId = orderItems.stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));
        List<ExchangeRequestItem> items = itemIds.stream().map(id -> {
            OrderItem original = byId.get(id);
            Product targetProduct = original.getProduct();
            ProductVariant variant = targetVariants.get(id);
            long targetUnitPrice = targetUnitPrice(targetProduct, variant);
            return ExchangeRequestItem.create(
                    exchangeRequest, original, normalized.items().get(id).quantity(), targetProduct, variant,
                    targetProduct.getName(), variant == null ? null : optionSnapshots.get(variant.getId()),
                    targetUnitPrice
            );
        }).toList();
        exchangeRequestItemRepository.saveAll(items);

        List<ExchangeRequestImage> images = new ArrayList<>();
        for (int i = 0; i < normalized.imageObjectKeys().size(); i++) {
            images.add(ExchangeRequestImage.create(exchangeRequest, normalized.imageObjectKeys().get(i), i));
        }
        if (!images.isEmpty()) exchangeRequestImageRepository.saveAll(images);
        return ExchangeRequestResponse.from(exchangeRequest, items, imageResponses(images));
    }

    private Map<Long, ProductVariant> loadAndValidateTargets(
            List<OrderItem> orderItems, Map<Long, NormalizedItem> requested
    ) {
        Map<Long, ProductVariant> result = new HashMap<>();
        for (OrderItem item : orderItems) {
            Product product = item.getProduct();
            NormalizedItem target = requested.get(item.getId());
            if (product.isDeleted() || product.getStatus() != ProductStatus.ON_SALE) {
                throw new OrderException("현재 판매가 중지된 상품은 교환 대상으로 선택할 수 없습니다.");
            }
            ProductVariant variant = null;
            if (item.getVariant() == null) {
                if (target.targetVariantId() != null) {
                    throw new OrderException("옵션이 없는 상품에는 교환 옵션을 선택할 수 없습니다.");
                }
                if (product.getStockQuantity() < target.quantity()) {
                    throw new OrderException("교환 대상 상품의 현재 재고가 부족합니다.");
                }
            } else {
                if (target.targetVariantId() == null) {
                    throw new OrderException("교환할 상품 옵션을 선택해주세요.");
                }
                variant = productVariantRepository.findById(target.targetVariantId())
                        .orElseThrow(() -> new OrderException("교환할 상품 옵션을 확인할 수 없습니다."));
                if (!variant.getProduct().getId().equals(product.getId())) {
                    throw new OrderException("같은 상품의 옵션으로만 교환할 수 있습니다.");
                }
                if (!variant.isActive()) throw new OrderException("비활성 옵션으로는 교환할 수 없습니다.");
                if (variant.getStockQuantity() < target.quantity()) {
                    throw new OrderException("교환 대상 옵션의 현재 재고가 부족합니다.");
                }
                result.put(item.getId(), variant);
            }
            if (targetUnitPrice(product, variant) != item.getUnitPrice()) {
                throw new OrderException("가격이 다른 옵션으로는 교환할 수 없습니다. 반품 후 다시 구매해 주세요.");
            }
        }
        return result;
    }

    private long targetUnitPrice(Product product, ProductVariant variant) {
        try {
            return Math.addExact(product.getPrice(), variant == null ? 0L : variant.getAdditionalPrice());
        } catch (ArithmeticException exception) {
            throw new OrderException("교환 대상 옵션 가격을 확인할 수 없습니다.");
        }
    }

    private Map<Long, String> optionSnapshots(Collection<ProductVariant> variants) {
        if (variants.isEmpty()) return Map.of();
        Map<Long, List<ProductVariantOptionValue>> byVariant = productVariantOptionValueRepository
                .findAllByVariantIdIn(variants.stream().map(ProductVariant::getId).toList())
                .stream().collect(Collectors.groupingBy(value -> value.getVariant().getId()));
        Map<Long, String> result = new HashMap<>();
        for (ProductVariant variant : variants) {
            List<ProductVariantOptionValue> values = byVariant.getOrDefault(variant.getId(), List.of());
            if (values.isEmpty()) throw new OrderException("상품 옵션 정보를 확인할 수 없습니다.");
            String snapshot = values.stream().map(ProductVariantOptionValue::getOptionValue)
                    .sorted(Comparator.comparing((ProductOptionValue value) -> value.getOptionGroup().getSortOrder())
                            .thenComparing(ProductOptionValue::getSortOrder))
                    .map(value -> value.getOptionGroup().getName() + ": " + value.getValue())
                    .collect(Collectors.joining(" / "));
            result.put(variant.getId(), snapshot);
        }
        return result;
    }

    private void validateAvailableQuantities(
            List<OrderItem> items, Map<Long, NormalizedItem> requested,
            Map<Long, Long> returnHeld, Map<Long, Long> exchangeHeld
    ) {
        for (OrderItem item : items) {
            long available = (long) item.getQuantity() - item.getCanceledQuantity()
                    - item.getReturnedQuantity() - item.getExchangedQuantity()
                    - returnHeld.getOrDefault(item.getId(), 0L)
                    - exchangeHeld.getOrDefault(item.getId(), 0L);
            if (requested.get(item.getId()).quantity() > available) {
                throw new OrderException("활성 반품 및 교환 요청을 포함한 교환 가능 수량을 초과했습니다.");
            }
        }
    }

    private void validateExchangePeriod(ExchangeResponsibility responsibility, Shipment outbound) {
        if (outbound.getStatus() != ShipmentStatus.DELIVERED || outbound.getDeliveredAt() == null) {
            throw new OrderException("배송완료 시각을 확인할 수 없어 교환을 요청할 수 없습니다.");
        }
        if (responsibility == ExchangeResponsibility.BUYER
                && currentTime().isAfter(outbound.getDeliveredAt().plusDays(BUYER_EXCHANGE_DAYS))) {
            throw new OrderException("구매자 귀책 교환 가능 기간이 지났습니다.");
        }
    }

    private void validateOrderItems(
            Order order, SellerOrder sellerOrder, List<OrderItem> items, List<Long> requestedIds
    ) {
        if (items.size() != requestedIds.size()) throw new OrderException("교환할 주문 상품 정보를 확인할 수 없습니다.");
        for (OrderItem item : items) {
            if (!item.getOrder().getId().equals(order.getId())
                    || !item.getSellerOrder().getId().equals(sellerOrder.getId())) {
                throw new OrderException("같은 판매자 주문의 상품만 함께 교환할 수 있습니다.");
            }
        }
    }

    private Optional<ExchangeRequestResponse> findExisting(
            Long userId, Long orderId, Long sellerOrderId, NormalizedRequest requested
    ) {
        return exchangeRequestRepository.findByClientRequestKey(requested.clientRequestKey()).map(existing -> {
            if (!existing.getOrder().getUser().getId().equals(userId)) {
                throw new OrderException("이미 사용된 교환 요청 키입니다.");
            }
            List<ExchangeRequestItem> items = exchangeRequestItemRepository
                    .findAllByExchangeRequestIdOrderByOrderItemIdAsc(existing.getId());
            Map<Long, NormalizedItem> existingItems = new LinkedHashMap<>();
            for (ExchangeRequestItem item : items) {
                existingItems.put(item.getOrderItem().getId(), new NormalizedItem(
                        item.getQuantity(), item.getTargetVariant() == null ? null : item.getTargetVariant().getId()
                ));
            }
            List<ExchangeRequestImage> images = exchangeRequestImageRepository
                    .findAllByExchangeRequestIdOrderBySortOrderAsc(existing.getId());
            List<String> keys = images.stream().map(ExchangeRequestImage::getObjectKey).toList();
            if (!samePayload(existing, orderId, sellerOrderId, requested, existingItems, keys)) {
                throw new OrderException("교환 요청 키가 최초 요청과 다른 내용으로 재사용되었습니다.");
            }
            return ExchangeRequestResponse.from(existing, items, imageResponses(images));
        });
    }

    private boolean samePayload(
            ExchangeRequest existing, Long orderId, Long sellerOrderId, NormalizedRequest request,
            Map<Long, NormalizedItem> items, List<String> keys
    ) {
        return existing.getOrder().getId().equals(orderId)
                && existing.getSellerOrder().getId().equals(sellerOrderId)
                && existing.getReasonType() == request.reasonType()
                && existing.getReason().equals(request.reason())
                && existing.getCollectionRecipientName().equals(request.collectionRecipientName())
                && existing.getCollectionPhone().equals(request.collectionPhone())
                && existing.getCollectionPostalCode().equals(request.collectionPostalCode())
                && existing.getCollectionAddress().equals(request.collectionAddress())
                && Objects.equals(existing.getCollectionAddressDetail(), request.collectionAddressDetail())
                && existing.getReshippingRecipientName().equals(request.reshippingRecipientName())
                && existing.getReshippingPhone().equals(request.reshippingPhone())
                && existing.getReshippingPostalCode().equals(request.reshippingPostalCode())
                && existing.getReshippingAddress().equals(request.reshippingAddress())
                && Objects.equals(existing.getReshippingAddressDetail(), request.reshippingAddressDetail())
                && items.equals(request.items()) && keys.equals(request.imageObjectKeys());
    }

    private ExchangeRequestResponse toResponse(ExchangeRequest request) {
        return ExchangeRequestResponse.from(
                request,
                exchangeRequestItemRepository.findAllByExchangeRequestIdOrderByOrderItemIdAsc(request.getId()),
                imageResponses(exchangeRequestImageRepository
                        .findAllByExchangeRequestIdOrderBySortOrderAsc(request.getId()))
        );
    }

    private List<ExchangeRequestImageResponse> imageResponses(List<ExchangeRequestImage> images) {
        return images.stream().map(image -> new ExchangeRequestImageResponse(
                image.getId(), storageService.createReadUrl(image.getObjectKey()), image.getSortOrder()
        )).toList();
    }

    private NormalizedRequest normalize(ExchangeRequestCreateRequest request) {
        if (request == null) throw new OrderException("교환 요청 정보를 확인해주세요.");
        if (request.reasonType() == null) throw new OrderException("교환 사유 유형을 선택해주세요.");
        Map<Long, NormalizedItem> items = normalizeItems(request.items());
        List<String> imageKeys = normalizeImageObjectKeys(request.imageObjectKeys());
        return new NormalizedRequest(
                normalizeRequestKey(request.clientRequestKey()), request.reasonType(),
                requiredText(request.reason(), 500, "교환 사유를 입력해주세요."),
                requiredText(request.collectionRecipientName(), 100, "회수 수령인 이름을 입력해주세요."),
                requiredText(request.collectionPhone(), 30, "회수 연락처를 입력해주세요."),
                requiredText(request.collectionPostalCode(), 20, "회수 우편번호를 입력해주세요."),
                requiredText(request.collectionAddress(), 255, "회수 주소를 입력해주세요."),
                nullableText(request.collectionAddressDetail(), "회수 상세 주소를 확인해주세요."),
                requiredText(request.reshippingRecipientName(), 100, "재배송 수령인 이름을 입력해주세요."),
                requiredText(request.reshippingPhone(), 30, "재배송 연락처를 입력해주세요."),
                requiredText(request.reshippingPostalCode(), 20, "재배송 우편번호를 입력해주세요."),
                requiredText(request.reshippingAddress(), 255, "재배송 주소를 입력해주세요."),
                nullableText(request.reshippingAddressDetail(), "재배송 상세 주소를 확인해주세요."),
                items, imageKeys
        );
    }

    private Map<Long, NormalizedItem> normalizeItems(List<ExchangeRequestItemRequest> items) {
        if (items == null || items.isEmpty() || items.size() > MAX_ITEM_COUNT) {
            throw new OrderException("교환할 주문 상품을 1개 이상 100개 이하로 선택해주세요.");
        }
        Map<Long, NormalizedItem> normalized = new LinkedHashMap<>();
        for (ExchangeRequestItemRequest item : items) {
            if (item == null || item.orderItemId() == null || item.orderItemId() <= 0
                    || item.quantity() == null || item.quantity() <= 0
                    || item.targetVariantId() != null && item.targetVariantId() <= 0) {
                throw new OrderException("교환 상품, 수량과 옵션을 다시 확인해주세요.");
            }
            if (normalized.putIfAbsent(item.orderItemId(),
                    new NormalizedItem(item.quantity(), item.targetVariantId())) != null) {
                throw new OrderException("같은 주문 상품이 교환 요청에 중복되었습니다.");
            }
        }
        return normalized.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> left, LinkedHashMap::new));
    }

    private List<String> normalizeImageObjectKeys(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        if (values.size() > MAX_IMAGE_COUNT) throw new OrderException("교환 증빙 이미지는 최대 5장까지 첨부할 수 있습니다.");
        List<String> normalized = values.stream()
                .map(value -> requiredText(value, 500, "교환 이미지 정보를 확인해주세요."))
                .toList();
        if (Set.copyOf(normalized).size() != normalized.size()) {
            throw new OrderException("같은 교환 이미지를 중복 첨부할 수 없습니다.");
        }
        return normalized;
    }

    private void validateImageOwnership(Long userId, List<String> keys) {
        String prefix = "exchanges/" + userId + "/";
        if (keys.stream().anyMatch(key -> !key.startsWith(prefix))) {
            throw new OrderException("교환 요청에 사용할 수 없는 이미지입니다.");
        }
    }

    private String normalizeRequestKey(String value) {
        String normalized = requiredText(value, 100, "교환 요청 키가 필요합니다.");
        try {
            if (!UUID.fromString(normalized).toString().equalsIgnoreCase(normalized)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            throw new OrderException("교환 요청 키는 UUID 형식이어야 합니다.");
        }
        return normalized;
    }

    private String requiredText(String value, int maxLength, String message) {
        if (value == null || value.isBlank()) throw new OrderException(message);
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new OrderException(message);
        return normalized;
    }

    private String nullableText(String value, String message) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 255) throw new OrderException(message);
        return normalized;
    }

    private void validateAuthenticated(Long userId) {
        if (userId == null) throw new AuthenticationException("인증이 필요합니다.");
    }

    private OrderException orderNotFound() { return new OrderException("주문 정보를 찾을 수 없습니다."); }

    LocalDateTime currentTime() { return LocalDateTime.now(); }

    private record NormalizedItem(int quantity, Long targetVariantId) { }

    private record NormalizedRequest(
            String clientRequestKey, ExchangeReasonType reasonType, String reason,
            String collectionRecipientName, String collectionPhone, String collectionPostalCode,
            String collectionAddress, String collectionAddressDetail,
            String reshippingRecipientName, String reshippingPhone, String reshippingPostalCode,
            String reshippingAddress, String reshippingAddressDetail,
            Map<Long, NormalizedItem> items, List<String> imageObjectKeys
    ) { }
}
