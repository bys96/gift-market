package com.giftmarket.settlement.service;

import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentStatus;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.settlement.config.SettlementProperties;
import com.giftmarket.settlement.exception.SettlementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InitialSettlementLedgerServiceTest {

    private static final Long ORDER_ID = 1L;
    private static final LocalDateTime APPROVED_AT = LocalDateTime.of(2026, 9, 15, 12, 30);

    @Mock SellerOrderRepository sellerOrderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock SettlementLedgerService ledgerService;
    @Mock Payment payment;
    @Mock Order order;

    private SettlementProperties properties;
    private InitialSettlementLedgerService service;

    @BeforeEach
    void setUp() {
        properties = new SettlementProperties();
        service = new InitialSettlementLedgerService(
                sellerOrderRepository,
                orderItemRepository,
                ledgerService,
                new SettlementCommissionCalculator(),
                properties
        );
        given(order.getId()).willReturn(ORDER_ID);
        given(order.getStatus()).willReturn(OrderStatus.PAID);
        given(payment.getOrder()).willReturn(order);
        given(payment.getStatus()).willReturn(PaymentStatus.PAID);
        lenient().when(payment.getApprovedAt()).thenReturn(APPROVED_AT);
    }

    @Test
    void createsProductShippingAndCommissionForSingleSellerOrder() {
        SellerOrder sellerOrder = sellerOrder(10L, 100L);
        OrderItem first = orderItem(sellerOrder, 6_000L, 3_000L);
        OrderItem second = orderItem(sellerOrder, 4_000L, 0L);
        givenAllocation(
                List.of(sellerOrder),
                List.of(first, second),
                10_000L,
                3_000L,
                13_000L
        );

        service.recordInitialSales(payment, order);

        ArgumentCaptor<SettlementLedgerCommand> product = ArgumentCaptor.forClass(
                SettlementLedgerCommand.class
        );
        ArgumentCaptor<SettlementLedgerCommand> shipping = ArgumentCaptor.forClass(
                SettlementLedgerCommand.class
        );
        ArgumentCaptor<SettlementLedgerCommand> commission = ArgumentCaptor.forClass(
                SettlementLedgerCommand.class
        );
        verify(ledgerService).recordProductSale(product.capture());
        verify(ledgerService).recordShippingSale(shipping.capture());
        verify(ledgerService).recordCommission(commission.capture());

        assertThat(product.getValue().amount()).isEqualTo(10_000L);
        assertThat(product.getValue().sourceId()).isEqualTo(10L);
        assertThat(product.getValue().sourceDetailKey()).isEqualTo("SALE_PRODUCT");
        assertThat(product.getValue().commissionRateBps()).isEqualTo(1_000);
        assertThat(product.getValue().commissionBaseAmount()).isEqualTo(10_000L);
        assertThat(shipping.getValue().amount()).isEqualTo(3_000L);
        assertThat(shipping.getValue().sourceId()).isEqualTo(10L);
        assertThat(shipping.getValue().sourceDetailKey()).isEqualTo("SALE_SHIPPING");
        assertThat(commission.getValue().amount()).isEqualTo(1_000L);
        assertThat(commission.getValue().sourceId()).isEqualTo(10L);
        assertThat(commission.getValue().sourceDetailKey()).isEqualTo("COMMISSION");
        assertThat(commission.getValue().commissionBaseAmount()).isEqualTo(10_000L);
    }

    @Test
    void createsIndependentLedgersForMultipleSellers() {
        SellerOrder firstSellerOrder = sellerOrder(10L, 100L);
        SellerOrder secondSellerOrder = sellerOrder(20L, 200L);
        givenAllocation(
                List.of(firstSellerOrder, secondSellerOrder),
                List.of(
                        orderItem(firstSellerOrder, 10_000L, 3_000L),
                        orderItem(secondSellerOrder, 20_000L, 2_000L)
                ),
                30_000L,
                5_000L,
                35_000L
        );

        service.recordInitialSales(payment, order);

        ArgumentCaptor<SettlementLedgerCommand> product = ArgumentCaptor.forClass(
                SettlementLedgerCommand.class
        );
        verify(ledgerService, times(2)).recordProductSale(product.capture());
        assertThat(product.getAllValues())
                .extracting(
                        SettlementLedgerCommand::sellerId,
                        SettlementLedgerCommand::sellerOrderId,
                        SettlementLedgerCommand::amount
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(100L, 10L, 10_000L),
                        org.assertj.core.groups.Tuple.tuple(200L, 20L, 20_000L)
                );
    }

    @Test
    void freeShippingDoesNotCreateShippingLedger() {
        SellerOrder sellerOrder = sellerOrder(10L, 100L);
        givenAllocation(
                List.of(sellerOrder),
                List.of(orderItem(sellerOrder, 10_000L, 0L)),
                10_000L,
                0L,
                10_000L
        );

        service.recordInitialSales(payment, order);

        verify(ledgerService, never()).recordShippingSale(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void zeroCommissionSkipsCommissionButSnapshotsZeroRateOnProductSale() {
        properties.setCommissionRateBps(0);
        SellerOrder sellerOrder = sellerOrder(10L, 100L);
        givenAllocation(
                List.of(sellerOrder),
                List.of(orderItem(sellerOrder, 10_000L, 0L)),
                10_000L,
                0L,
                10_000L
        );

        service.recordInitialSales(payment, order);

        ArgumentCaptor<SettlementLedgerCommand> product = ArgumentCaptor.forClass(
                SettlementLedgerCommand.class
        );
        verify(ledgerService).recordProductSale(product.capture());
        verify(ledgerService, never()).recordCommission(
                org.mockito.ArgumentMatchers.any()
        );
        assertThat(product.getValue().commissionRateBps()).isZero();
        assertThat(product.getValue().commissionBaseAmount()).isEqualTo(10_000L);
    }

    @Test
    void commissionUsesProductAmountAndExcludesShipping() {
        properties.setCommissionRateBps(500);
        SellerOrder sellerOrder = sellerOrder(10L, 100L);
        givenAllocation(
                List.of(sellerOrder),
                List.of(orderItem(sellerOrder, 9_999L, 5_000L)),
                9_999L,
                5_000L,
                14_999L
        );

        service.recordInitialSales(payment, order);

        ArgumentCaptor<SettlementLedgerCommand> commission = ArgumentCaptor.forClass(
                SettlementLedgerCommand.class
        );
        verify(ledgerService).recordCommission(commission.capture());
        assertThat(commission.getValue().amount()).isEqualTo(499L);
        assertThat(commission.getValue().commissionBaseAmount()).isEqualTo(9_999L);
        assertThat(commission.getValue().commissionRateBps()).isEqualTo(500);
    }

    @Test
    void everyInitialLedgerUsesPaymentApprovedAtAndNullEligibility() {
        SellerOrder sellerOrder = sellerOrder(10L, 100L);
        givenAllocation(
                List.of(sellerOrder),
                List.of(orderItem(sellerOrder, 10_000L, 3_000L)),
                10_000L,
                3_000L,
                13_000L
        );

        service.recordInitialSales(payment, order);

        ArgumentCaptor<SettlementLedgerCommand> commands = ArgumentCaptor.forClass(
                SettlementLedgerCommand.class
        );
        verify(ledgerService).recordProductSale(commands.capture());
        verify(ledgerService).recordShippingSale(commands.capture());
        verify(ledgerService).recordCommission(commands.capture());
        assertThat(commands.getAllValues())
                .allSatisfy(command -> {
                    assertThat(command.occurredAt()).isEqualTo(APPROVED_AT);
                    assertThat(command.eligibleAt()).isNull();
                });
    }

    @Test
    void rejectsProductAllocationMismatch() {
        SellerOrder sellerOrder = sellerOrder(10L, 100L);
        givenAllocation(
                List.of(sellerOrder),
                List.of(orderItem(sellerOrder, 9_000L, 0L)),
                10_000L,
                0L,
                10_000L
        );

        assertThatThrownBy(() -> service.recordInitialSales(payment, order))
                .isInstanceOf(SettlementException.class)
                .hasMessageContaining("상품 매출 합계");
        verifyNoLedgerCreated();
    }

    @Test
    void rejectsShippingAllocationMismatch() {
        SellerOrder sellerOrder = sellerOrder(10L, 100L);
        givenAllocation(
                List.of(sellerOrder),
                List.of(orderItem(sellerOrder, 10_000L, 2_000L)),
                10_000L,
                3_000L,
                13_000L
        );

        assertThatThrownBy(() -> service.recordInitialSales(payment, order))
                .isInstanceOf(SettlementException.class)
                .hasMessageContaining("배송비 합계");
        verifyNoLedgerCreated();
    }

    @Test
    void rejectsSellerOrderTotalMismatch() {
        SellerOrder sellerOrder = sellerOrder(10L, 100L);
        givenAllocation(
                List.of(sellerOrder),
                List.of(orderItem(sellerOrder, 10_000L, 3_000L)),
                10_000L,
                3_000L,
                14_000L
        );

        assertThatThrownBy(() -> service.recordInitialSales(payment, order))
                .isInstanceOf(SettlementException.class)
                .hasMessageContaining("총액 합계");
        verifyNoLedgerCreated();
    }

    @Test
    void rejectsPaymentAndOrderAmountMismatch() {
        SellerOrder sellerOrder = sellerOrder(10L, 100L);
        givenAllocation(
                List.of(sellerOrder),
                List.of(orderItem(sellerOrder, 10_000L, 3_000L)),
                10_000L,
                3_000L,
                13_000L
        );
        given(payment.getAmount()).willReturn(12_999L);

        assertThatThrownBy(() -> service.recordInitialSales(payment, order))
                .isInstanceOf(SettlementException.class)
                .hasMessageContaining("결제 금액");
        verifyNoLedgerCreated();
    }

    @Test
    void rejectsSellerAllocationOverflow() {
        SellerOrder sellerOrder = sellerOrder(10L, 100L);
        OrderItem first = orderItem(sellerOrder, Long.MAX_VALUE, 0L);
        OrderItem second = orderItem(sellerOrder, 1L, 0L);
        given(sellerOrderRepository.findAllByOrderIdOrderByIdAsc(ORDER_ID))
                .willReturn(List.of(sellerOrder));
        given(orderItemRepository.findAllByOrderIdOrderByIdAsc(ORDER_ID))
                .willReturn(List.of(first, second));

        assertThatThrownBy(() -> service.recordInitialSales(payment, order))
                .isInstanceOf(SettlementException.class)
                .hasMessageContaining("안전하게 합산");
        verifyNoLedgerCreated();
    }

    private void givenAllocation(
            List<SellerOrder> sellerOrders,
            List<OrderItem> orderItems,
            long productTotal,
            long shippingTotal,
            long orderTotal
    ) {
        given(sellerOrderRepository.findAllByOrderIdOrderByIdAsc(ORDER_ID))
                .willReturn(sellerOrders);
        given(orderItemRepository.findAllByOrderIdOrderByIdAsc(ORDER_ID))
                .willReturn(orderItems);
        given(order.getTotalProductAmount()).willReturn(productTotal);
        given(order.getTotalShippingFee()).willReturn(shippingTotal);
        given(order.getTotalAmount()).willReturn(orderTotal);
        given(payment.getAmount()).willReturn(orderTotal);
    }

    private SellerOrder sellerOrder(Long sellerOrderId, Long sellerId) {
        Seller seller = org.mockito.Mockito.mock(Seller.class);
        given(seller.getId()).willReturn(sellerId);
        SellerOrder sellerOrder = org.mockito.Mockito.mock(SellerOrder.class);
        given(sellerOrder.getId()).willReturn(sellerOrderId);
        given(sellerOrder.getSeller()).willReturn(seller);
        given(sellerOrder.getOrder()).willReturn(order);
        given(sellerOrder.getStatus()).willReturn(SellerOrderStatus.PAID);
        return sellerOrder;
    }

    private OrderItem orderItem(
            SellerOrder sellerOrder,
            long totalPrice,
            long shippingFee
    ) {
        OrderItem orderItem = org.mockito.Mockito.mock(OrderItem.class);
        Seller seller = sellerOrder.getSeller();
        given(orderItem.getOrder()).willReturn(order);
        given(orderItem.getSellerOrder()).willReturn(sellerOrder);
        given(orderItem.getSeller()).willReturn(seller);
        given(orderItem.getTotalPrice()).willReturn(totalPrice);
        given(orderItem.getShippingFee()).willReturn(shippingFee);
        return orderItem;
    }

    private void verifyNoLedgerCreated() {
        verify(ledgerService, never()).recordProductSale(
                org.mockito.ArgumentMatchers.any()
        );
        verify(ledgerService, never()).recordShippingSale(
                org.mockito.ArgumentMatchers.any()
        );
        verify(ledgerService, never()).recordCommission(
                org.mockito.ArgumentMatchers.any()
        );
    }
}
