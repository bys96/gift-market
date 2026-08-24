package com.giftmarket.order.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExchangeRequestTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 22, 12, 0);

    @Test
    void resolvesBuyerSellerAndOtherResponsibility() {
        assertThat(request(ExchangeReasonType.CHANGE_OF_MIND).getResponsibility())
                .isEqualTo(ExchangeResponsibility.BUYER);
        assertThat(request(ExchangeReasonType.DEFECTIVE).getResponsibility())
                .isEqualTo(ExchangeResponsibility.SELLER);
        ExchangeRequest other = request(ExchangeReasonType.OTHER);
        assertThat(other.getResponsibility()).isNull();
        other.confirmResponsibility(ExchangeResponsibility.SELLER);
        assertThat(other.getResponsibility()).isEqualTo(ExchangeResponsibility.SELLER);
    }

    @Test
    void rejectsOrderAndSellerOrderMismatch() {
        Order order = mock(Order.class);
        SellerOrder sellerOrder = mock(SellerOrder.class);
        when(sellerOrder.getOrder()).thenReturn(mock(Order.class));
        assertThatThrownBy(() -> create(order, sellerOrder, ExchangeReasonType.DEFECTIVE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buyerMovesThroughPaymentPendingWithExternalDueAt() {
        ExchangeRequest request = request(ExchangeReasonType.CHANGE_OF_MIND);
        request.approve(NOW.plusMinutes(1));
        request.startPaymentPending(NOW.plusMinutes(2), NOW.plusHours(24));
        assertThat(request.getStatus()).isEqualTo(ExchangeRequestStatus.PAYMENT_PENDING);
        assertThat(request.getPaymentDueAt()).isEqualTo(NOW.plusHours(24));
    }

    @Test
    void sellerCannotEnterPaymentPending() {
        ExchangeRequest request = request(ExchangeReasonType.DEFECTIVE);
        request.approve(NOW.plusMinutes(1));
        assertThatThrownBy(() -> request.startPaymentPending(NOW.plusMinutes(2), NOW.plusHours(24)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sellerCanEnterCollectingAfterReservationWithoutShipment() {
        ExchangeRequest request = request(ExchangeReasonType.DEFECTIVE);
        request.approve(NOW.plusMinutes(1));
        request.startCollectingAfterReservation(NOW.plusMinutes(2));
        assertThat(request.getStatus()).isEqualTo(ExchangeRequestStatus.COLLECTING);
        assertThat(request.getCollectionShipment()).isNull();
    }

    @Test
    void sellerFollowsCollectionInspectionAndReshippingFlow() {
        ExchangeRequest request = request(ExchangeReasonType.DEFECTIVE);
        SellerOrder sellerOrder = request.getSellerOrder();
        Shipment collection = shipment(sellerOrder, ShipmentType.EXCHANGE_COLLECTION, ShipmentStatus.SHIPPED);
        Shipment outbound = shipment(sellerOrder, ShipmentType.EXCHANGE_OUTBOUND, ShipmentStatus.DELIVERED);

        request.approve(NOW.plusMinutes(1));
        request.assignCollectionShipment(collection);
        request.startCollecting(NOW.plusMinutes(2));
        request.receive(NOW.plusMinutes(3));
        request.completeInspection(NOW.plusMinutes(4));
        request.assignOutboundShipment(outbound);
        request.startReshipping(NOW.plusMinutes(5));
        request.complete(NOW.plusMinutes(6));

        assertThat(request.getStatus()).isEqualTo(ExchangeRequestStatus.COMPLETED);
        assertThat(request.getCollectionShipment()).isSameAs(collection);
        assertThat(request.getOutboundShipment()).isSameAs(outbound);
    }

    @Test
    void rejectsWrongShipmentTypeAndSellerOrder() {
        ExchangeRequest request = request(ExchangeReasonType.DEFECTIVE);
        request.approve(NOW.plusMinutes(1));
        assertThatThrownBy(() -> request.assignCollectionShipment(
                shipment(request.getSellerOrder(), ShipmentType.ORIGINAL_OUTBOUND, ShipmentStatus.SHIPPED)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request.assignCollectionShipment(
                shipment(mock(SellerOrder.class), ShipmentType.EXCHANGE_COLLECTION, ShipmentStatus.SHIPPED)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidTransitionAndUndeliveredOutboundAreBlocked() {
        ExchangeRequest request = request(ExchangeReasonType.DEFECTIVE);
        assertThatThrownBy(() -> request.receive(NOW)).isInstanceOf(IllegalStateException.class);

        request.approve(NOW.plusMinutes(1));
        request.assignCollectionShipment(
                shipment(request.getSellerOrder(), ShipmentType.EXCHANGE_COLLECTION, ShipmentStatus.SHIPPED));
        request.startCollecting(NOW.plusMinutes(2));
        request.receive(NOW.plusMinutes(3));
        request.completeInspection(NOW.plusMinutes(4));
        request.assignOutboundShipment(
                shipment(request.getSellerOrder(), ShipmentType.EXCHANGE_OUTBOUND, ShipmentStatus.SHIPPED));
        request.startReshipping(NOW.plusMinutes(5));
        assertThatThrownBy(() -> request.complete(NOW.plusMinutes(6)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requestedExchangeCanBeRejectedOrCanceled() {
        ExchangeRequest rejected = request(ExchangeReasonType.DEFECTIVE);
        rejected.reject("target 재고가 없습니다.", NOW.plusMinutes(1));
        assertThat(rejected.getStatus()).isEqualTo(ExchangeRequestStatus.REJECTED);
        assertThat(rejected.getRejectedReason()).isEqualTo("target 재고가 없습니다.");

        ExchangeRequest canceled = request(ExchangeReasonType.CHANGE_OF_MIND);
        canceled.approve(NOW.plusMinutes(1));
        canceled.startPaymentPending(NOW.plusMinutes(2), NOW.plusHours(24));
        canceled.cancel(NOW.plusHours(24));
        assertThat(canceled.getStatus()).isEqualTo(ExchangeRequestStatus.CANCELED);
    }

    @Test
    void failedTransitionIsExplicitAndTerminalStatesAreProtected() {
        ExchangeRequest request = request(ExchangeReasonType.DEFECTIVE);
        request.fail(NOW.plusMinutes(1));
        assertThat(request.getStatus()).isEqualTo(ExchangeRequestStatus.FAILED);
        assertThatThrownBy(() -> request.fail(NOW.plusMinutes(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    private ExchangeRequest request(ExchangeReasonType reasonType) {
        Order order = mock(Order.class);
        SellerOrder sellerOrder = mock(SellerOrder.class);
        when(sellerOrder.getOrder()).thenReturn(order);
        return create(order, sellerOrder, reasonType);
    }

    private ExchangeRequest create(Order order, SellerOrder sellerOrder, ExchangeReasonType reasonType) {
        return ExchangeRequest.createRequested(
                order, sellerOrder, "exchange-key", reasonType, "교환 사유",
                "회수인", "010-1111-2222", "12345", "회수 주소", null,
                "수령인", "010-3333-4444", "54321", "재배송 주소", "101호", NOW
        );
    }

    private Shipment shipment(SellerOrder sellerOrder, ShipmentType type, ShipmentStatus status) {
        Shipment shipment = mock(Shipment.class);
        when(shipment.getSellerOrder()).thenReturn(sellerOrder);
        when(shipment.getType()).thenReturn(type);
        when(shipment.getStatus()).thenReturn(status);
        return shipment;
    }
}
