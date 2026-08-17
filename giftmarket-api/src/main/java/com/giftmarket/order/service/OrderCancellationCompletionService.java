package com.giftmarket.order.service;

import com.giftmarket.order.dto.response.OrderCancellationCompletionResult;
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
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentStatus;
import com.giftmarket.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderCancellationCompletionService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final SellerOrderRepository sellerOrderRepository;
    private final OrderCancellationRepository cancellationRepository;
    private final OrderCancellationItemRepository cancellationItemRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderInventoryService inventoryService;

    @Transactional
    public OrderCancellationCompletionResult complete(Long cancellationId) {
        OrderCancellation reference = cancellationRepository.findById(cancellationId)
                .orElseThrow(this::completionNotAvailable);
        Long orderId = reference.getOrder().getId();
        Long sellerOrderId = reference.getSellerOrder().getId();

        Payment payment = paymentRepository.findFirstByOrderIdOrderByIdDesc(orderId)
                .flatMap(value -> paymentRepository.findByIdForUpdate(value.getId()))
                .orElseThrow(this::completionNotAvailable);
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(this::completionNotAvailable);
        SellerOrder sellerOrder = sellerOrderRepository
                .findByIdAndOrderIdForUpdate(sellerOrderId, orderId)
                .orElseThrow(this::completionNotAvailable);
        OrderCancellation cancellation = cancellationRepository.findByIdForUpdate(cancellationId)
                .orElseThrow(this::completionNotAvailable);

        validateIdentity(cancellation, orderId, sellerOrderId);
        if (cancellation.getStatus() == OrderCancellationStatus.COMPLETED) {
            return result(cancellation, sellerOrder);
        }
        validateActiveState(payment, order, sellerOrder, cancellation);

        List<OrderItem> lockedOrderItems = orderItemRepository
                .findAllBySellerOrderIdForUpdate(sellerOrderId);
        List<OrderCancellationItem> cancellationItems = cancellationItemRepository
                .findAllByOrderCancellationIdOrderByIdAsc(cancellationId);
        Map<Long, OrderItem> lockedItemsById = validateQuantities(
                cancellation,
                sellerOrder,
                lockedOrderItems,
                cancellationItems
        );

        inventoryService.restoreCancellationItems(cancellationItems);
        confirmQuantities(lockedItemsById, cancellationItems);
        if (lockedOrderItems.stream().allMatch(OrderItem::isFullyCanceled)) {
            sellerOrder.cancel();
        }
        cancellation.complete(LocalDateTime.now());
        return result(cancellation, sellerOrder);
    }

    private void validateIdentity(OrderCancellation cancellation, Long orderId, Long sellerOrderId) {
        if (!cancellation.getOrder().getId().equals(orderId)
                || !cancellation.getSellerOrder().getId().equals(sellerOrderId)) {
            throw completionNotAvailable();
        }
    }

    private void validateActiveState(
            Payment payment,
            Order order,
            SellerOrder sellerOrder,
            OrderCancellation cancellation
    ) {
        if (payment.getStatus() != PaymentStatus.PAID
                || order.getStatus() != OrderStatus.PAID
                || cancellation.getStatus() != OrderCancellationStatus.PROCESSING
                || (sellerOrder.getStatus() != SellerOrderStatus.PAID
                    && sellerOrder.getStatus() != SellerOrderStatus.PREPARING)) {
            throw completionNotAvailable();
        }
    }

    private Map<Long, OrderItem> validateQuantities(
            OrderCancellation cancellation,
            SellerOrder sellerOrder,
            List<OrderItem> lockedOrderItems,
            List<OrderCancellationItem> cancellationItems
    ) {
        if (lockedOrderItems.isEmpty() || cancellationItems.isEmpty()) {
            throw completionNotAvailable();
        }
        Map<Long, OrderItem> lockedItemsById = new HashMap<>();
        for (OrderItem orderItem : lockedOrderItems) {
            if (orderItem.getSellerOrder() != sellerOrder) {
                throw completionNotAvailable();
            }
            lockedItemsById.put(orderItem.getId(), orderItem);
        }
        for (OrderCancellationItem cancellationItem : cancellationItems) {
            OrderItem lockedItem = lockedItemsById.get(cancellationItem.getOrderItem().getId());
            if (lockedItem == null
                    || cancellationItem.getOrderCancellation() != cancellation
                    || cancellationItem.getOrderItem().getSellerOrder() != sellerOrder
                    || cancellationItem.getQuantity() <= 0
                    || cancellationItem.getQuantity() > lockedItem.getRemainingQuantity()) {
                throw completionNotAvailable();
            }
        }
        return lockedItemsById;
    }

    private void confirmQuantities(
            Map<Long, OrderItem> lockedItemsById,
            List<OrderCancellationItem> cancellationItems
    ) {
        for (OrderCancellationItem cancellationItem : cancellationItems) {
            OrderItem lockedItem = lockedItemsById.get(cancellationItem.getOrderItem().getId());
            lockedItem.confirmCancellation(cancellationItem.getQuantity());
        }
    }

    private OrderCancellationCompletionResult result(
            OrderCancellation cancellation,
            SellerOrder sellerOrder
    ) {
        return new OrderCancellationCompletionResult(
                cancellation.getId(),
                cancellation.getStatus(),
                sellerOrder.getStatus()
        );
    }

    private OrderException completionNotAvailable() {
        return new OrderException("현재 주문 취소 요청을 확정할 수 없습니다.");
    }
}
