package com.giftmarket.payment.service;

import com.giftmarket.order.entity.OrderCancellation;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.order.repository.OrderCancellationRepository;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentCancellation;
import com.giftmarket.payment.exception.PaymentException;
import com.giftmarket.payment.repository.PaymentCancellationRepository;
import com.giftmarket.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartialPaymentCancellationPreparationService {

    private final PaymentRepository paymentRepository;
    private final PaymentCancellationRepository paymentCancellationRepository;
    private final OrderCancellationRepository orderCancellationRepository;
    private final PaymentRefundBalanceService refundBalanceService;

    @Transactional
    public PaymentCancellation prepare(Long cancellationId, long refundAmount) {
        OrderCancellation reference = orderCancellationRepository.findById(cancellationId)
                .orElseThrow(() -> new OrderException("주문 취소 요청을 찾을 수 없습니다."));
        Payment latestPayment = paymentRepository
                .findFirstByOrderIdOrderByIdDesc(reference.getOrder().getId())
                .orElseThrow(() -> new PaymentException("결제 정보를 찾을 수 없습니다."));
        Payment payment = paymentRepository.findByIdForUpdate(latestPayment.getId())
                .orElseThrow(() -> new PaymentException("결제 정보를 찾을 수 없습니다."));
        OrderCancellation cancellation = orderCancellationRepository.findByIdForUpdate(cancellationId)
                .orElseThrow(() -> new OrderException("주문 취소 요청을 찾을 수 없습니다."));

        PaymentCancellation existing = paymentCancellationRepository
                .findByOrderCancellationId(cancellationId)
                .orElse(null);
        if (existing != null) {
            if (!existing.getPayment().getId().equals(payment.getId())
                    || !existing.getAmount().equals(refundAmount)) {
                throw new PaymentException("기존 부분환불 요청 정보와 일치하지 않습니다.");
            }
            return existing;
        }
        if (cancellation.getStatus() != OrderCancellationStatus.PROCESSING
                || cancellation.getOrder() != payment.getOrder()
                || !payment.isRefundableState()) {
            throw new PaymentException("현재 상태에서는 부분환불 거래를 준비할 수 없습니다.");
        }

        PaymentRefundBalance balance = refundBalanceService.getBalance(payment);
        refundBalanceService.validateRefundAmount(balance, refundAmount);
        PaymentCancellation created = PaymentCancellation.createPartial(
                payment,
                cancellation,
                "PARTIAL-" + UUID.randomUUID(),
                UUID.randomUUID().toString(),
                refundAmount,
                cancellation.getReason(),
                LocalDateTime.now()
        );
        return paymentCancellationRepository.saveAndFlush(created);
    }
}
