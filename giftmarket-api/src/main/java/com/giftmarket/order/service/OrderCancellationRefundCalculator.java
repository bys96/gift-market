package com.giftmarket.order.service;

import com.giftmarket.order.dto.response.CancellationRefundCalculation;
import com.giftmarket.order.dto.response.CancellationRefundItemCalculation;
import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderCancellation;
import com.giftmarket.order.entity.OrderCancellationItem;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.order.entity.SellerOrder;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderCancellationRefundCalculator {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final SellerOrderRepository sellerOrderRepository;
    private final OrderCancellationRepository cancellationRepository;
    private final OrderCancellationItemRepository cancellationItemRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public CancellationRefundCalculation calculate(Long cancellationId) {
        OrderCancellation reference = cancellationRepository.findById(cancellationId)
                .orElseThrow(this::calculationNotAvailable);
        Long orderId = reference.getOrder().getId();
        Long sellerOrderId = reference.getSellerOrder().getId();

        Payment payment = paymentRepository.findFirstByOrderIdOrderByIdDesc(orderId)
                .flatMap(value -> paymentRepository.findByIdForUpdate(value.getId()))
                .orElseThrow(this::calculationNotAvailable);
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(this::calculationNotAvailable);
        SellerOrder sellerOrder = sellerOrderRepository
                .findByIdAndOrderIdForUpdate(sellerOrderId, orderId)
                .orElseThrow(this::calculationNotAvailable);
        OrderCancellation cancellation = cancellationRepository
                .findByIdForUpdate(cancellationId)
                .orElseThrow(this::calculationNotAvailable);
        List<OrderItem> sellerOrderItems = orderItemRepository
                .findAllBySellerOrderIdForUpdate(sellerOrderId);
        List<OrderCancellationItem> cancellationItems = cancellationItemRepository
                .findAllByOrderCancellationIdOrderByIdAsc(cancellationId);

        validateState(payment, order, sellerOrder, cancellation, orderId, sellerOrderId);
        return calculateAmounts(payment, sellerOrder, cancellation, sellerOrderItems, cancellationItems);
    }

    private CancellationRefundCalculation calculateAmounts(
            Payment payment,
            SellerOrder sellerOrder,
            OrderCancellation cancellation,
            List<OrderItem> sellerOrderItems,
            List<OrderCancellationItem> cancellationItems
    ) {
        if (sellerOrderItems.isEmpty() || cancellationItems.isEmpty()) {
            throw calculationNotAvailable();
        }

        Map<Long, OrderItem> sellerItemsById = new HashMap<>();
        for (OrderItem orderItem : sellerOrderItems) {
            validateSnapshot(orderItem, sellerOrder);
            sellerItemsById.put(orderItem.getId(), orderItem);
        }

        Map<Long, Integer> requestedByItemId = new HashMap<>();
        List<CancellationRefundItemCalculation> itemCalculations = new ArrayList<>();
        long productRefundAmount = 0L;

        try {
            for (OrderCancellationItem cancellationItem : cancellationItems) {
                Long orderItemId = cancellationItem.getOrderItem().getId();
                OrderItem orderItem = sellerItemsById.get(orderItemId);
                int requestedQuantity = cancellationItem.getQuantity();

                if (orderItem == null || requestedByItemId.put(orderItemId, requestedQuantity) != null) {
                    throw calculationNotAvailable();
                }
                int remainingQuantity = orderItem.getRemainingQuantity();
                if (requestedQuantity <= 0 || requestedQuantity > remainingQuantity) {
                    throw calculationNotAvailable();
                }

                long itemRefundAmount = Math.multiplyExact(
                        orderItem.getUnitPrice(),
                        (long) requestedQuantity
                );
                productRefundAmount = Math.addExact(productRefundAmount, itemRefundAmount);
                itemCalculations.add(new CancellationRefundItemCalculation(
                        orderItemId,
                        requestedQuantity,
                        orderItem.getUnitPrice(),
                        itemRefundAmount,
                        remainingQuantity - requestedQuantity
                ));
            }

            boolean fullyCanceled = sellerOrderItems.stream().allMatch(orderItem -> {
                int requestedQuantity = requestedByItemId.getOrDefault(orderItem.getId(), 0);
                return orderItem.getRemainingQuantity() - requestedQuantity == 0;
            });
            long shippingRefundAmount = fullyCanceled
                    ? sumOriginalShippingFee(sellerOrderItems)
                    : 0L;
            long totalRefundAmount = Math.addExact(productRefundAmount, shippingRefundAmount);

            if (productRefundAmount < 0L
                    || shippingRefundAmount < 0L
                    || totalRefundAmount <= 0L
                    || totalRefundAmount > payment.getAmount()) {
                throw calculationNotAvailable();
            }
            return new CancellationRefundCalculation(
                    cancellation.getId(),
                    productRefundAmount,
                    shippingRefundAmount,
                    totalRefundAmount,
                    fullyCanceled,
                    itemCalculations
            );
        } catch (ArithmeticException exception) {
            throw new OrderException("환불 금액을 안전하게 계산할 수 없습니다.");
        }
    }

    private long sumOriginalShippingFee(List<OrderItem> sellerOrderItems) {
        long shippingFee = 0L;
        for (OrderItem orderItem : sellerOrderItems) {
            shippingFee = Math.addExact(shippingFee, orderItem.getShippingFee());
        }
        return shippingFee;
    }

    private void validateState(
            Payment payment,
            Order order,
            SellerOrder sellerOrder,
            OrderCancellation cancellation,
            Long orderId,
            Long sellerOrderId
    ) {
        if (payment.getStatus() != PaymentStatus.PAID
                || order.getStatus() != OrderStatus.PAID
                || !cancellation.getOrder().getId().equals(orderId)
                || !cancellation.getSellerOrder().getId().equals(sellerOrderId)
                || sellerOrder.getOrder() != order
                || (cancellation.getStatus() != OrderCancellationStatus.REQUESTED
                    && cancellation.getStatus() != OrderCancellationStatus.PROCESSING)) {
            throw calculationNotAvailable();
        }
        if (payment.getAmount() == null || payment.getAmount() <= 0L) {
            throw calculationNotAvailable();
        }
    }

    private void validateSnapshot(OrderItem orderItem, SellerOrder sellerOrder) {
        if (orderItem.getSellerOrder() != sellerOrder
                || orderItem.getQuantity() == null
                || orderItem.getQuantity() <= 0
                || orderItem.getCanceledQuantity() < 0
                || orderItem.getCanceledQuantity() > orderItem.getQuantity()
                || orderItem.getUnitPrice() == null
                || orderItem.getUnitPrice() <= 0L
                || orderItem.getShippingFee() == null
                || orderItem.getShippingFee() < 0L) {
            throw calculationNotAvailable();
        }
    }

    private OrderException calculationNotAvailable() {
        return new OrderException("현재 주문 취소 요청의 환불 금액을 계산할 수 없습니다.");
    }
}
