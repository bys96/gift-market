package com.giftmarket.payment.service;

import com.giftmarket.order.entity.Order;
import com.giftmarket.order.entity.OrderStatus;
import com.giftmarket.order.repository.OrderRepository;
import com.giftmarket.order.service.OrderInventoryService;
import com.giftmarket.order.service.SellerOrderLifecycleService;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentStatus;
import com.giftmarket.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentExpirationTransactionService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderInventoryService orderInventoryService;
    private final SellerOrderLifecycleService sellerOrderLifecycleService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireReadyPayment(Long paymentId, LocalDateTime now) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElse(null);

        if (payment == null
                || payment.getStatus() != PaymentStatus.READY
                || payment.getExpiresAt().isAfter(now)) {
            return false;
        }

        Order order = orderRepository.findByIdForUpdate(payment.getOrder().getId())
                .orElse(null);

        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return false;
        }

        orderInventoryService.restore(order.getId());
        payment.expire();
        order.markPaymentExpired();
        sellerOrderLifecycleService.cancel(order.getId());
        return true;
    }
}
