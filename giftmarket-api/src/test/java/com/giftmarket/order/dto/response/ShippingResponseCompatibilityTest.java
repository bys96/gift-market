package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.entity.Shipment;
import com.giftmarket.seller.entity.Seller;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShippingResponseCompatibilityTest {

    @Test
    void shipmentTakesPriorityOverLegacySellerOrderSnapshot() {
        SellerOrder sellerOrder = sellerOrderWithLegacySnapshot();
        Shipment shipment = mock(Shipment.class);
        LocalDateTime shipmentShippedAt = LocalDateTime.now();
        when(shipment.getShippingCompany()).thenReturn("Shipment 택배");
        when(shipment.getTrackingNumber()).thenReturn("SHIPMENT-1234");
        when(shipment.getShippedAt()).thenReturn(shipmentShippedAt);

        BuyerSellerOrderResponse response = BuyerSellerOrderResponse.from(
                sellerOrder, shipment, List.of(), Map.of()
        );

        assertThat(response.shippingCompany()).isEqualTo("Shipment 택배");
        assertThat(response.trackingNumber()).isEqualTo("SHIPMENT-1234");
        assertThat(response.shippedAt()).isEqualTo(shipmentShippedAt);
    }

    @Test
    void legacySnapshotIsUsedOnlyWhenShipmentIsMissing() {
        SellerOrder sellerOrder = sellerOrderWithLegacySnapshot();

        BuyerSellerOrderResponse response = BuyerSellerOrderResponse.from(
                sellerOrder, null, List.of(), Map.of()
        );

        assertThat(response.shippingCompany()).isEqualTo("Legacy 택배");
        assertThat(response.trackingNumber()).isEqualTo("LEGACY-1234");
        assertThat(response.shippedAt()).isNotNull();
    }

    private SellerOrder sellerOrderWithLegacySnapshot() {
        Seller seller = mock(Seller.class);
        when(seller.getStoreName()).thenReturn("테스트 스토어");
        SellerOrder sellerOrder = SellerOrder.createPendingPayment(
                mock(com.giftmarket.order.entity.Order.class), seller
        );
        sellerOrder.markPaid();
        sellerOrder.prepare(LocalDateTime.now().minusHours(2));
        LocalDateTime shippedAt = LocalDateTime.now().minusHours(1);
        sellerOrder.markShipped(shippedAt);
        sellerOrder.synchronizeLegacyShippingSnapshot(
                "Legacy 택배", "LEGACY-1234", shippedAt, null
        );
        assertThat(sellerOrder.getStatus()).isEqualTo(SellerOrderStatus.SHIPPED);
        return sellerOrder;
    }
}
