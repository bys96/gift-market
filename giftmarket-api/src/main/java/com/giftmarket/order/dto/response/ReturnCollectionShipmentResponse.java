package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.Shipment;
import com.giftmarket.order.entity.ShipmentStatus;
import com.giftmarket.order.entity.ShipmentType;

import java.time.LocalDateTime;

public record ReturnCollectionShipmentResponse(
        Long shipmentId,
        ShipmentType type,
        ShipmentStatus status,
        String shippingCompany,
        String trackingNumber,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt
) {
    public static ReturnCollectionShipmentResponse from(Shipment shipment) {
        if (shipment == null) return null;
        return new ReturnCollectionShipmentResponse(
                shipment.getId(), shipment.getType(), shipment.getStatus(),
                shipment.getShippingCompany(), shipment.getTrackingNumber(),
                shipment.getShippedAt(), shipment.getDeliveredAt()
        );
    }
}
