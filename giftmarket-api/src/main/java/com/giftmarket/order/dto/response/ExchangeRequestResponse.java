package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.*;

import java.time.LocalDateTime;
import java.util.List;

public record ExchangeRequestResponse(
        Long exchangeRequestId, Long orderId, Long sellerOrderId,
        ExchangeRequestStatus status, ExchangeReasonType reasonType, String reason,
        ExchangeResponsibility responsibility,
        String collectionRecipientName, String collectionPhone, String collectionPostalCode,
        String collectionAddress, String collectionAddressDetail,
        String reshippingRecipientName, String reshippingPhone, String reshippingPostalCode,
        String reshippingAddress, String reshippingAddressDetail,
        LocalDateTime requestedAt, LocalDateTime approvedAt,
        LocalDateTime paymentPendingAt, LocalDateTime paymentDueAt,
        LocalDateTime collectingAt, LocalDateTime receivedAt, LocalDateTime inspectedAt,
        LocalDateTime reshippingAt, LocalDateTime completedAt,
        LocalDateTime rejectedAt, String rejectedReason,
        LocalDateTime canceledAt, LocalDateTime failedAt,
        ExchangeShipmentResponse collectionShipment,
        ExchangeShipmentResponse outboundShipment,
        List<ExchangeRequestItemResponse> items,
        List<ExchangeRequestImageResponse> images
) {
    public static ExchangeRequestResponse from(
            ExchangeRequest request, List<ExchangeRequestItem> items,
            List<ExchangeRequestImageResponse> images
    ) {
        return new ExchangeRequestResponse(
                request.getId(), request.getOrder().getId(), request.getSellerOrder().getId(),
                request.getStatus(), request.getReasonType(), request.getReason(), request.getResponsibility(),
                request.getCollectionRecipientName(), request.getCollectionPhone(), request.getCollectionPostalCode(),
                request.getCollectionAddress(), request.getCollectionAddressDetail(),
                request.getReshippingRecipientName(), request.getReshippingPhone(), request.getReshippingPostalCode(),
                request.getReshippingAddress(), request.getReshippingAddressDetail(),
                request.getRequestedAt(), request.getApprovedAt(), request.getPaymentPendingAt(), request.getPaymentDueAt(),
                request.getCollectingAt(), request.getReceivedAt(), request.getInspectedAt(), request.getReshippingAt(),
                request.getCompletedAt(), request.getRejectedAt(), request.getRejectedReason(),
                request.getCanceledAt(), request.getFailedAt(),
                ExchangeShipmentResponse.from(request.getCollectionShipment()),
                ExchangeShipmentResponse.from(request.getOutboundShipment()),
                items.stream().map(ExchangeRequestItemResponse::from).toList(),
                images == null ? List.of() : List.copyOf(images)
        );
    }
}
