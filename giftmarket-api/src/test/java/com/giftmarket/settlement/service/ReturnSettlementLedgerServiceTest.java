package com.giftmarket.settlement.service;

import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.ReturnRequest;
import com.giftmarket.order.entity.ReturnRequestItem;
import com.giftmarket.order.entity.ReturnRequestStatus;
import com.giftmarket.order.entity.ReturnResponsibility;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.repository.ReturnRequestItemRepository;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentCancellation;
import com.giftmarket.payment.entity.PaymentCancellationStatus;
import com.giftmarket.payment.entity.PaymentCancellationType;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.settlement.config.SettlementProperties;
import com.giftmarket.settlement.entity.Settlement;
import com.giftmarket.settlement.entity.SettlementLedgerEntry;
import com.giftmarket.settlement.entity.SettlementLedgerSourceType;
import com.giftmarket.settlement.entity.SettlementLedgerType;
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
class ReturnSettlementLedgerServiceTest {

    private static final Long SELLER_ID = 1L;
    private static final Long SELLER_ORDER_ID = 2L;
    private static final Long RETURN_ID = 3L;
    private static final Long PAYMENT_CANCELLATION_ID = 4L;
    private static final LocalDateTime DELIVERED_AT = LocalDateTime.of(2026, 9, 15, 14, 0);
    private static final LocalDateTime PG_CANCELED_AT = LocalDateTime.of(2026, 9, 24, 13, 0);
    private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, 9, 25, 15, 0);

    @Mock ReturnRequestItemRepository returnItemRepository;
    @Mock SettlementLedgerEntryRepository ledgerRepository;
    @Mock SettlementLedgerService ledgerService;
    @Mock RefundedProductAmountAggregator refundedProductAmountAggregator;
    @Mock Seller seller;
    @Mock SellerOrder sellerOrder;
    @Mock Order order;
    @Mock ReturnRequest request;
    @Mock ReturnRequestItem returnItem;
    @Mock OrderItem orderItem;
    @Mock Payment payment;
    @Mock PaymentCancellation paymentCancellation;
    @Mock SettlementLedgerEntry productSale;
    @Mock SettlementLedgerEntry commission;

    private ReturnSettlementLedgerService service;

    @BeforeEach
    void setUp() {
        SettlementProperties properties = new SettlementProperties();
        properties.setHoldDays(7);
        service = new ReturnSettlementLedgerService(
                returnItemRepository,
                ledgerRepository,
                ledgerService,
                new SettlementCommissionCalculator(),
                refundedProductAmountAggregator,
                properties
        );
        given(seller.getId()).willReturn(SELLER_ID);
        given(sellerOrder.getId()).willReturn(SELLER_ORDER_ID);
        given(sellerOrder.getSeller()).willReturn(seller);
        given(sellerOrder.getOrder()).willReturn(order);
        given(sellerOrder.getStatus()).willReturn(SellerOrderStatus.DELIVERED);
        given(sellerOrder.getDeliveredAt()).willReturn(DELIVERED_AT);
        given(request.getId()).willReturn(RETURN_ID);
        given(request.getSellerOrder()).willReturn(sellerOrder);
        given(request.getOrder()).willReturn(order);
        given(request.getStatus()).willReturn(ReturnRequestStatus.COMPLETED);
        given(request.getCompletedAt()).willReturn(COMPLETED_AT);
        given(request.getResponsibility()).willReturn(ReturnResponsibility.BUYER);
        refundSnapshots(10_000L, 3_000L, 3_000L, 10_000L);

        given(paymentCancellation.getId()).willReturn(PAYMENT_CANCELLATION_ID);
        given(paymentCancellation.getStatus()).willReturn(PaymentCancellationStatus.SUCCEEDED);
        given(paymentCancellation.getType()).willReturn(PaymentCancellationType.PARTIAL);
        given(paymentCancellation.getReturnRequest()).willReturn(request);
        given(paymentCancellation.getPayment()).willReturn(payment);
        given(payment.getOrder()).willReturn(order);
        given(paymentCancellation.getAmount()).willReturn(10_000L);
        given(paymentCancellation.getCanceledAt()).willReturn(PG_CANCELED_AT);

        given(returnItem.getReturnRequest()).willReturn(request);
        given(returnItem.getOrderItem()).willReturn(orderItem);
        given(returnItem.getQuantity()).willReturn(1);
        given(orderItem.getSellerOrder()).willReturn(sellerOrder);
        given(orderItem.getSeller()).willReturn(seller);
        given(orderItem.getUnitPrice()).willReturn(10_000L);
        given(returnItemRepository.findAllByReturnRequestIdOrderByIdAsc(RETURN_ID))
                .willReturn(List.of(returnItem));

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
        given(productLedger()).willReturn(Optional.of(productSale));
        given(commissionLedger()).willReturn(Optional.of(commission));
        given(refundedProductAmountAggregator.calculate(sellerOrder)).willReturn(10_000L);
        given(ledgerRepository.sumAmountBySellerOrderIdAndType(
                SELLER_ORDER_ID,
                SettlementLedgerType.COMMISSION_REVERSAL
        )).willReturn(0L);
    }

    @Test
    void completedReturnRecordsActualPgRefundAndProductCommissionReversal() {
        service.recordCompletedReturn(request, paymentCancellation, sellerOrder);

        SettlementLedgerCommand refund = captureReturnRefund();
        SettlementLedgerCommand reversal = captureCommissionReversal();
        assertThat(refund.amount()).isEqualTo(10_000L);
        assertThat(refund.sourceDetailKey()).isEqualTo("SO:2:RETURN_REFUND");
        assertThat(refund.occurredAt()).isEqualTo(PG_CANCELED_AT);
        assertThat(refund.eligibleAt()).isEqualTo(COMPLETED_AT);
        assertThat(reversal.amount()).isEqualTo(1_000L);
        assertThat(reversal.commissionBaseAmount()).isEqualTo(10_000L);
    }

    @Test
    void returnShippingChargeIsNotAddedBackOrIncludedInCommissionBase() {
        service.recordCompletedReturn(request, paymentCancellation, sellerOrder);

        assertThat(captureReturnRefund().amount()).isEqualTo(10_000L);
        assertThat(captureCommissionReversal().commissionBaseAmount()).isEqualTo(10_000L);
    }

    @Test
    void sellerFaultUsesSameConfirmedSnapshots() {
        given(request.getResponsibility()).willReturn(ReturnResponsibility.SELLER);
        refundSnapshots(10_000L, 3_000L, 0L, 13_000L);
        given(paymentCancellation.getAmount()).willReturn(13_000L);

        service.recordCompletedReturn(request, paymentCancellation, sellerOrder);

        assertThat(captureReturnRefund().amount()).isEqualTo(13_000L);
        assertThat(captureCommissionReversal().amount()).isEqualTo(1_000L);
    }

    @Test
    void mixedCancellationAndReturnUsesUnifiedCumulativeRounding() {
        given(productSale.getCommissionRateBps()).willReturn(333);
        given(commission.getAmount()).willReturn(-333L);
        given(commission.getCommissionRateBps()).willReturn(333);
        refundSnapshots(3_333L, 0L, 0L, 3_333L);
        given(orderItem.getUnitPrice()).willReturn(3_333L);
        given(paymentCancellation.getAmount()).willReturn(3_333L);
        given(refundedProductAmountAggregator.calculate(sellerOrder)).willReturn(6_666L);
        given(ledgerRepository.sumAmountBySellerOrderIdAndType(
                SELLER_ORDER_ID,
                SettlementLedgerType.COMMISSION_REVERSAL
        )).willReturn(110L);

        service.recordCompletedReturn(request, paymentCancellation, sellerOrder);

        assertThat(captureCommissionReversal().amount()).isEqualTo(111L);
    }

    @Test
    void finalCombinedRefundReversesOnlyRemainingOriginalCommission() {
        given(ledgerRepository.sumAmountBySellerOrderIdAndType(
                SELLER_ORDER_ID,
                SettlementLedgerType.COMMISSION_REVERSAL
        )).willReturn(900L);

        service.recordCompletedReturn(request, paymentCancellation, sellerOrder);

        assertThat(captureCommissionReversal().amount()).isEqualTo(100L);
    }

    @Test
    void zeroPercentCommissionCreatesNoReversal() {
        given(productSale.getCommissionRateBps()).willReturn(0);
        given(commissionLedger()).willReturn(Optional.empty());

        service.recordCompletedReturn(request, paymentCancellation, sellerOrder);

        verify(ledgerService, never()).recordCommissionReversal(any());
    }

    @Test
    void zeroPgRefundStillReversesCommissionForReturnedProduct() {
        refundSnapshots(10_000L, 3_000L, 13_000L, 0L);

        service.recordCompletedReturn(request, null, sellerOrder);

        verify(ledgerService, never()).recordReturnRefund(any());
        SettlementLedgerCommand reversal = captureZeroRefundCommissionReversal();
        assertThat(reversal.amount()).isEqualTo(1_000L);
        assertThat(reversal.sourceId()).isEqualTo(RETURN_ID);
        assertThat(reversal.occurredAt()).isEqualTo(COMPLETED_AT);
    }

    @Test
    void unsuccessfulPaymentCancellationIsRejected() {
        given(paymentCancellation.getStatus()).willReturn(PaymentCancellationStatus.REQUESTED);

        assertThatThrownBy(() -> service.recordCompletedReturn(
                request,
                paymentCancellation,
                sellerOrder
        )).isInstanceOf(SettlementException.class);
        verify(ledgerService, never()).recordReturnRefund(any());
    }

    @Test
    void pgAmountMustEqualReturnRefundSnapshot() {
        given(paymentCancellation.getAmount()).willReturn(9_000L);

        assertThatThrownBy(() -> service.recordCompletedReturn(
                request,
                paymentCancellation,
                sellerOrder
        )).isInstanceOf(SettlementException.class);
    }

    @Test
    void nonCompletedReturnCreatesNoLedger() {
        given(request.getStatus()).willReturn(ReturnRequestStatus.REFUNDING);

        assertThatThrownBy(() -> service.recordCompletedReturn(
                request,
                paymentCancellation,
                sellerOrder
        )).isInstanceOf(SettlementException.class);
        verify(ledgerService, never()).recordReturnRefund(any());
    }

    @Test
    void sameCompletedReturnRetryIsNoOp() {
        SettlementLedgerEntry existing = org.mockito.Mockito.mock(SettlementLedgerEntry.class);
        given(existing.getType()).willReturn(SettlementLedgerType.RETURN_REFUND);
        given(existing.getAmount()).willReturn(-10_000L);
        given(existing.getSellerOrder()).willReturn(sellerOrder);
        given(existing.getOccurredAt()).willReturn(PG_CANCELED_AT);
        given(ledgerRepository.findBySourceTypeAndSourceIdAndSourceDetailKey(
                SettlementLedgerSourceType.PAYMENT_CANCELLATION,
                PAYMENT_CANCELLATION_ID,
                "SO:2:RETURN_REFUND"
        )).willReturn(Optional.of(existing));

        service.recordCompletedReturn(request, paymentCancellation, sellerOrder);

        verify(ledgerService, never()).recordReturnRefund(any());
        verify(ledgerService, never()).recordCommissionReversal(any());
    }

    @Test
    void confirmedInitialSaleRemainsUnchangedWhileNewReturnLedgerIsCreated() {
        Settlement settlement = org.mockito.Mockito.mock(Settlement.class);
        given(settlement.isConfirmed()).willReturn(true);
        given(productSale.getSettlement()).willReturn(settlement);

        service.recordCompletedReturn(request, paymentCancellation, sellerOrder);

        assertThat(captureReturnRefund().amount()).isEqualTo(10_000L);
    }

    private void refundSnapshots(long product, long originalShipping, long returnCharge, long refund) {
        given(request.getProductRefundAmount()).willReturn(product);
        given(request.getOriginalShippingRefundAmount()).willReturn(originalShipping);
        given(request.getReturnShippingCharge()).willReturn(returnCharge);
        given(request.getRefundAmount()).willReturn(refund);
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

    private SettlementLedgerCommand captureReturnRefund() {
        ArgumentCaptor<SettlementLedgerCommand> captor = ArgumentCaptor.forClass(
                SettlementLedgerCommand.class
        );
        verify(ledgerService).recordReturnRefund(captor.capture());
        return captor.getValue();
    }

    private SettlementLedgerCommand captureCommissionReversal() {
        ArgumentCaptor<SettlementLedgerCommand> captor = ArgumentCaptor.forClass(
                SettlementLedgerCommand.class
        );
        verify(ledgerService).recordCommissionReversal(captor.capture());
        return captor.getValue();
    }

    private SettlementLedgerCommand captureZeroRefundCommissionReversal() {
        ArgumentCaptor<SettlementLedgerCommand> captor = ArgumentCaptor.forClass(
                SettlementLedgerCommand.class
        );
        verify(ledgerService).recordZeroRefundReturnCommissionReversal(captor.capture());
        return captor.getValue();
    }
}
