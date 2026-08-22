package com.giftmarket.order.service;

import com.giftmarket.order.dto.response.ReturnRefundCalculation;
import com.giftmarket.order.entity.*;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.order.repository.PendingReturnQuantityProjection;
import com.giftmarket.order.repository.ReturnRequestRepository;
import com.giftmarket.payment.entity.Payment;
import com.giftmarket.payment.entity.PaymentCancellationStatus;
import com.giftmarket.payment.repository.PaymentCancellationRepository;
import com.giftmarket.payment.service.PaymentRefundBalance;
import com.giftmarket.payment.service.PaymentRefundBalanceService;
import com.giftmarket.payment.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReturnRefundCalculationService {

    private static final Set<ReturnRequestStatus> CALCULATED_QUANTITY_STATUSES = Set.of(
            ReturnRequestStatus.INSPECTED, ReturnRequestStatus.REFUNDING
    );
    private static final Set<ReturnRequestStatus> SHIPPING_CLAIM_STATUSES = Set.of(
            ReturnRequestStatus.INSPECTED, ReturnRequestStatus.REFUNDING,
            ReturnRequestStatus.COMPLETED
    );
    private static final Set<PaymentCancellationStatus> CANCELLATION_CLAIM_STATUSES = Set.of(
            PaymentCancellationStatus.REQUESTED, PaymentCancellationStatus.SUCCEEDED
    );

    private final ReturnRefundCalculator calculator;
    private final ReturnRequestRepository returnRequestRepository;
    private final PaymentCancellationRepository paymentCancellationRepository;
    private final PaymentRefundBalanceService paymentRefundBalanceService;

    public ReturnRefundCalculation confirm(
            Payment payment,
            SellerOrder sellerOrder,
            ReturnRequest request,
            List<OrderItem> sellerOrderItems,
            List<ReturnRequestItem> returnItems
    ) {
        if (payment == null || !payment.isRefundableState()
                || payment.getOrder().getStatus() != OrderStatus.PAID
                || sellerOrder.getStatus() != SellerOrderStatus.DELIVERED
                || request.getStatus() != ReturnRequestStatus.INSPECTED
                || request.getResponsibility() == null
                || request.getOrder() != payment.getOrder()
                || request.getSellerOrder() != sellerOrder) {
            throw new OrderException("현재 반품 요청의 환불 금액을 확정할 수 없습니다.");
        }
        Map<Long, Long> reservedQuantities = returnRequestRepository
                .sumCalculatedItemQuantities(
                        sellerOrder.getId(), request.getId(), CALCULATED_QUANTITY_STATUSES
                ).stream().collect(Collectors.toMap(
                        PendingReturnQuantityProjection::getOrderItemId,
                        PendingReturnQuantityProjection::getPendingQuantity
                ));
        boolean returnShippingClaimed = returnRequestRepository
                .existsBySellerOrderIdAndIdNotAndOriginalShippingRefundAmountGreaterThanAndStatusIn(
                        sellerOrder.getId(), request.getId(), 0L, SHIPPING_CLAIM_STATUSES
                );
        boolean cancellationShippingClaimed = paymentCancellationRepository
                .countShippingRefundsBySellerOrderId(
                        sellerOrder.getId(), CANCELLATION_CLAIM_STATUSES
                ) > 0L;
        ReturnRefundCalculation calculation = calculator.calculate(
                sellerOrder, request.getResponsibility(), sellerOrderItems, returnItems,
                reservedQuantities, returnShippingClaimed || cancellationShippingClaimed
        );
        if (calculation.refundAmount() > 0L) {
            PaymentRefundBalance balance = paymentRefundBalanceService.getBalance(payment);
            long returnSnapshotReserved = amount(returnRequestRepository
                    .sumRefundAmountByOrderIdExcludingRequest(
                            request.getOrder().getId(), request.getId(), CALCULATED_QUANTITY_STATUSES
                    ));
            long availableAfterReturnSnapshots;
            try {
                availableAfterReturnSnapshots = Math.subtractExact(
                        balance.availableRefundAmount(), returnSnapshotReserved
                );
            } catch (ArithmeticException exception) {
                throw new PaymentException("환불 가능 금액을 안전하게 계산할 수 없습니다.");
            }
            if (returnSnapshotReserved < 0L || availableAfterReturnSnapshots < 0L
                    || calculation.refundAmount() > availableAfterReturnSnapshots) {
                throw new PaymentException("환불 가능 금액을 초과했습니다.");
            }
            paymentRefundBalanceService.validateRefundAmount(balance, calculation.refundAmount());
        }
        request.confirmRefundCalculation(
                calculation.productRefundAmount(), calculation.originalShippingRefundAmount(),
                calculation.returnShippingCharge(), calculation.refundAmount()
        );
        return calculation;
    }

    private long amount(Long value) {
        return value == null ? 0L : value;
    }
}
