package com.giftmarket.order.service;

import com.giftmarket.order.dto.response.ReturnRefundCalculation;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.product.entity.Product;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ReturnRefundCalculatorTest {

    private ReturnRefundCalculator calculator;
    private Order order;
    private SellerOrder sellerOrder;

    @BeforeEach
    void setUp() {
        calculator = new ReturnRefundCalculator();
        order = Order.createPendingPayment(
                "GM", mock(User.class), 100_000L, 0L,
                "구매자", "010", "12345", "서울", null
        );
        ReflectionTestUtils.setField(order, "id", 1L);
        sellerOrder = SellerOrder.createPendingPayment(order, mock(Seller.class));
        ReflectionTestUtils.setField(sellerOrder, "id", 2L);
    }

    @Test
    void calculatesPartialQuantityFromOrderItemSnapshot() {
        OrderItem item = item(10L, 12_000L, 3, 2_000L, 3_000L, 6_000L, false);
        ReturnRefundCalculation result = calculate(ReturnResponsibility.BUYER,
                List.of(item), List.of(returnItem(item, 2)), Map.of(), false);

        assertThat(result.productRefundAmount()).isEqualTo(24_000L);
        assertThat(result.returnShippingCharge()).isEqualTo(3_000L);
        assertThat(result.refundAmount()).isEqualTo(21_000L);
        assertThat(result.fullSellerOrderReturn()).isFalse();
    }

    @Test
    void addsMultipleItemsAndUsesMaximumOneWayFeeOnlyOnce() {
        OrderItem first = item(10L, 10_000L, 2, 2_000L, 3_000L, 6_000L, false);
        OrderItem second = item(11L, 20_000L, 2, 1_000L, 5_000L, 8_000L, false);
        ReturnRefundCalculation result = calculate(ReturnResponsibility.BUYER,
                List.of(first, second), List.of(returnItem(first, 1), returnItem(second, 1)),
                Map.of(), false);

        assertThat(result.productRefundAmount()).isEqualTo(30_000L);
        assertThat(result.returnShippingCharge()).isEqualTo(5_000L);
        assertThat(result.refundAmount()).isEqualTo(25_000L);
    }

    @Test
    void sellerResponsibilityPartialHasNoShippingAmounts() {
        OrderItem item = item(10L, 10_000L, 2, 3_000L, 4_000L, 8_000L, false);
        ReturnRefundCalculation result = calculate(ReturnResponsibility.SELLER,
                List.of(item), List.of(returnItem(item, 1)), Map.of(), false);
        assertThat(result.originalShippingRefundAmount()).isZero();
        assertThat(result.returnShippingCharge()).isZero();
        assertThat(result.refundAmount()).isEqualTo(10_000L);
    }

    @Test
    void sellerResponsibilityFullRefundsOriginalShippingWithoutCharge() {
        OrderItem item = item(10L, 30_000L, 1, 3_000L, 3_000L, 6_000L, false);
        ReturnRefundCalculation result = calculate(ReturnResponsibility.SELLER,
                List.of(item), List.of(returnItem(item, 1)), Map.of(), false);
        assertThat(result.fullSellerOrderReturn()).isTrue();
        assertThat(result.originalShippingRefundAmount()).isEqualTo(3_000L);
        assertThat(result.returnShippingCharge()).isZero();
        assertThat(result.refundAmount()).isEqualTo(33_000L);
    }

    @Test
    void buyerFullFreeShippingDeductsRoundTripFee() {
        OrderItem item = item(10L, 30_000L, 1, 0L, 3_000L, 6_000L, true);
        ReturnRefundCalculation result = calculate(ReturnResponsibility.BUYER,
                List.of(item), List.of(returnItem(item, 1)), Map.of(), false);
        assertThat(result.originalShippingRefundAmount()).isZero();
        assertThat(result.returnShippingCharge()).isEqualTo(6_000L);
        assertThat(result.refundAmount()).isEqualTo(24_000L);
    }

    @Test
    void buyerFullPaidShippingRefundsOriginalAndUsesMaximumRoundTripFee() {
        OrderItem first = item(10L, 10_000L, 1, 3_000L, 3_000L, 6_000L, false);
        OrderItem second = item(11L, 20_000L, 1, 2_000L, 4_000L, 8_000L, false);
        ReturnRefundCalculation result = calculate(ReturnResponsibility.BUYER,
                List.of(first, second), List.of(returnItem(first, 1), returnItem(second, 1)),
                Map.of(), false);
        assertThat(result.originalShippingRefundAmount()).isEqualTo(5_000L);
        assertThat(result.returnShippingCharge()).isEqualTo(8_000L);
        assertThat(result.refundAmount()).isEqualTo(27_000L);
    }

    @Test
    void fullReturnIncludesCanceledReturnedCurrentAndOtherCalculatedQuantities() {
        OrderItem first = item(10L, 10_000L, 4, 1_000L, 3_000L, 6_000L, false);
        ReflectionTestUtils.setField(first, "canceledQuantity", 1);
        ReflectionTestUtils.setField(first, "returnedQuantity", 1);
        ReturnRefundCalculation result = calculate(ReturnResponsibility.SELLER,
                List.of(first), List.of(returnItem(first, 1)), Map.of(10L, 1L), false);
        assertThat(result.fullSellerOrderReturn()).isTrue();
        assertThat(result.originalShippingRefundAmount()).isEqualTo(1_000L);
    }

    @Test
    void anyRemainingSellerOrderItemMakesPartialReturn() {
        OrderItem first = item(10L, 10_000L, 1, 2_000L, 3_000L, 6_000L, false);
        OrderItem second = item(11L, 20_000L, 1, 1_000L, 3_000L, 6_000L, false);
        ReturnRefundCalculation result = calculate(ReturnResponsibility.SELLER,
                List.of(first, second), List.of(returnItem(first, 1)), Map.of(), false);
        assertThat(result.fullSellerOrderReturn()).isFalse();
        assertThat(result.originalShippingRefundAmount()).isZero();
    }

    @Test
    void claimedOriginalShippingIsNeverRefundedAgain() {
        OrderItem item = item(10L, 30_000L, 1, 3_000L, 3_000L, 6_000L, false);
        ReturnRefundCalculation result = calculate(ReturnResponsibility.SELLER,
                List.of(item), List.of(returnItem(item, 1)), Map.of(), true);
        assertThat(result.fullSellerOrderReturn()).isTrue();
        assertThat(result.originalShippingRefundAmount()).isZero();
        assertThat(result.refundAmount()).isEqualTo(30_000L);
    }

    @Test
    void zeroRefundIsAllowed() {
        OrderItem item = item(10L, 6_000L, 1, 0L, 3_000L, 6_000L, true);
        ReturnRefundCalculation result = calculate(ReturnResponsibility.BUYER,
                List.of(item), List.of(returnItem(item, 1)), Map.of(), false);
        assertThat(result.refundAmount()).isZero();
    }

    @Test
    void negativeRefundAndOverflowAreRejected() {
        OrderItem low = item(10L, 1_000L, 1, 0L, 3_000L, 6_000L, true);
        assertThatThrownBy(() -> calculate(ReturnResponsibility.BUYER,
                List.of(low), List.of(returnItem(low, 1)), Map.of(), false))
                .isInstanceOf(OrderException.class);

        OrderItem huge = item(11L, Long.MAX_VALUE, 2, 0L, 0L, 0L, true);
        assertThatThrownBy(() -> calculate(ReturnResponsibility.SELLER,
                List.of(huge), List.of(returnItem(huge, 2)), Map.of(), false))
                .isInstanceOf(OrderException.class).hasMessageContaining("안전하게");
    }

    private ReturnRefundCalculation calculate(
            ReturnResponsibility responsibility,
            List<OrderItem> items,
            List<ReturnRequestItem> returns,
            Map<Long, Long> other,
            boolean claimed
    ) {
        return calculator.calculate(sellerOrder, responsibility, items, returns, other, claimed);
    }

    private ReturnRequestItem returnItem(OrderItem item, int quantity) {
        ReturnRequest request = ReturnRequest.createRequested(
                order, sellerOrder, UUID.randomUUID().toString(), ReturnReasonType.OTHER,
                "사유", "구매자", "010", "12345", "서울", null, LocalDateTime.now()
        );
        request.confirmResponsibility(ReturnResponsibility.BUYER);
        return ReturnRequestItem.create(request, item, quantity);
    }

    private OrderItem item(
            long id, long unitPrice, int quantity, long shippingFee,
            long returnFee, long exchangeFee, boolean freeShipping
    ) {
        OrderItem value = OrderItem.create(
                order, mock(Product.class), null, mock(Seller.class), sellerOrder, null,
                "상품", null, "상점", null, null, unitPrice, 0L, quantity,
                freeShipping, shippingFee, returnFee, exchangeFee
        );
        ReflectionTestUtils.setField(value, "id", id);
        return value;
    }
}
