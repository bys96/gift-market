package com.giftmarket.order.service;

import com.giftmarket.order.entity.*;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.order.repository.*;
import com.giftmarket.payment.entity.*;
import com.giftmarket.payment.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReturnCompletionService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final SellerOrderRepository sellerOrderRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnRequestItemRepository returnItemRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentCancellationRepository cancellationRepository;
    private final OrderInventoryService inventoryService;

    @Transactional
    public void complete(Long returnRequestId) {
        ReturnRequest reference = returnRequestRepository.findById(returnRequestId).orElseThrow(this::notAvailable);
        Payment payment = paymentRepository.findFirstByOrderIdOrderByIdDesc(reference.getOrder().getId())
                .flatMap(value -> paymentRepository.findByIdForUpdate(value.getId())).orElseThrow(this::notAvailable);
        Order order = orderRepository.findByIdForUpdate(reference.getOrder().getId()).orElseThrow(this::notAvailable);
        SellerOrder sellerOrder = sellerOrderRepository.findByIdAndOrderIdForUpdate(
                reference.getSellerOrder().getId(), order.getId()).orElseThrow(this::notAvailable);
        ReturnRequest request = returnRequestRepository.findByIdForUpdate(returnRequestId).orElseThrow(this::notAvailable);
        if (request.getStatus() == ReturnRequestStatus.COMPLETED) return;
        validateIdentity(request, order, sellerOrder);
        PaymentCancellation cancellation = cancellationRepository.findByReturnRequestId(returnRequestId)
                .flatMap(value -> cancellationRepository.findByIdForUpdate(value.getId())).orElse(null);
        validateRefundCompletion(request, payment, cancellation);

        List<ReturnRequestItem> returnItems = returnItemRepository.findAllByReturnRequestIdOrderByIdAsc(returnRequestId);
        List<OrderItem> lockedItems = orderItemRepository.findAllBySellerOrderIdForUpdate(sellerOrder.getId());
        Map<Long, OrderItem> byId = validateItems(request, sellerOrder, returnItems, lockedItems);

        inventoryService.restoreReturnItems(returnItems);
        for (ReturnRequestItem item : returnItems) {
            OrderItem locked = byId.get(item.getOrderItem().getId());
            locked.confirmReturn(item.getQuantity());
            if (item.getInspectionResult() == ReturnInspectionResult.RESTOCKABLE) {
                item.increaseRestockedQuantity(item.getQuantity());
            } else if (item.getRestockedQuantity() != 0) {
                throw notAvailable();
            }
        }
        request.complete(LocalDateTime.now());
    }

    private void validateIdentity(ReturnRequest request, Order order, SellerOrder sellerOrder) {
        if (request.getOrder() != order || request.getSellerOrder() != sellerOrder
                || sellerOrder.getStatus() != SellerOrderStatus.DELIVERED) throw notAvailable();
    }

    private void validateRefundCompletion(ReturnRequest request, Payment payment, PaymentCancellation cancellation) {
        if (request.getStatus() != ReturnRequestStatus.REFUNDING || request.getRefundAmount() == null) throw notAvailable();
        if (request.getRefundAmount() == 0L) {
            if (cancellation != null) throw notAvailable();
            return;
        }
        if (request.getRefundAmount() < 0L || cancellation == null
                || cancellation.getType() != PaymentCancellationType.PARTIAL
                || cancellation.getStatus() != PaymentCancellationStatus.SUCCEEDED
                || cancellation.getPayment() != payment
                || cancellation.getReturnRequest() != request
                || cancellation.getOrderCancellation() != null
                || !Objects.equals(cancellation.getAmount(), request.getRefundAmount())) throw notAvailable();
    }

    private Map<Long, OrderItem> validateItems(ReturnRequest request, SellerOrder sellerOrder,
                                                List<ReturnRequestItem> returnItems, List<OrderItem> lockedItems) {
        if (returnItems.isEmpty() || lockedItems.isEmpty()) throw notAvailable();
        Map<Long, OrderItem> byId = new HashMap<>();
        for (OrderItem item : lockedItems) byId.put(item.getId(), item);
        for (ReturnRequestItem item : returnItems) {
            OrderItem locked = byId.get(item.getOrderItem().getId());
            if (locked == null || item.getReturnRequest() != request || locked.getSellerOrder() != sellerOrder
                    || item.getInspectionResult() == null || item.getQuantity() <= 0
                    || item.getRestockedQuantity() != 0 || item.getQuantity() > locked.getReturnableQuantity()) {
                throw notAvailable();
            }
        }
        return byId;
    }

    private OrderException notAvailable() { return new OrderException("현재 상태에서는 반품을 완료할 수 없습니다."); }
}
