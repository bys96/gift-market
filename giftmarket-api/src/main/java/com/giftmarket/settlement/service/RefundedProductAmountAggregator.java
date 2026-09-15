package com.giftmarket.settlement.service;

import com.giftmarket.order.entity.OrderCancellationItem;
import com.giftmarket.order.entity.ReturnRequest;
import com.giftmarket.order.entity.ReturnRequestStatus;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.repository.OrderCancellationItemRepository;
import com.giftmarket.order.repository.ReturnRequestRepository;
import com.giftmarket.payment.entity.PaymentCancellation;
import com.giftmarket.payment.entity.PaymentCancellationStatus;
import com.giftmarket.payment.repository.PaymentCancellationRepository;
import com.giftmarket.settlement.exception.SettlementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class RefundedProductAmountAggregator {

    private final PaymentCancellationRepository paymentCancellationRepository;
    private final OrderCancellationItemRepository cancellationItemRepository;
    private final ReturnRequestRepository returnRequestRepository;

    public long calculate(SellerOrder sellerOrder) {
        if (sellerOrder == null || sellerOrder.getId() == null) {
            throw new SettlementException("환불 상품금액을 집계할 판매자 주문이 필요합니다.");
        }
        long amount = cancellationProductAmount(sellerOrder);
        for (ReturnRequest request : returnRequestRepository
                .findAllBySellerOrderIdAndStatusOrderByCompletedAtAscIdAsc(
                        sellerOrder.getId(),
                        ReturnRequestStatus.COMPLETED
                )) {
            if (request.getSellerOrder() == null
                    || !Objects.equals(request.getSellerOrder().getId(), sellerOrder.getId())
                    || request.getCompletedAt() == null
                    || request.getProductRefundAmount() == null
                    || request.getProductRefundAmount() < 0L) {
                throw new SettlementException("완료된 반품 상품환불 snapshot이 올바르지 않습니다.");
            }
            amount = add(amount, request.getProductRefundAmount());
        }
        return amount;
    }

    private long cancellationProductAmount(SellerOrder sellerOrder) {
        List<PaymentCancellation> succeeded = paymentCancellationRepository
                .findAllByOrderCancellationSellerOrderIdAndStatusOrderByCanceledAtAscIdAsc(
                        sellerOrder.getId(),
                        PaymentCancellationStatus.SUCCEEDED
                );
        if (succeeded.isEmpty()) {
            return 0L;
        }
        List<Long> cancellationIds = succeeded.stream().map(value -> {
            if (value.getOrderCancellation() == null
                    || value.getCanceledAt() == null
                    || value.getAmount() == null || value.getAmount() <= 0L) {
                throw new SettlementException("성공한 취소 환불 이력이 올바르지 않습니다.");
            }
            return value.getOrderCancellation().getId();
        }).toList();
        long amount = 0L;
        for (OrderCancellationItem item : cancellationItemRepository
                .findAllByOrderCancellationIdInOrderByOrderCancellationIdAscOrderItemIdAsc(
                        cancellationIds
                )) {
            if (item.getOrderItem() == null || item.getOrderItem().getUnitPrice() == null
                    || item.getOrderItem().getUnitPrice() <= 0L || item.getQuantity() <= 0) {
                throw new SettlementException("완료된 취소 상품환불 snapshot이 올바르지 않습니다.");
            }
            try {
                amount = add(amount, Math.multiplyExact(
                        item.getOrderItem().getUnitPrice(),
                        (long) item.getQuantity()
                ));
            } catch (ArithmeticException exception) {
                throw new SettlementException("누적 상품 환불액을 안전하게 계산할 수 없습니다.");
            }
        }
        return amount;
    }

    private long add(long first, long second) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException exception) {
            throw new SettlementException("누적 상품 환불액을 안전하게 계산할 수 없습니다.");
        }
    }
}
