package com.giftmarket.order.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ShipmentTest {

    @Test
    void createsAndCompletesOriginalOutboundShipment() {
        LocalDateTime shippedAt = LocalDateTime.now();
        LocalDateTime deliveredAt = shippedAt.plusDays(1);
        Shipment shipment = Shipment.createShipped(
                mock(SellerOrder.class),
                ShipmentType.ORIGINAL_OUTBOUND,
                "택배사",
                "1234567890",
                shippedAt
        );

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.SHIPPED);
        assertThat(shipment.getShippedAt()).isEqualTo(shippedAt);

        shipment.deliver(deliveredAt);

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(shipment.getDeliveredAt()).isEqualTo(deliveredAt);
    }

    @Test
    void rejectsIncompleteTrackingInformation() {
        assertThatThrownBy(() -> Shipment.createShipped(
                mock(SellerOrder.class),
                ShipmentType.ORIGINAL_OUTBOUND,
                " ",
                "1234",
                LocalDateTime.now()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readyShipmentCanBeShippedOrCanceledButShippedShipmentCannotBeCanceled() {
        Shipment shipped = Shipment.createReady(
                mock(SellerOrder.class),
                ShipmentType.RETURN_COLLECTION,
                "택배사",
                "RETURN-1234"
        );
        LocalDateTime shippedAt = LocalDateTime.now();

        shipped.ship(shippedAt);

        assertThat(shipped.getStatus()).isEqualTo(ShipmentStatus.SHIPPED);
        assertThat(shipped.getShippedAt()).isEqualTo(shippedAt);
        assertThatThrownBy(shipped::cancel)
                .isInstanceOf(IllegalStateException.class);

        Shipment canceled = Shipment.createReady(
                mock(SellerOrder.class),
                ShipmentType.EXCHANGE_OUTBOUND,
                "택배사",
                "EXCHANGE-1234"
        );
        canceled.cancel();

        assertThat(canceled.getStatus()).isEqualTo(ShipmentStatus.CANCELED);
        assertThatThrownBy(() -> canceled.ship(LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
    }
}
