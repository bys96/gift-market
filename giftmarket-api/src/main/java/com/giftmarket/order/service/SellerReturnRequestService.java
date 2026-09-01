package com.giftmarket.order.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.global.storage.service.StorageService;
import com.giftmarket.order.dto.request.SellerReturnInspectRequest;
import com.giftmarket.order.dto.request.SellerReturnInspectionItemRequest;
import com.giftmarket.order.dto.response.ReturnRequestResponse;
import com.giftmarket.order.dto.response.ReturnRequestImageResponse;
import com.giftmarket.order.dto.response.SellerReturnRequestPageResponse;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.*;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.exception.SellerException;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SellerReturnRequestService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_REASON_LENGTH = 500;

    private final SellerRepository sellerRepository;
    private final OrderRepository orderRepository;
    private final SellerOrderRepository sellerOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnRequestItemRepository returnRequestItemRepository;
    private final ReturnRequestImageRepository returnRequestImageRepository;
    private final ShipmentRepository shipmentRepository;
    private final PaymentRepository paymentRepository;
    private final ReturnRefundCalculationService returnRefundCalculationService;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    public SellerReturnRequestPageResponse getReturns(
            Long userId,
            ReturnRequestStatus status,
            int page,
            int size
    ) {
        Seller seller = getActiveSeller(userId);
        validatePage(page, size);
        Page<ReturnRequest> returns = returnRequestRepository.findSellerReturns(
                seller.getId(), status, PageRequest.of(page, size)
        );
        Map<Long, List<ReturnRequestItem>> itemsByRequestId = getItemsByRequestId(
                returns.getContent()
        );
        Map<Long, List<ReturnRequestImage>> imagesByRequestId = getImagesByRequestId(returns.getContent());
        return new SellerReturnRequestPageResponse(
                returns.getContent().stream()
                        .map(request -> response(request, itemsByRequestId.getOrDefault(
                                request.getId(), List.of()
                        ), imagesByRequestId.getOrDefault(request.getId(), List.of())))
                        .toList(),
                returns.getNumber(), returns.getSize(), returns.getTotalElements(),
                returns.getTotalPages(), returns.isFirst(), returns.isLast()
        );
    }

    @Transactional(readOnly = true)
    public ReturnRequestResponse getReturn(Long userId, Long returnRequestId) {
        Seller seller = getActiveSeller(userId);
        returnRequestRepository.findOwnership(returnRequestId, seller.getId())
                .orElseThrow(this::returnNotFound);
        ReturnRequest request = returnRequestRepository.findById(returnRequestId)
                .orElseThrow(this::returnNotFound);
        validateSellerOwnership(request, seller.getId());
        return response(request, getItems(returnRequestId));
    }

    @Transactional
    public ReturnRequestResponse approve(
            Long userId,
            Long returnRequestId,
            ReturnResponsibility responsibility
    ) {
        LockedReturn locked = lock(userId, returnRequestId);
        ReturnRequest request = locked.request();
        requireStatus(request, ReturnRequestStatus.REQUESTED);
        try {
            if (request.getReasonType() == ReturnReasonType.OTHER) {
                if (responsibility == null) {
                    throw new SellerException("기타 반품 사유는 귀책 주체를 선택해야 합니다.");
                }
                request.confirmResponsibility(responsibility);
            } else if (responsibility != null) {
                throw new SellerException("기타 사유가 아닌 반품의 귀책 주체는 변경할 수 없습니다.");
            }
            request.approve(currentTime());
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new SellerException(exception.getMessage());
        }
        return response(request, locked.items());
    }

    @Transactional
    public ReturnRequestResponse reject(Long userId, Long returnRequestId, String reason) {
        String normalized = requiredText(reason, MAX_REASON_LENGTH, "반품 거절 사유를 입력해주세요.");
        return transition(userId, returnRequestId, ReturnRequestStatus.REQUESTED,
                request -> request.reject(normalized, currentTime()));
    }

    @Transactional
    public ReturnRequestResponse collect(
            Long userId,
            Long returnRequestId,
            String shippingCompany,
            String trackingNumber
    ) {
        String normalizedCompany = requiredText(shippingCompany, 100, "택배사를 입력해주세요.");
        String normalizedTracking = requiredText(trackingNumber, 100, "송장번호를 입력해주세요.");
        LockedReturn locked = lock(userId, returnRequestId);
        ReturnRequest request = locked.request();
        requireStatus(request, ReturnRequestStatus.APPROVED);
        if (request.getCollectionShipment() != null) {
            throw new SellerException("이미 회수 배송이 시작되었습니다.");
        }
        LocalDateTime now = currentTime();
        Shipment shipment = Shipment.createShipped(
                locked.sellerOrder(), ShipmentType.RETURN_COLLECTION,
                normalizedCompany, normalizedTracking, now
        );
        shipmentRepository.save(shipment);
        try {
            request.assignCollectionShipment(shipment);
            request.startCollecting(now);
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new SellerException(exception.getMessage());
        }
        return response(request, locked.items());
    }

    @Transactional
    public ReturnRequestResponse receive(Long userId, Long returnRequestId) {
        LockedReturn locked = lock(userId, returnRequestId);
        ReturnRequest request = locked.request();
        requireStatus(request, ReturnRequestStatus.COLLECTING);
        Shipment shipment = validateCollectionShipment(request, locked.sellerOrder());
        LocalDateTime now = currentTime();
        try {
            shipment.deliver(now);
            request.receive(now);
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new SellerException(exception.getMessage());
        }
        return response(request, locked.items());
    }

    @Transactional
    public ReturnRequestResponse inspect(
            Long userId,
            Long returnRequestId,
            SellerReturnInspectRequest inspectRequest
    ) {
        Map<Long, ReturnInspectionResult> results = normalizeInspection(inspectRequest);
        LockedReturn locked = lockForRefundCalculation(userId, returnRequestId);
        requireStatus(locked.request(), ReturnRequestStatus.RECEIVED);
        Map<Long, ReturnRequestItem> itemsByOrderItemId = locked.items().stream()
                .collect(Collectors.toMap(item -> item.getOrderItem().getId(), item -> item));
        if (!itemsByOrderItemId.keySet().equals(results.keySet())) {
            throw new SellerException("반품 요청의 모든 상품 검수 결과를 한 번에 입력해주세요.");
        }
        try {
            results.forEach((orderItemId, result) ->
                    itemsByOrderItemId.get(orderItemId).inspect(result));
            locked.request().completeInspection(currentTime());
            returnRefundCalculationService.confirm(
                    locked.payment(), locked.sellerOrder(), locked.request(),
                    locked.lockedOrderItems(), locked.items()
            );
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new SellerException(exception.getMessage());
        }
        return response(locked.request(), locked.items());
    }

    private ReturnRequestResponse transition(
            Long userId,
            Long returnRequestId,
            ReturnRequestStatus expected,
            Consumer<ReturnRequest> action
    ) {
        LockedReturn locked = lock(userId, returnRequestId);
        requireStatus(locked.request(), expected);
        try {
            action.accept(locked.request());
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new SellerException(exception.getMessage());
        }
        return response(locked.request(), locked.items());
    }

    private LockedReturn lock(Long userId, Long returnRequestId) {
        Seller seller = getActiveSeller(userId);
        ReturnRequestOwnershipProjection ownership = returnRequestRepository
                .findOwnership(returnRequestId, seller.getId())
                .orElseThrow(this::returnNotFound);
        Order order = orderRepository.findByIdForUpdate(ownership.getOrderId())
                .orElseThrow(this::returnNotFound);
        SellerOrder sellerOrder = sellerOrderRepository.findByIdAndSellerIdForUpdate(
                        ownership.getSellerOrderId(), seller.getId()
                )
                .orElseThrow(this::returnNotFound);
        ReturnRequest request = returnRequestRepository.findByIdForUpdate(returnRequestId)
                .orElseThrow(this::returnNotFound);
        if (!request.getOrder().getId().equals(order.getId())
                || !request.getSellerOrder().getId().equals(sellerOrder.getId())) {
            throw returnNotFound();
        }
        List<ReturnRequestItem> items = getItems(returnRequestId);
        if (items.isEmpty()) {
            throw new SellerException("반품 요청 상품 정보를 확인할 수 없습니다.");
        }
        List<Long> itemIds = items.stream()
                .map(item -> item.getOrderItem().getId()).sorted().toList();
        List<OrderItem> lockedItems = orderItemRepository.findAllByIdInForUpdate(itemIds);
        validateItems(request, sellerOrder, items, lockedItems, itemIds);
        return new LockedReturn(order, sellerOrder, request, items, lockedItems, null);
    }

    private LockedReturn lockForRefundCalculation(Long userId, Long returnRequestId) {
        Seller seller = getActiveSeller(userId);
        ReturnRequestOwnershipProjection ownership = returnRequestRepository
                .findOwnership(returnRequestId, seller.getId())
                .orElseThrow(this::returnNotFound);
        Payment payment = paymentRepository.findFirstByOrderIdOrderByIdDesc(ownership.getOrderId())
                .flatMap(value -> paymentRepository.findByIdForUpdate(value.getId()))
                .orElseThrow(this::returnNotFound);
        Order order = orderRepository.findByIdForUpdate(ownership.getOrderId())
                .orElseThrow(this::returnNotFound);
        SellerOrder sellerOrder = sellerOrderRepository.findByIdAndSellerIdForUpdate(
                        ownership.getSellerOrderId(), seller.getId())
                .orElseThrow(this::returnNotFound);
        ReturnRequest request = returnRequestRepository.findByIdForUpdate(returnRequestId)
                .orElseThrow(this::returnNotFound);
        if (!request.getOrder().getId().equals(order.getId())
                || !request.getSellerOrder().getId().equals(sellerOrder.getId())) {
            throw returnNotFound();
        }
        List<ReturnRequestItem> items = getItems(returnRequestId);
        if (items.isEmpty()) throw new SellerException("반품 요청 상품 정보를 확인할 수 없습니다.");
        List<Long> itemIds = items.stream().map(item -> item.getOrderItem().getId()).sorted().toList();
        List<OrderItem> sellerOrderItems = orderItemRepository
                .findAllBySellerOrderIdForUpdate(sellerOrder.getId());
        validateItems(request, sellerOrder, items, sellerOrderItems, itemIds);
        return new LockedReturn(order, sellerOrder, request, items, sellerOrderItems, payment);
    }

    private void validateItems(
            ReturnRequest request,
            SellerOrder sellerOrder,
            List<ReturnRequestItem> requestItems,
            List<OrderItem> lockedItems,
            List<Long> itemIds
    ) {
        java.util.Set<Long> lockedIds = lockedItems.stream()
                .map(OrderItem::getId).collect(java.util.stream.Collectors.toSet());
        if (lockedIds.size() != lockedItems.size() || !lockedIds.containsAll(itemIds)) {
            throw new SellerException("반품 요청 상품 정보를 확인할 수 없습니다.");
        }
        for (ReturnRequestItem requestItem : requestItems) {
            OrderItem item = requestItem.getOrderItem();
            if (!item.getOrder().getId().equals(request.getOrder().getId())
                    || !item.getSellerOrder().getId().equals(sellerOrder.getId())) {
                throw new SellerException("반품 요청 상품 정보가 판매자 주문과 일치하지 않습니다.");
            }
        }
    }

    private Shipment validateCollectionShipment(ReturnRequest request, SellerOrder sellerOrder) {
        Shipment shipment = request.getCollectionShipment();
        if (shipment == null
                || shipment.getType() != ShipmentType.RETURN_COLLECTION
                || !shipment.getSellerOrder().getId().equals(sellerOrder.getId())) {
            throw new SellerException("회수 배송 정보를 확인할 수 없습니다.");
        }
        return shipment;
    }

    private Map<Long, ReturnInspectionResult> normalizeInspection(SellerReturnInspectRequest request) {
        if (request == null || request.items() == null
                || request.items().isEmpty() || request.items().size() > 100) {
            throw new SellerException("검수 상품 정보를 확인해주세요.");
        }
        Map<Long, ReturnInspectionResult> results = new LinkedHashMap<>();
        for (SellerReturnInspectionItemRequest item : request.items()) {
            if (item == null || item.orderItemId() == null || item.orderItemId() <= 0
                    || item.inspectionResult() == null) {
                throw new SellerException("검수 상품 정보를 확인해주세요.");
            }
            if (results.putIfAbsent(item.orderItemId(), item.inspectionResult()) != null) {
                throw new SellerException("같은 주문 상품의 검수 결과가 중복되었습니다.");
            }
        }
        return results;
    }

    private Map<Long, List<ReturnRequestItem>> getItemsByRequestId(List<ReturnRequest> requests) {
        if (requests.isEmpty()) return Map.of();
        return returnRequestItemRepository
                .findAllByReturnRequestIdInOrderByReturnRequestIdAscOrderItemIdAsc(
                        requests.stream().map(ReturnRequest::getId).toList()
                )
                .stream()
                .collect(Collectors.groupingBy(item -> item.getReturnRequest().getId()));
    }

    private List<ReturnRequestItem> getItems(Long requestId) {
        return returnRequestItemRepository.findAllByReturnRequestIdOrderByIdAsc(requestId);
    }

    private ReturnRequestResponse response(ReturnRequest request, List<ReturnRequestItem> items) {
        return response(request, items,
                returnRequestImageRepository.findAllByReturnRequestIdOrderBySortOrderAsc(request.getId()));
    }

    private ReturnRequestResponse response(
            ReturnRequest request,
            List<ReturnRequestItem> items,
            List<ReturnRequestImage> images
    ) {
        if (items.isEmpty()) {
            throw new SellerException("반품 요청 상품 정보를 확인할 수 없습니다.");
        }
        return ReturnRequestResponse.from(request, items, images.stream()
                .map(image -> new ReturnRequestImageResponse(
                        image.getId(), storageService.createReadUrl(image.getObjectKey()), image.getSortOrder()
                )).toList());
    }

    private Map<Long, List<ReturnRequestImage>> getImagesByRequestId(List<ReturnRequest> requests) {
        if (requests.isEmpty()) return Map.of();
        return returnRequestImageRepository
                .findAllByReturnRequestIdInOrderByReturnRequestIdAscSortOrderAsc(
                        requests.stream().map(ReturnRequest::getId).toList())
                .stream().collect(Collectors.groupingBy(image -> image.getReturnRequest().getId()));
    }

    private void requireStatus(ReturnRequest request, ReturnRequestStatus expected) {
        if (request.getStatus() != expected) {
            throw new SellerException("현재 반품 상태에서는 요청을 처리할 수 없습니다.");
        }
    }

    private Seller getActiveSeller(Long userId) {
        if (userId == null) throw new AuthenticationException("인증이 필요합니다.");
        Seller seller = sellerRepository.findByUserId(userId).orElseThrow(this::returnNotFound);
        if (seller.getStatus() != SellerStatus.ACTIVE
                && seller.getStatus() != SellerStatus.SALES_SUSPENDED) {
            throw new SellerException("활성 상태의 판매자만 반품 요청을 처리할 수 있습니다.");
        }
        return seller;
    }

    private void validateSellerOwnership(ReturnRequest request, Long sellerId) {
        if (!request.getSellerOrder().getSeller().getId().equals(sellerId)) {
            throw returnNotFound();
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new SellerException("페이지 정보를 확인해주세요.");
        }
    }

    private String requiredText(String value, int maxLength, String message) {
        if (value == null || value.isBlank()) throw new SellerException(message);
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new SellerException(message);
        return normalized;
    }

    private SellerException returnNotFound() {
        return new SellerException("반품 요청 정보를 찾을 수 없습니다.");
    }

    LocalDateTime currentTime() {
        return LocalDateTime.now();
    }

    private record LockedReturn(
            Order order,
            SellerOrder sellerOrder,
            ReturnRequest request,
            List<ReturnRequestItem> items,
            List<OrderItem> lockedOrderItems,
            Payment payment
    ) {
    }
}
