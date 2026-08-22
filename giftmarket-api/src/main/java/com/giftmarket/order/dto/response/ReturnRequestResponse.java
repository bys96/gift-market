package com.giftmarket.order.dto.response;

import com.giftmarket.order.entity.ReturnReasonType;
import com.giftmarket.order.entity.ReturnRequest;
import com.giftmarket.order.entity.ReturnRequestItem;
import com.giftmarket.order.entity.ReturnRequestStatus;
import com.giftmarket.order.entity.ReturnResponsibility;

import java.time.LocalDateTime;
import java.util.List;

public record ReturnRequestResponse(
        Long returnRequestId,
        Long orderId,
        Long sellerOrderId,
        ReturnRequestStatus status,
        ReturnReasonType reasonType,
        String reason,
        ReturnResponsibility responsibility,
        String collectionRecipientName,
        String collectionPhone,
        String collectionPostalCode,
        String collectionAddress,
        String collectionAddressDetail,
        LocalDateTime requestedAt,
        LocalDateTime approvedAt,
        LocalDateTime collectingAt,
        LocalDateTime receivedAt,
        LocalDateTime inspectedAt,
        LocalDateTime refundingAt,
        LocalDateTime completedAt,
        LocalDateTime rejectedAt,
        String rejectedReason,
        LocalDateTime canceledAt,
        LocalDateTime failedAt,
        Long productRefundAmount,
        Long originalShippingRefundAmount,
        Long returnShippingCharge,
        Long refundAmount,
        ReturnCollectionShipmentResponse collectionShipment,
        List<ReturnRequestItemResponse> items
) {
    public static ReturnRequestResponse from(
            ReturnRequest request,
            List<ReturnRequestItem> items
    ) {
        return new ReturnRequestResponse(
                request.getId(), request.getOrder().getId(), request.getSellerOrder().getId(),
                request.getStatus(), request.getReasonType(), request.getReason(),
                request.getResponsibility(), request.getCollectionRecipientName(),
                request.getCollectionPhone(), request.getCollectionPostalCode(),
                request.getCollectionAddress(), request.getCollectionAddressDetail(),
                request.getRequestedAt(), request.getApprovedAt(), request.getCollectingAt(),
                request.getReceivedAt(), request.getInspectedAt(), request.getRefundingAt(),
                request.getCompletedAt(), request.getRejectedAt(), request.getRejectedReason(),
                request.getCanceledAt(), request.getFailedAt(),
                request.getProductRefundAmount(), request.getOriginalShippingRefundAmount(),
                request.getReturnShippingCharge(), request.getRefundAmount(),
                ReturnCollectionShipmentResponse.from(request.getCollectionShipment()),
                items.stream().map(ReturnRequestItemResponse::from).toList()
        );
    }
}
