package com.giftmarket.payment.service;

import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.*;
import com.giftmarket.order.service.OrderInventoryService;
import com.giftmarket.payment.entity.PaymentCancellation;
import com.giftmarket.payment.entity.PaymentCancellationStatus;
import com.giftmarket.payment.entity.PaymentCancellationType;
import com.giftmarket.payment.exception.PaymentException;
import com.giftmarket.settlement.service.CancellationSettlementLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FullOrderCancellationCompletionService {
    private final FullOrderCancellationPolicy policy;
    private final SellerOrderRepository sellerOrders;
    private final OrderItemRepository orderItems;
    private final OrderCancellationRepository cancellations;
    private final OrderCancellationItemRepository cancellationItems;
    private final OrderInventoryService inventory;
    private final CancellationSettlementLedgerService ledger;

    /** Caller holds Payment -> Order -> PaymentCancellation; all effects share its transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(Order order, PaymentCancellation paymentCancellation) {
        if (paymentCancellation.getType() != PaymentCancellationType.FULL
                || paymentCancellation.getStatus() != PaymentCancellationStatus.SUCCEEDED
                || paymentCancellation.getPayment().getOrder() != order) {
            throw new PaymentException("전체취소 완료 정보를 확인할 수 없습니다.");
        }
        policy.validate(order, SellerOrderStatus.PAID);
        List<SellerCancellation> plans = new ArrayList<>();
        long total = 0L;
        try {
            for (SellerOrder sellerOrder : sellerOrders.findAllByOrderIdForUpdate(order.getId())) {
                List<OrderItem> items = orderItems.findAllBySellerOrderIdForUpdate(sellerOrder.getId());
                long amount = 0L;
                for (OrderItem item : items) {
                    amount = Math.addExact(amount, Math.addExact(
                            Math.multiplyExact(item.getUnitPrice(), (long) item.getQuantity()),
                            item.getShippingFee()));
                }
                total = Math.addExact(total, amount);
                plans.add(new SellerCancellation(sellerOrder, items, amount));
            }
        } catch (ArithmeticException exception) {
            throw new PaymentException("전체취소 상품·배송비 금액을 안전하게 계산할 수 없습니다.");
        }
        if (plans.isEmpty() || total != paymentCancellation.getAmount()) {
            throw new PaymentException("전체취소 금액이 주문 상품·배송비 snapshot과 일치하지 않습니다.");
        }

        // One restoration across all sellers preserves the existing product/variant lock order.
        inventory.restore(order.getId());
        for (SellerCancellation plan : plans) {
            OrderCancellation cancellation = cancellations.save(OrderCancellation.createProcessing(
                    order, plan.sellerOrder(),
                    "FULL:" + paymentCancellation.getId() + ":SO:" + plan.sellerOrder().getId(),
                    paymentCancellation.getReason(), paymentCancellation.getRequestedAt()));
            for (OrderItem item : plan.items()) {
                cancellationItems.save(OrderCancellationItem.create(cancellation, item, item.getQuantity()));
                item.confirmCancellation(item.getQuantity());
            }
            cancellation.complete(paymentCancellation.getCanceledAt());
            plan.sellerOrder().cancel();
            ledger.recordFullCancellation(cancellation, paymentCancellation, plan.sellerOrder(), plan.amount());
        }
    }

    private record SellerCancellation(SellerOrder sellerOrder, List<OrderItem> items, long amount) { }
}
