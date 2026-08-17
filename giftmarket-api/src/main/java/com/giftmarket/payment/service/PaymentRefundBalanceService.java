package com.giftmarket.payment.service;

import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentCancellationStatus;
import com.giftmarket.payment.exception.PaymentException;
import com.giftmarket.payment.repository.PaymentCancellationRepository;
import com.giftmarket.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentRefundBalanceService {

    private final PaymentRepository paymentRepository;
    private final PaymentCancellationRepository cancellationRepository;

    @Transactional(readOnly = true)
    public PaymentRefundBalance getBalance(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException("결제 정보를 찾을 수 없습니다."));
        return getBalance(payment);
    }

    public PaymentRefundBalance getBalance(Payment payment) {
        if (payment == null || payment.getId() == null
                || payment.getAmount() == null || payment.getAmount() <= 0L) {
            throw new PaymentException("환불 가능 금액을 계산할 수 없습니다.");
        }
        long succeeded = amount(payment.getId(), PaymentCancellationStatus.SUCCEEDED);
        long reserved = amount(payment.getId(), PaymentCancellationStatus.REQUESTED);
        try {
            long unavailable = Math.addExact(succeeded, reserved);
            long available = Math.subtractExact(payment.getAmount(), unavailable);
            if (succeeded < 0L || reserved < 0L || available < 0L) {
                throw new PaymentException("환불 금액 기록의 정합성이 올바르지 않습니다.");
            }
            return new PaymentRefundBalance(payment.getAmount(), succeeded, reserved, available);
        } catch (ArithmeticException exception) {
            throw new PaymentException("환불 가능 금액을 안전하게 계산할 수 없습니다.");
        }
    }

    public void validateRefundAmount(PaymentRefundBalance balance, long refundAmount) {
        if (balance == null || refundAmount <= 0L
                || refundAmount > balance.availableRefundAmount()) {
            throw new PaymentException("환불 가능 금액을 초과했습니다.");
        }
    }

    private long amount(Long paymentId, PaymentCancellationStatus status) {
        Long amount = cancellationRepository.sumAmountByPaymentIdAndStatus(paymentId, status);
        return amount == null ? 0L : amount;
    }
}
