package com.giftmarket.settlement.service;

import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderCancellation;
import com.giftmarket.order.entity.OrderCancellationItem;
import com.giftmarket.order.entity.OrderCancellationRequesterType;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.repository.OrderCancellationItemRepository;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentCancellation;
import com.giftmarket.payment.entity.PaymentCancellationStatus;
import com.giftmarket.payment.entity.PaymentCancellationType;
import com.giftmarket.payment.repository.PaymentCancellationRepository;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.settlement.entity.SettlementLedgerEntry;
import com.giftmarket.settlement.entity.SettlementLedgerSourceType;
import com.giftmarket.settlement.entity.SettlementLedgerType;
import com.giftmarket.settlement.entity.Settlement;
import com.giftmarket.settlement.exception.SettlementException;
import com.giftmarket.settlement.repository.SettlementLedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CancellationSettlementLedgerServiceTest {

    private static final Long SELLER_ID = 1L;
    private static final Long SELLER_ORDER_ID = 2L;
    private static final Long CANCELLATION_ID = 3L;
    private static final Long PAYMENT_CANCELLATION_ID = 4L;
    private static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 9, 20, 15, 0);

    @Mock OrderCancellationItemRepository cancellationItemRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock PaymentCancellationRepository paymentCancellationRepository;
    @Mock SettlementLedgerEntryRepository ledgerRepository;
    @Mock SettlementLedgerService ledgerService;
    @Mock SettlementEligibilityService eligibilityService;
    @Mock RefundedProductAmountAggregator refundedProductAmountAggregator;
    @Mock Seller seller;
    @Mock SellerOrder sellerOrder;
    @Mock Order order;
    @Mock Payment payment;
    @Mock OrderCancellation cancellation;
    @Mock PaymentCancellation paymentCancellation;
    @Mock OrderCancellationItem cancellationItem;
    @Mock OrderItem orderItem;
    @Mock SettlementLedgerEntry productSale;
    @Mock SettlementLedgerEntry commission;

    private CancellationSettlementLedgerService service;

    @BeforeEach
    void setUp() {
        service = new CancellationSettlementLedgerService(
                cancellationItemRepository,
                orderItemRepository,
                ledgerRepository,
                ledgerService,
                new SettlementCommissionCalculator(),
                eligibilityService,
                refundedProductAmountAggregator
        );
        given(seller.getId()).willReturn(SELLER_ID);
        given(sellerOrder.getId()).willReturn(SELLER_ORDER_ID);
        given(sellerOrder.getSeller()).willReturn(seller);
        given(sellerOrder.getOrder()).willReturn(order);
        given(sellerOrder.getStatus()).willReturn(SellerOrderStatus.PAID);
        given(cancellation.getId()).willReturn(CANCELLATION_ID);
        given(cancellation.getOrder()).willReturn(order);
        given(cancellation.getSellerOrder()).willReturn(sellerOrder);
        given(cancellation.getStatus()).willReturn(OrderCancellationStatus.COMPLETED);
        given(cancellation.getRequesterType()).willReturn(OrderCancellationRequesterType.BUYER);
        given(paymentCancellation.getId()).willReturn(PAYMENT_CANCELLATION_ID);
        given(paymentCancellation.getPayment()).willReturn(payment);
        given(payment.getOrder()).willReturn(order);
        given(paymentCancellation.getOrderCancellation()).willReturn(cancellation);
        given(paymentCancellation.getType()).willReturn(PaymentCancellationType.PARTIAL);
        given(paymentCancellation.getStatus()).willReturn(PaymentCancellationStatus.SUCCEEDED);
        given(paymentCancellation.getCanceledAt()).willReturn(CANCELED_AT);
        given(paymentCancellation.getAmount()).willReturn(3_000L);

        given(cancellationItem.getOrderCancellation()).willReturn(cancellation);
        given(cancellationItem.getOrderItem()).willReturn(orderItem);
        given(cancellationItem.getQuantity()).willReturn(1);
        given(orderItem.getSellerOrder()).willReturn(sellerOrder);
        given(orderItem.getSeller()).willReturn(seller);
        given(orderItem.getUnitPrice()).willReturn(3_000L);
        given(orderItem.getShippingFee()).willReturn(3_000L);
        given(cancellationItemRepository.findAllByOrderCancellationIdOrderByIdAsc(
                CANCELLATION_ID
        )).willReturn(List.of(cancellationItem));
        given(orderItemRepository.findAllBySellerOrderIdForUpdate(SELLER_ORDER_ID))
                .willReturn(List.of(orderItem));
        given(paymentCancellationRepository
                .findAllByOrderCancellationSellerOrderIdAndStatusOrderByCanceledAtAscIdAsc(
                        SELLER_ORDER_ID,
                        PaymentCancellationStatus.SUCCEEDED
                )).willReturn(List.of(paymentCancellation));
        given(cancellationItemRepository
                .findAllByOrderCancellationIdInOrderByOrderCancellationIdAscOrderItemIdAsc(
                        List.of(CANCELLATION_ID)
                )).willReturn(List.of(cancellationItem));

        given(productSale.getType()).willReturn(SettlementLedgerType.SALE_PRODUCT);
        given(productSale.getSellerOrder()).willReturn(sellerOrder);
        given(productSale.getSeller()).willReturn(seller);
        given(productSale.getAmount()).willReturn(10_000L);
        given(productSale.getCommissionRateBps()).willReturn(1_000);
        given(productSale.getCommissionBaseAmount()).willReturn(10_000L);
        given(commission.getType()).willReturn(SettlementLedgerType.COMMISSION);
        given(commission.getAmount()).willReturn(-1_000L);
        given(commission.getCommissionRateBps()).willReturn(1_000);
        given(commission.getCommissionBaseAmount()).willReturn(10_000L);
        givenInitialLedgers(productSale, commission);
        given(ledgerRepository.sumAmountBySellerOrderIdAndType(
                SELLER_ORDER_ID,
                SettlementLedgerType.COMMISSION_REVERSAL
        )).willReturn(0L);
        given(refundedProductAmountAggregator.calculate(sellerOrder)).willReturn(3_000L);
    }

    @Test
    void partialCancellationRecordsPgRefundAndProductBasedCommissionReversal() {
        service.recordCancellation(cancellation, paymentCancellation, sellerOrder);

        SettlementLedgerCommand refund = captureRefundCommand();
        SettlementLedgerCommand reversal = captureReversalCommand();
        assertThat(refund.amount()).isEqualTo(3_000L);
        assertThat(refund.sourceDetailKey()).isEqualTo("SO:2:CANCELLATION_REFUND");
        assertThat(refund.occurredAt()).isEqualTo(CANCELED_AT);
        assertThat(refund.eligibleAt()).isNull();
        assertThat(reversal.amount()).isEqualTo(300L);
        assertThat(reversal.commissionRateBps()).isEqualTo(1_000);
        assertThat(reversal.commissionBaseAmount()).isEqualTo(3_000L);
    }

    @Test
    void fullCancellationIncludesOriginalShippingButCommissionUsesOnlyProduct() {
        given(sellerOrder.getStatus()).willReturn(SellerOrderStatus.CANCELLED);
        given(paymentCancellation.getAmount()).willReturn(6_000L);

        service.recordCancellation(cancellation, paymentCancellation, sellerOrder);

        SettlementLedgerCommand refund = captureRefundCommand();
        SettlementLedgerCommand reversal = captureReversalCommand();
        assertThat(refund.amount()).isEqualTo(6_000L);
        assertThat(refund.eligibleAt()).isEqualTo(CANCELED_AT);
        assertThat(reversal.amount()).isEqualTo(300L);
        assertThat(reversal.commissionBaseAmount()).isEqualTo(3_000L);
        verify(eligibilityService).activateFullCancellationEligibility(
                sellerOrder,
                CANCELED_AT
        );
    }

    @Test
    void fullCancellationWithFreeShippingRefundsOnlyProduct() {
        given(sellerOrder.getStatus()).willReturn(SellerOrderStatus.CANCELLED);
        given(orderItem.getShippingFee()).willReturn(0L);

        service.recordCancellation(cancellation, paymentCancellation, sellerOrder);

        assertThat(captureRefundCommand().amount()).isEqualTo(3_000L);
        assertThat(captureReversalCommand().amount()).isEqualTo(300L);
    }

    @Test
    void cumulativeRoundingUsesTargetTotalMinusAlreadyReversed() {
        given(productSale.getCommissionRateBps()).willReturn(333);
        given(commission.getAmount()).willReturn(-333L);
        given(commission.getCommissionRateBps()).willReturn(333);
        given(orderItem.getUnitPrice()).willReturn(101L);
        given(paymentCancellation.getAmount()).willReturn(101L);
        given(refundedProductAmountAggregator.calculate(sellerOrder)).willReturn(202L);
        given(ledgerRepository.sumAmountBySellerOrderIdAndType(
                SELLER_ORDER_ID,
                SettlementLedgerType.COMMISSION_REVERSAL
        )).willReturn(3L);

        service.recordCancellation(cancellation, paymentCancellation, sellerOrder);

        assertThat(captureReversalCommand().amount()).isEqualTo(3L);
    }

    @Test
    void finalProductCancellationReversesRemainingOriginalCommission() {
        given(orderItem.getUnitPrice()).willReturn(10_000L);
        given(paymentCancellation.getAmount()).willReturn(10_000L);
        given(refundedProductAmountAggregator.calculate(sellerOrder)).willReturn(10_000L);
        given(ledgerRepository.sumAmountBySellerOrderIdAndType(
                SELLER_ORDER_ID,
                SettlementLedgerType.COMMISSION_REVERSAL
        )).willReturn(900L);

        service.recordCancellation(cancellation, paymentCancellation, sellerOrder);

        assertThat(captureReversalCommand().amount()).isEqualTo(100L);
    }

    @Test
    void zeroPercentCommissionDoesNotCreateReversal() {
        given(productSale.getCommissionRateBps()).willReturn(0);
        given(commissionLedger()).willReturn(Optional.empty());

        service.recordCancellation(cancellation, paymentCancellation, sellerOrder);

        verify(ledgerService, never()).recordCommissionReversal(any());
    }

    @Test
    void nonSucceededPaymentCancellationCreatesNoLedger() {
        given(paymentCancellation.getStatus()).willReturn(PaymentCancellationStatus.REQUESTED);

        assertThatThrownBy(() -> service.recordCancellation(
                cancellation,
                paymentCancellation,
                sellerOrder
        )).isInstanceOf(SettlementException.class);
        verify(ledgerService, never()).recordCancellationRefund(any());
    }

    @Test
    void sameSuccessfulCancellationRetryIsNoOp() {
        SettlementLedgerEntry existing = org.mockito.Mockito.mock(SettlementLedgerEntry.class);
        given(existing.getType()).willReturn(SettlementLedgerType.CANCELLATION_REFUND);
        given(existing.getAmount()).willReturn(-3_000L);
        given(existing.getSellerOrder()).willReturn(sellerOrder);
        given(existing.getOccurredAt()).willReturn(CANCELED_AT);
        given(ledgerRepository.findBySourceTypeAndSourceIdAndSourceDetailKey(
                SettlementLedgerSourceType.PAYMENT_CANCELLATION,
                PAYMENT_CANCELLATION_ID,
                "SO:2:CANCELLATION_REFUND"
        )).willReturn(Optional.of(existing));

        service.recordCancellation(cancellation, paymentCancellation, sellerOrder);

        verify(ledgerService, never()).recordCancellationRefund(any());
        verify(ledgerService, never()).recordCommissionReversal(any());
    }

    @Test
    void sameSourceWithDifferentAmountIsRejected() {
        SettlementLedgerEntry existing = org.mockito.Mockito.mock(SettlementLedgerEntry.class);
        given(existing.getType()).willReturn(SettlementLedgerType.CANCELLATION_REFUND);
        given(existing.getAmount()).willReturn(-2_000L);
        given(ledgerRepository.findBySourceTypeAndSourceIdAndSourceDetailKey(
                SettlementLedgerSourceType.PAYMENT_CANCELLATION,
                PAYMENT_CANCELLATION_ID,
                "SO:2:CANCELLATION_REFUND"
        )).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.recordCancellation(
                cancellation,
                paymentCancellation,
                sellerOrder
        )).isInstanceOf(SettlementException.class);
    }

    @Test
    void sellerRequestedCancellationUsesSameCalculation() {
        given(cancellation.getRequesterType()).willReturn(OrderCancellationRequesterType.SELLER);

        service.recordCancellation(cancellation, paymentCancellation, sellerOrder);

        assertThat(captureRefundCommand().amount()).isEqualTo(3_000L);
        assertThat(captureReversalCommand().amount()).isEqualTo(300L);
    }

    @Test
    void confirmedInitialSaleIsNotMutatedAndNewRefundLedgerIsRecorded() {
        Settlement confirmedSettlement = org.mockito.Mockito.mock(Settlement.class);
        given(confirmedSettlement.isConfirmed()).willReturn(true);
        given(productSale.getSettlement()).willReturn(confirmedSettlement);

        service.recordCancellation(cancellation, paymentCancellation, sellerOrder);

        assertThat(captureRefundCommand().amount()).isEqualTo(3_000L);
        verify(eligibilityService, never()).activateFullCancellationEligibility(any(), any());
    }

    @Test
    void legacyOrderWithoutInitialLedgerIsLeftForBackfill() {
        given(productLedger()).willReturn(Optional.empty());
        given(ledgerRepository.existsBySellerOrderIdAndSourceTypeAndSourceId(
                SELLER_ORDER_ID,
                SettlementLedgerSourceType.SELLER_ORDER,
                SELLER_ORDER_ID
        )).willReturn(false);

        service.recordCancellation(cancellation, paymentCancellation, sellerOrder);

        verify(ledgerService, never()).recordCancellationRefund(any());
    }

    private void givenInitialLedgers(
            SettlementLedgerEntry product,
            SettlementLedgerEntry commissionEntry
    ) {
        given(productLedger()).willReturn(Optional.ofNullable(product));
        given(commissionLedger()).willReturn(Optional.ofNullable(commissionEntry));
    }

    private Optional<SettlementLedgerEntry> productLedger() {
        return ledgerRepository.findBySourceTypeAndSourceIdAndSourceDetailKey(
                SettlementLedgerSourceType.SELLER_ORDER,
                SELLER_ORDER_ID,
                InitialSettlementLedgerService.SALE_PRODUCT_SOURCE_DETAIL
        );
    }

    private Optional<SettlementLedgerEntry> commissionLedger() {
        return ledgerRepository.findBySourceTypeAndSourceIdAndSourceDetailKey(
                SettlementLedgerSourceType.SELLER_ORDER,
                SELLER_ORDER_ID,
                InitialSettlementLedgerService.COMMISSION_SOURCE_DETAIL
        );
    }

    private SettlementLedgerCommand captureRefundCommand() {
        ArgumentCaptor<SettlementLedgerCommand> captor = ArgumentCaptor.forClass(
                SettlementLedgerCommand.class
        );
        verify(ledgerService).recordCancellationRefund(captor.capture());
        return captor.getValue();
    }

    private SettlementLedgerCommand captureReversalCommand() {
        ArgumentCaptor<SettlementLedgerCommand> captor = ArgumentCaptor.forClass(
                SettlementLedgerCommand.class
        );
        verify(ledgerService).recordCommissionReversal(captor.capture());
        return captor.getValue();
    }
}
