package com.giftmarket.payment.service;

import com.giftmarket.order.entity.OrderCancellation;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.repository.OrderCancellationRepository;
import com.giftmarket.payment.repository.PaymentCancellationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PartialPaymentCancellationOrphanRecoveryService {

    private final OrderCancellationRepository orderCancellationRepository;
    private final PaymentCancellationRepository paymentCancellationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failIfPaymentCancellationWasNotCreated(Long orderCancellationId) {
        OrderCancellation cancellation = orderCancellationRepository
                .findByIdForUpdate(orderCancellationId)
                .orElse(null);
        if (cancellation == null
                || cancellation.getStatus() != OrderCancellationStatus.PROCESSING
                || paymentCancellationRepository
                        .findByOrderCancellationId(orderCancellationId)
                        .isPresent()) {
            return;
        }
        cancellation.fail(LocalDateTime.now());
    }
}
