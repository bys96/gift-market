package com.giftmarket.order.service;

import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.*;
import com.giftmarket.payment.entity.*;
import com.giftmarket.payment.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReturnCompletionServiceTest {
    @Mock PaymentRepository payments; @Mock OrderRepository orders; @Mock SellerOrderRepository sellerOrders;
    @Mock ReturnRequestRepository returns; @Mock ReturnRequestItemRepository returnItems;
    @Mock OrderItemRepository orderItems; @Mock PaymentCancellationRepository cancellations;
    @Mock OrderInventoryService inventory;
    @Mock Payment payment; @Mock Order order; @Mock SellerOrder sellerOrder; @Mock ReturnRequest request;
    @Mock ReturnRequestItem returnItem; @Mock OrderItem orderItem; @Mock PaymentCancellation cancellation;
    ReturnCompletionService service;

    @BeforeEach
    void setUp() {
        service = new ReturnCompletionService(payments, orders, sellerOrders, returns, returnItems,
                orderItems, cancellations, inventory);
        given(returns.findById(10L)).willReturn(Optional.of(request));
        given(request.getOrder()).willReturn(order);
        given(request.getSellerOrder()).willReturn(sellerOrder);
        given(order.getId()).willReturn(1L);
        given(sellerOrder.getId()).willReturn(2L);
        given(payments.findFirstByOrderIdOrderByIdDesc(1L)).willReturn(Optional.of(payment));
        given(payment.getId()).willReturn(3L);
        given(payments.findByIdForUpdate(3L)).willReturn(Optional.of(payment));
        given(orders.findByIdForUpdate(1L)).willReturn(Optional.of(order));
        given(sellerOrders.findByIdAndOrderIdForUpdate(2L, 1L)).willReturn(Optional.of(sellerOrder));
        given(returns.findByIdForUpdate(10L)).willReturn(Optional.of(request));
        given(sellerOrder.getStatus()).willReturn(SellerOrderStatus.DELIVERED);
    }

    @Test
    void completesSucceededRefundAndRestocksRestockableItem() {
        given(request.getStatus()).willReturn(ReturnRequestStatus.REFUNDING);
        given(request.getRefundAmount()).willReturn(1_000L);
        given(cancellations.findByReturnRequestId(10L)).willReturn(Optional.of(cancellation));
        given(cancellation.getId()).willReturn(4L);
        given(cancellations.findByIdForUpdate(4L)).willReturn(Optional.of(cancellation));
        given(cancellation.getType()).willReturn(PaymentCancellationType.PARTIAL);
        given(cancellation.getStatus()).willReturn(PaymentCancellationStatus.SUCCEEDED);
        given(cancellation.getPayment()).willReturn(payment);
        given(cancellation.getReturnRequest()).willReturn(request);
        given(cancellation.getAmount()).willReturn(1_000L);
        prepareItem(ReturnInspectionResult.RESTOCKABLE);

        service.complete(10L);

        verify(inventory).restoreReturnItems(List.of(returnItem));
        verify(orderItem).confirmReturn(2);
        verify(returnItem).increaseRestockedQuantity(2);
        verify(request).complete(any());
        verify(payment, never()).markPartiallyCanceled(anyString());
        verify(payment, never()).markFullyCanceled(anyString(), any());
    }

    @Test
    void completesZeroRefundWithoutPaymentCancellationAndDoesNotRestockRejectedStock() {
        given(request.getStatus()).willReturn(ReturnRequestStatus.REFUNDING);
        given(request.getRefundAmount()).willReturn(0L);
        prepareItem(ReturnInspectionResult.NON_RESTOCKABLE);

        service.complete(10L);

        verify(orderItem).confirmReturn(2);
        verify(returnItem, never()).increaseRestockedQuantity(anyInt());
        verify(request).complete(any());
    }

    @Test
    void completedRequestIsIdempotentNoOp() {
        given(request.getStatus()).willReturn(ReturnRequestStatus.COMPLETED);

        service.complete(10L);

        verifyNoInteractions(inventory);
        verify(request, never()).complete(any());
    }

    private void prepareItem(ReturnInspectionResult result) {
        given(returnItems.findAllByReturnRequestIdOrderByIdAsc(10L)).willReturn(List.of(returnItem));
        given(orderItems.findAllBySellerOrderIdForUpdate(2L)).willReturn(List.of(orderItem));
        given(returnItem.getReturnRequest()).willReturn(request);
        given(returnItem.getOrderItem()).willReturn(orderItem);
        given(returnItem.getInspectionResult()).willReturn(result);
        given(returnItem.getQuantity()).willReturn(2);
        given(orderItem.getId()).willReturn(5L);
        given(orderItem.getSellerOrder()).willReturn(sellerOrder);
        given(orderItem.getReturnableQuantity()).willReturn(2);
    }
}
