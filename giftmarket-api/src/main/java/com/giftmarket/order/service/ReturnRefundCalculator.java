package com.giftmarket.order.service;

import com.giftmarket.order.dto.response.ReturnRefundCalculation;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.ReturnRequestItem;
import com.giftmarket.order.entity.ReturnResponsibility;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.exception.OrderException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ReturnRefundCalculator {

    public ReturnRefundCalculation calculate(
            SellerOrder sellerOrder,
            ReturnResponsibility responsibility,
            List<OrderItem> sellerOrderItems,
            List<ReturnRequestItem> returnItems,
            Map<Long, Long> otherCalculatedQuantities,
            boolean originalShippingAlreadyClaimed
    ) {
        if (sellerOrder == null || responsibility == null
                || sellerOrderItems == null || sellerOrderItems.isEmpty()
                || returnItems == null || returnItems.isEmpty()
                || otherCalculatedQuantities == null) {
            throw calculationNotAvailable();
        }
        Map<Long, OrderItem> itemsById = new HashMap<>();
        for (OrderItem item : sellerOrderItems) {
            validateSnapshot(item, sellerOrder);
            if (itemsById.put(item.getId(), item) != null) throw calculationNotAvailable();
        }
        Map<Long, Integer> currentQuantities = new HashMap<>();
        long productRefundAmount = 0L;
        long maxReturnFee = 0L;
        long maxExchangeFee = 0L;
        try {
            for (ReturnRequestItem returnItem : returnItems) {
                Long itemId = returnItem.getOrderItem().getId();
                OrderItem item = itemsById.get(itemId);
                int quantity = returnItem.getQuantity();
                if (item == null || quantity <= 0
                        || currentQuantities.put(itemId, quantity) != null) {
                    throw calculationNotAvailable();
                }
                long baseAvailable = (long) item.getQuantity()
                        - item.getCanceledQuantity() - item.getReturnedQuantity();
                long reserved = otherCalculatedQuantities.getOrDefault(itemId, 0L);
                if (reserved < 0L || quantity > baseAvailable - reserved) {
                    throw calculationNotAvailable();
                }
                productRefundAmount = Math.addExact(productRefundAmount,
                        Math.multiplyExact(item.getUnitPrice(), (long) quantity));
                maxReturnFee = Math.max(maxReturnFee, item.getReturnShippingFee());
                maxExchangeFee = Math.max(maxExchangeFee, item.getExchangeShippingFee());
            }

            boolean fullReturn = true;
            for (OrderItem item : sellerOrderItems) {
                long remaining = (long) item.getQuantity()
                        - item.getCanceledQuantity()
                        - item.getReturnedQuantity()
                        - otherCalculatedQuantities.getOrDefault(item.getId(), 0L)
                        - currentQuantities.getOrDefault(item.getId(), 0);
                if (remaining < 0L) throw calculationNotAvailable();
                if (remaining > 0L) fullReturn = false;
            }
            long originalShippingRefundAmount = fullReturn && !originalShippingAlreadyClaimed
                    ? sumOriginalShippingFee(sellerOrderItems) : 0L;
            long returnShippingCharge = responsibility == ReturnResponsibility.SELLER
                    ? 0L : (fullReturn ? maxExchangeFee : maxReturnFee);
            long refundAmount = Math.subtractExact(
                    Math.addExact(productRefundAmount, originalShippingRefundAmount),
                    returnShippingCharge
            );
            if (productRefundAmount < 0L || originalShippingRefundAmount < 0L
                    || returnShippingCharge < 0L || refundAmount < 0L) {
                throw calculationNotAvailable();
            }
            return new ReturnRefundCalculation(
                    productRefundAmount, originalShippingRefundAmount,
                    returnShippingCharge, refundAmount, fullReturn
            );
        } catch (ArithmeticException exception) {
            throw new OrderException("반품 환불 금액을 안전하게 계산할 수 없습니다.");
        }
    }

    private long sumOriginalShippingFee(List<OrderItem> items) {
        long amount = 0L;
        for (OrderItem item : items) amount = Math.addExact(amount, item.getShippingFee());
        return amount;
    }

    private void validateSnapshot(OrderItem item, SellerOrder sellerOrder) {
        if (item == null || item.getSellerOrder() != sellerOrder
                || item.getQuantity() == null || item.getQuantity() <= 0
                || item.getCanceledQuantity() < 0 || item.getReturnedQuantity() < 0
                || (long) item.getCanceledQuantity() + item.getReturnedQuantity() > item.getQuantity()
                || item.getUnitPrice() == null || item.getUnitPrice() <= 0L
                || item.getShippingFee() == null || item.getShippingFee() < 0L
                || item.getReturnShippingFee() == null || item.getReturnShippingFee() < 0L
                || item.getExchangeShippingFee() == null || item.getExchangeShippingFee() < 0L) {
            throw calculationNotAvailable();
        }
    }

    private OrderException calculationNotAvailable() {
        return new OrderException("현재 반품 요청의 환불 금액을 계산할 수 없습니다.");
    }
}
