package com.giftmarket.order.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.order.dto.response.PurchaseConfirmationResponse;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.order.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseConfirmationService {
    private final OrderItemRepository orderItemRepository;
    private final ShipmentRepository shipmentRepository;
    private final PurchaseConfirmationQuantities quantities;

    @Transactional
    public PurchaseConfirmationResponse confirm(Long userId, Long orderId, Long orderItemId) {
        if (userId == null) throw new AuthenticationException("인증이 필요합니다.");
        OrderItem item = orderItemRepository.findByIdAndOrderIdForUpdate(orderItemId, orderId)
                .orElseThrow(this::notFound);
        if (!item.getOrder().getUser().getId().equals(userId)) throw notFound();

        Shipment shipment = shipmentRepository.findBySellerOrderIdAndType(
                        item.getSellerOrder().getId(), ShipmentType.ORIGINAL_OUTBOUND)
                .orElseThrow(() -> new OrderException("배송 완료된 상품만 구매확정할 수 있습니다."));
        if (shipment.getStatus() != ShipmentStatus.DELIVERED || shipment.getDeliveredAt() == null) {
            throw new OrderException("배송 완료된 상품만 구매확정할 수 있습니다.");
        }

        var pending = quantities.load(List.of(item.getId()));
        int confirmable = quantities.confirmable(item, pending);
        if (confirmable <= 0) throw new OrderException("구매확정 가능한 수량이 없습니다.");
        try {
            item.confirmPurchase(confirmable);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new OrderException(exception.getMessage());
        }
        return new PurchaseConfirmationResponse(item.getId(), item.getConfirmedQuantity(), 0);
    }

    private OrderException notFound() { return new OrderException("주문 상품 정보를 찾을 수 없습니다."); }
}
