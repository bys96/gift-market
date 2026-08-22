package com.giftmarket.order.service;

import com.giftmarket.order.dto.response.ReturnRefundCalculation;
import com.giftmarket.order.dto.response.ReturnRequestResponse;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.PendingReturnQuantityProjection;
import com.giftmarket.order.repository.ReturnRequestRepository;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.exception.PaymentException;
import com.giftmarket.payment.repository.PaymentCancellationRepository;
import com.giftmarket.payment.service.PaymentRefundBalance;
import com.giftmarket.payment.service.PaymentRefundBalanceService;
import com.giftmarket.product.entity.Product;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReturnRefundCalculationServiceTest {

    @Mock ReturnRefundCalculator calculator;
    @Mock ReturnRequestRepository returnRequestRepository;
    @Mock PaymentCancellationRepository paymentCancellationRepository;
    @Mock PaymentRefundBalanceService balanceService;

    private ReturnRefundCalculationService service;
    private Order order;
    private SellerOrder sellerOrder;
    private OrderItem orderItem;
    private ReturnRequest request;
    private ReturnRequestItem returnItem;
    private Payment payment;

    @BeforeEach
    void setUp() {
        service = new ReturnRefundCalculationService(
                calculator, returnRequestRepository, paymentCancellationRepository, balanceService
        );
        order = Order.createPendingPayment(
                "GM", mock(User.class), 30_000L, 0L,
                "구매자", "010", "12345", "서울", null
        );
        ReflectionTestUtils.setField(order, "id", 1L);
        order.markPaid(LocalDateTime.now());
        sellerOrder = SellerOrder.createPendingPayment(order, mock(Seller.class));
        ReflectionTestUtils.setField(sellerOrder, "id", 2L);
        sellerOrder.markPaid();
        sellerOrder.prepare(LocalDateTime.now());
        sellerOrder.markShipped(LocalDateTime.now());
        sellerOrder.markDelivered(LocalDateTime.now());
        orderItem = OrderItem.create(
                order, mock(Product.class), null, mock(Seller.class), sellerOrder, null,
                "상품", null, "상점", null, null, 30_000L, 0L, 1,
                false, 3_000L, 3_000L, 6_000L
        );
        ReflectionTestUtils.setField(orderItem, "id", 3L);
        request = ReturnRequest.createRequested(
                order, sellerOrder, "key", ReturnReasonType.CHANGE_OF_MIND, "사유",
                "구매자", "010", "12345", "서울", null, LocalDateTime.now()
        );
        ReflectionTestUtils.setField(request, "id", 5L);
        returnItem = ReturnRequestItem.create(request, orderItem, 1);
        request.approve(LocalDateTime.now());
        Shipment collection = Shipment.createShipped(
                sellerOrder, ShipmentType.RETURN_COLLECTION, "택배", "123", LocalDateTime.now()
        );
        request.assignCollectionShipment(collection);
        request.startCollecting(LocalDateTime.now());
        request.receive(LocalDateTime.now());
        returnItem.inspect(ReturnInspectionResult.RESTOCKABLE);
        request.completeInspection(LocalDateTime.now());
        payment = mock(Payment.class);
        given(payment.getId()).willReturn(4L);
        given(payment.getOrder()).willReturn(order);
        given(payment.isRefundableState()).willReturn(true);
        given(returnRequestRepository.sumCalculatedItemQuantities(anyLong(), anyLong(), any()))
                .willReturn(List.of());
        given(returnRequestRepository
                .existsBySellerOrderIdAndIdNotAndOriginalShippingRefundAmountGreaterThanAndStatusIn(
                        anyLong(), anyLong(), anyLong(), any())).willReturn(false);
        given(paymentCancellationRepository.countShippingRefundsBySellerOrderId(anyLong(), any()))
                .willReturn(0L);
        given(returnRequestRepository.sumRefundAmountByOrderIdExcludingRequest(anyLong(), anyLong(), any()))
                .willReturn(0L);
        given(balanceService.getBalance(payment))
                .willReturn(new PaymentRefundBalance(100_000L, 0L, 0L, 100_000L));
    }

    @Test
    void savesSnapshotAfterBalanceValidationAndKeepsInspectedState() {
        ReturnRefundCalculation calculation = new ReturnRefundCalculation(
                30_000L, 3_000L, 6_000L, 27_000L, true
        );
        given(calculator.calculate(eq(sellerOrder), eq(ReturnResponsibility.BUYER),
                anyList(), anyList(), eq(Map.of()), eq(false))).willReturn(calculation);

        service.confirm(payment, sellerOrder, request, List.of(orderItem), List.of(returnItem));

        assertThat(request.getProductRefundAmount()).isEqualTo(30_000L);
        assertThat(request.getOriginalShippingRefundAmount()).isEqualTo(3_000L);
        assertThat(request.getReturnShippingCharge()).isEqualTo(6_000L);
        assertThat(request.getRefundAmount()).isEqualTo(27_000L);
        assertThat(request.getStatus()).isEqualTo(ReturnRequestStatus.INSPECTED);
        assertThat(orderItem.getReturnedQuantity()).isZero();
        assertThat(ReturnRequestResponse.from(request, List.of(returnItem)).refundAmount())
                .isEqualTo(27_000L);
        verify(balanceService).validateRefundAmount(any(), eq(27_000L));
    }

    @Test
    void zeroRefundIsSnapshottedWithoutBalanceReservationValidation() {
        given(calculator.calculate(any(), any(), anyList(), anyList(), anyMap(), anyBoolean()))
                .willReturn(new ReturnRefundCalculation(6_000L, 0L, 6_000L, 0L, true));

        service.confirm(payment, sellerOrder, request, List.of(orderItem), List.of(returnItem));

        assertThat(request.getRefundAmount()).isZero();
        verify(balanceService, never()).getBalance(any(Payment.class));
        verify(balanceService, never()).validateRefundAmount(any(), anyLong());
    }

    @Test
    void refundAboveAvailableBalanceIsRejectedBeforeSnapshot() {
        given(calculator.calculate(any(), any(), anyList(), anyList(), anyMap(), anyBoolean()))
                .willReturn(new ReturnRefundCalculation(30_000L, 0L, 0L, 30_000L, true));
        doThrow(new PaymentException("환불 가능 금액을 초과했습니다."))
                .when(balanceService).validateRefundAmount(any(), eq(30_000L));

        assertThatThrownBy(() -> service.confirm(
                payment, sellerOrder, request, List.of(orderItem), List.of(returnItem)
        )).isInstanceOf(PaymentException.class);
        assertThat(request.getRefundAmount()).isNull();
    }

    @Test
    void otherReturnSnapshotsReservePaymentBalanceAcrossSellerOrders() {
        given(calculator.calculate(any(), any(), anyList(), anyList(), anyMap(), anyBoolean()))
                .willReturn(new ReturnRefundCalculation(30_000L, 0L, 0L, 30_000L, true));
        given(balanceService.getBalance(payment))
                .willReturn(new PaymentRefundBalance(100_000L, 20_000L, 10_000L, 70_000L));
        given(returnRequestRepository.sumRefundAmountByOrderIdExcludingRequest(anyLong(), anyLong(), any()))
                .willReturn(50_000L);

        assertThatThrownBy(() -> service.confirm(
                payment, sellerOrder, request, List.of(orderItem), List.of(returnItem)
        )).isInstanceOf(PaymentException.class).hasMessageContaining("초과");
        assertThat(request.getRefundAmount()).isNull();
    }

    @Test
    void passesOtherCalculatedQuantityAndExistingReturnShippingClaimToCalculator() {
        PendingReturnQuantityProjection projection = mock(PendingReturnQuantityProjection.class);
        given(projection.getOrderItemId()).willReturn(3L);
        given(projection.getPendingQuantity()).willReturn(1L);
        given(returnRequestRepository.sumCalculatedItemQuantities(anyLong(), anyLong(), any()))
                .willReturn(List.of(projection));
        given(returnRequestRepository
                .existsBySellerOrderIdAndIdNotAndOriginalShippingRefundAmountGreaterThanAndStatusIn(
                        anyLong(), anyLong(), anyLong(), any())).willReturn(true);
        given(calculator.calculate(any(), any(), anyList(), anyList(), eq(Map.of(3L, 1L)), eq(true)))
                .willReturn(new ReturnRefundCalculation(30_000L, 0L, 0L, 30_000L, true));

        service.confirm(payment, sellerOrder, request, List.of(orderItem), List.of(returnItem));

        verify(calculator).calculate(any(), any(), anyList(), anyList(), eq(Map.of(3L, 1L)), eq(true));
    }

    @Test
    void existingCancellationShippingRefundAlsoBlocksOriginalShipping() {
        given(paymentCancellationRepository.countShippingRefundsBySellerOrderId(anyLong(), any()))
                .willReturn(1L);
        given(calculator.calculate(any(), any(), anyList(), anyList(), anyMap(), eq(true)))
                .willReturn(new ReturnRefundCalculation(30_000L, 0L, 0L, 30_000L, true));

        service.confirm(payment, sellerOrder, request, List.of(orderItem), List.of(returnItem));
        verify(calculator).calculate(any(), any(), anyList(), anyList(), anyMap(), eq(true));
    }

    @Test
    void snapshotConfirmationIsIdempotentOnlyForSameValues() {
        request.confirmRefundCalculation(10L, 2L, 1L, 11L);
        request.confirmRefundCalculation(10L, 2L, 1L, 11L);
        assertThatThrownBy(() -> request.confirmRefundCalculation(10L, 0L, 1L, 9L))
                .isInstanceOf(IllegalStateException.class);
    }

}
