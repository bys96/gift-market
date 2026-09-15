package com.giftmarket.settlement.service;

import com.giftmarket.order.entity.OrderCancellation;
import com.giftmarket.order.entity.OrderCancellationItem;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.ReturnRequest;
import com.giftmarket.order.entity.ReturnRequestStatus;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.repository.OrderCancellationItemRepository;
import com.giftmarket.order.repository.ReturnRequestRepository;
import com.giftmarket.payment.entity.PaymentCancellation;
import com.giftmarket.payment.entity.PaymentCancellationStatus;
import com.giftmarket.payment.repository.PaymentCancellationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RefundedProductAmountAggregatorTest {

    @Mock PaymentCancellationRepository paymentCancellationRepository;
    @Mock OrderCancellationItemRepository cancellationItemRepository;
    @Mock ReturnRequestRepository returnRequestRepository;
    @Mock SellerOrder sellerOrder;
    @Mock PaymentCancellation paymentCancellation;
    @Mock OrderCancellation cancellation;
    @Mock OrderCancellationItem cancellationItem;
    @Mock OrderItem orderItem;
    @Mock ReturnRequest returnRequest;

    @Test
    void combinesSucceededCancellationProductsAndCompletedReturnProducts() {
        given(sellerOrder.getId()).willReturn(10L);
        given(paymentCancellationRepository
                .findAllByOrderCancellationSellerOrderIdAndStatusOrderByCanceledAtAscIdAsc(
                        10L,
                        PaymentCancellationStatus.SUCCEEDED
                )).willReturn(List.of(paymentCancellation));
        given(paymentCancellation.getOrderCancellation()).willReturn(cancellation);
        given(cancellation.getId()).willReturn(20L);
        given(paymentCancellation.getCanceledAt()).willReturn(
                LocalDateTime.of(2026, 9, 20, 10, 0)
        );
        given(paymentCancellation.getAmount()).willReturn(3_000L);
        given(cancellationItemRepository
                .findAllByOrderCancellationIdInOrderByOrderCancellationIdAscOrderItemIdAsc(
                        List.of(20L)
                )).willReturn(List.of(cancellationItem));
        given(cancellationItem.getOrderItem()).willReturn(orderItem);
        given(cancellationItem.getQuantity()).willReturn(1);
        given(orderItem.getUnitPrice()).willReturn(3_333L);
        given(returnRequestRepository
                .findAllBySellerOrderIdAndStatusOrderByCompletedAtAscIdAsc(
                        10L,
                        ReturnRequestStatus.COMPLETED
                )).willReturn(List.of(returnRequest));
        given(returnRequest.getSellerOrder()).willReturn(sellerOrder);
        given(returnRequest.getCompletedAt()).willReturn(
                LocalDateTime.of(2026, 9, 25, 10, 0)
        );
        given(returnRequest.getProductRefundAmount()).willReturn(3_333L);

        RefundedProductAmountAggregator aggregator = new RefundedProductAmountAggregator(
                paymentCancellationRepository,
                cancellationItemRepository,
                returnRequestRepository
        );

        assertThat(aggregator.calculate(sellerOrder)).isEqualTo(6_666L);
    }
}
