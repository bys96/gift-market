package com.giftmarket.order.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.order.dto.response.SellerOrderCancellationPageResponse;
import com.giftmarket.order.dto.response.SellerOrderCancellationResponse;
import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderCancellation;
import com.giftmarket.order.entity.OrderCancellationItem;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.repository.OrderCancellationItemRepository;
import com.giftmarket.order.repository.OrderCancellationOwnershipProjection;
import com.giftmarket.order.repository.OrderCancellationRepository;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentStatus;
import com.giftmarket.payment.repository.PaymentRepository;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SellerOrderCancellationService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_REJECT_REASON_LENGTH = 500;

    private final SellerRepository sellerRepository;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final SellerOrderRepository sellerOrderRepository;
    private final OrderCancellationRepository cancellationRepository;
    private final OrderCancellationItemRepository cancellationItemRepository;

    @Transactional(readOnly = true)
    public SellerOrderCancellationPageResponse getCancellations(
            Long userId,
            OrderCancellationStatus status,
            int page,
            int size
    ) {
        Seller seller = getActiveSeller(userId);
        validatePage(page, size);
        Page<OrderCancellation> cancellations = cancellationRepository
                .findSellerApprovalCancellations(
                        seller.getId(),
                        status,
                        PageRequest.of(page, size)
                );
        Map<Long, List<OrderCancellationItem>> itemsByCancellationId =
                getItemsByCancellationId(cancellations.getContent());

        return new SellerOrderCancellationPageResponse(
                cancellations.getContent().stream()
                        .map(cancellation -> response(
                                cancellation,
                                itemsByCancellationId.getOrDefault(
                                        cancellation.getId(),
                                        List.of()
                                )
                        ))
                        .toList(),
                cancellations.getNumber(),
                cancellations.getSize(),
                cancellations.getTotalElements(),
                cancellations.getTotalPages(),
                cancellations.isFirst(),
                cancellations.isLast()
        );
    }

    @Transactional(readOnly = true)
    public SellerOrderCancellationResponse getCancellation(
            Long userId,
            Long cancellationId
    ) {
        Seller seller = getActiveSeller(userId);
        OrderCancellationOwnershipProjection ownership = cancellationRepository
                .findOwnership(cancellationId, seller.getId())
                .orElseThrow(this::cancellationNotFound);
        OrderCancellation cancellation = cancellationRepository.findById(cancellationId)
                .orElseThrow(this::cancellationNotFound);
        validateOwnership(cancellation, ownership);
        return response(cancellation, getItems(cancellationId));
    }

    @Transactional
    public SellerOrderCancellationResponse approve(
            Long userId,
            Long cancellationId
    ) {
        return transition(
                userId,
                cancellationId,
                cancellation -> cancellation.startProcessing(LocalDateTime.now())
        );
    }

    @Transactional
    public SellerOrderCancellationResponse reject(
            Long userId,
            Long cancellationId,
            String reason
    ) {
        String normalizedReason = normalizeRejectReason(reason);
        return transition(
                userId,
                cancellationId,
                cancellation -> cancellation.reject(
                        normalizedReason,
                        LocalDateTime.now()
                )
        );
    }

    private SellerOrderCancellationResponse transition(
            Long userId,
            Long cancellationId,
            Consumer<OrderCancellation> transition
    ) {
        Seller seller = getActiveSeller(userId);
        OrderCancellationOwnershipProjection ownership = cancellationRepository
                .findOwnership(cancellationId, seller.getId())
                .orElseThrow(this::cancellationNotFound);

        Payment payment = paymentRepository
                .findFirstByOrderIdOrderByIdDesc(ownership.getOrderId())
                .flatMap(value -> paymentRepository.findByIdForUpdate(value.getId()))
                .orElseThrow(this::cancellationNotFound);
        Order order = orderRepository.findByIdForUpdate(ownership.getOrderId())
                .orElseThrow(this::cancellationNotFound);
        SellerOrder sellerOrder = sellerOrderRepository.findByIdAndSellerIdForUpdate(
                        ownership.getSellerOrderId(),
                        seller.getId()
                )
                .orElseThrow(this::cancellationNotFound);
        OrderCancellation cancellation = cancellationRepository.findByIdForUpdate(cancellationId)
                .orElseThrow(this::cancellationNotFound);

        validateTransitionState(payment, order, sellerOrder, cancellation, ownership);
        List<OrderCancellationItem> items = getItems(cancellationId);
        validateItems(cancellation, sellerOrder, items);
        try {
            transition.accept(cancellation);
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new SellerException(exception.getMessage());
        }
        return response(cancellation, items);
    }

    private void validateTransitionState(
            Payment payment,
            Order order,
            SellerOrder sellerOrder,
            OrderCancellation cancellation,
            OrderCancellationOwnershipProjection ownership
    ) {
        validateOwnership(cancellation, ownership);
        if (!cancellation.isRequiresSellerApproval()) {
            throw new SellerException("판매자 승인이 필요한 취소 요청이 아닙니다.");
        }
        if (!payment.isRefundableState()
                || order.getStatus() != OrderStatus.PAID
                || sellerOrder.getStatus() != SellerOrderStatus.PREPARING) {
            throw new SellerException("현재 주문 상태에서는 취소 요청을 처리할 수 없습니다.");
        }
        if (cancellation.getStatus() != OrderCancellationStatus.REQUESTED) {
            throw new SellerException("이미 처리되었거나 처리 중인 취소 요청입니다.");
        }
    }

    private void validateOwnership(
            OrderCancellation cancellation,
            OrderCancellationOwnershipProjection ownership
    ) {
        if (!cancellation.getOrder().getId().equals(ownership.getOrderId())
                || !cancellation.getSellerOrder().getId().equals(ownership.getSellerOrderId())) {
            throw cancellationNotFound();
        }
    }

    private void validateItems(
            OrderCancellation cancellation,
            SellerOrder sellerOrder,
            List<OrderCancellationItem> items
    ) {
        if (items.isEmpty()) {
            throw new SellerException("취소 요청 상품 정보를 확인할 수 없습니다.");
        }
        for (OrderCancellationItem item : items) {
            OrderItem orderItem = item.getOrderItem();
            if (!orderItem.getOrder().getId().equals(cancellation.getOrder().getId())
                    || !orderItem.getSellerOrder().getId().equals(sellerOrder.getId())) {
                throw new SellerException("취소 요청 상품 정보가 판매자 주문과 일치하지 않습니다.");
            }
        }
    }

    private Map<Long, List<OrderCancellationItem>> getItemsByCancellationId(
            List<OrderCancellation> cancellations
    ) {
        if (cancellations.isEmpty()) {
            return Map.of();
        }
        return cancellationItemRepository
                .findAllByOrderCancellationIdInOrderByOrderCancellationIdAscOrderItemIdAsc(
                        cancellations.stream().map(OrderCancellation::getId).toList()
                )
                .stream()
                .collect(Collectors.groupingBy(
                        item -> item.getOrderCancellation().getId()
                ));
    }

    private List<OrderCancellationItem> getItems(Long cancellationId) {
        return cancellationItemRepository
                .findAllByOrderCancellationIdOrderByIdAsc(cancellationId);
    }

    private SellerOrderCancellationResponse response(
            OrderCancellation cancellation,
            List<OrderCancellationItem> items
    ) {
        if (items.isEmpty()) {
            throw new SellerException("취소 요청 상품 정보를 확인할 수 없습니다.");
        }
        return SellerOrderCancellationResponse.from(cancellation, items);
    }

    private Seller getActiveSeller(Long userId) {
        if (userId == null) {
            throw new AuthenticationException("인증이 필요합니다.");
        }
        Seller seller = sellerRepository.findByUserId(userId)
                .orElseThrow(this::cancellationNotFound);
        if (seller.getStatus() != SellerStatus.ACTIVE) {
            throw new SellerException("활성 상태의 판매자만 취소 요청을 처리할 수 있습니다.");
        }
        return seller;
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new SellerException("페이지 정보를 확인해주세요.");
        }
    }

    private String normalizeRejectReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new SellerException("취소 거절 사유를 입력해주세요.");
        }
        String normalized = reason.trim();
        if (normalized.length() > MAX_REJECT_REASON_LENGTH) {
            throw new SellerException("취소 거절 사유는 500자 이내로 입력해주세요.");
        }
        return normalized;
    }

    private SellerException cancellationNotFound() {
        return new SellerException("취소 요청 정보를 찾을 수 없습니다.");
    }
}
