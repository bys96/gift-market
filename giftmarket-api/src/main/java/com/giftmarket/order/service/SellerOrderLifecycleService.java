package com.giftmarket.order.service;

import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.seller.entity.Seller;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SellerOrderLifecycleService {

    private final SellerOrderRepository sellerOrderRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Long, SellerOrder> createPendingPayment(
            Order order,
            List<Seller> sellers
    ) {
        Map<Long, SellerOrder> sellerOrders = new LinkedHashMap<>();

        for (Seller seller : sellers) {
            if (seller.getId() == null) {
                throw new OrderException("판매자 정보를 확인할 수 없습니다.");
            }
            sellerOrders.computeIfAbsent(
                    seller.getId(),
                    ignored -> SellerOrder.createPendingPayment(order, seller)
            );
        }

        if (sellerOrders.isEmpty()) {
            throw new OrderException("판매자 주문을 생성할 상품이 없습니다.");
        }

        sellerOrderRepository.saveAll(sellerOrders.values());
        return sellerOrders;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markPaid(Long orderId) {
        getSellerOrders(orderId).forEach(SellerOrder::markPaid);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancel(Long orderId) {
        getSellerOrders(orderId).forEach(SellerOrder::cancel);
    }

    private List<SellerOrder> getSellerOrders(Long orderId) {
        List<SellerOrder> sellerOrders =
                sellerOrderRepository.findAllByOrderIdOrderByIdAsc(orderId);
        if (sellerOrders.isEmpty()) {
            throw new OrderException("판매자 주문 정보를 확인할 수 없습니다.");
        }
        return sellerOrders;
    }
}
