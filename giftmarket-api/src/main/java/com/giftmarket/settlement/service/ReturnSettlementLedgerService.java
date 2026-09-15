package com.giftmarket.settlement.service;

import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.ReturnRequest;
import com.giftmarket.order.entity.ReturnRequestItem;
import com.giftmarket.order.entity.ReturnRequestStatus;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.repository.ReturnRequestItemRepository;
import com.giftmarket.payment.entity.PaymentCancellation;
import com.giftmarket.payment.entity.PaymentCancellationStatus;
import com.giftmarket.payment.entity.PaymentCancellationType;
import com.giftmarket.settlement.config.SettlementProperties;
import com.giftmarket.settlement.entity.SettlementLedgerEntry;
import com.giftmarket.settlement.entity.SettlementLedgerSourceType;
import com.giftmarket.settlement.entity.SettlementLedgerType;
import com.giftmarket.settlement.exception.SettlementException;
import com.giftmarket.settlement.repository.SettlementLedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ReturnSettlementLedgerService {

    private static final String RETURN_REFUND_SUFFIX = ":RETURN_REFUND";
    private static final String COMMISSION_REVERSAL_SUFFIX = ":COMMISSION_REVERSAL";

    private final ReturnRequestItemRepository returnItemRepository;
    private final SettlementLedgerEntryRepository ledgerRepository;
    private final SettlementLedgerService ledgerService;
    private final SettlementCommissionCalculator commissionCalculator;
    private final RefundedProductAmountAggregator refundedProductAmountAggregator;
    private final SettlementProperties settlementProperties;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCompletedReturn(
            ReturnRequest request,
            PaymentCancellation paymentCancellation,
            SellerOrder sellerOrder
    ) {
        validateCompletedReturn(request, paymentCancellation, sellerOrder);
        validateRefundSnapshots(request, sellerOrder);

        SettlementLedgerEntry productSale = findInitialProductSale(sellerOrder);
        if (productSale == null) {
            return;
        }
        InitialCommission initialCommission = validateInitialCommission(
                sellerOrder,
                productSale
        );
        LocalDateTime eligibleAt = calculateEligibleAt(
                sellerOrder,
                request,
                paymentCancellation
        );

        if (request.getRefundAmount() > 0L) {
            SettlementLedgerEntry existingRefund = ledgerRepository
                    .findBySourceTypeAndSourceIdAndSourceDetailKey(
                            SettlementLedgerSourceType.PAYMENT_CANCELLATION,
                            paymentCancellation.getId(),
                            detailKey(sellerOrder.getId(), RETURN_REFUND_SUFFIX)
                    )
                    .orElse(null);
            if (existingRefund != null) {
                validateExistingRefund(
                        existingRefund,
                        request,
                        paymentCancellation,
                        sellerOrder
                );
                return;
            }
        } else {
            SettlementLedgerEntry existingReversal = ledgerRepository
                    .findBySourceTypeAndSourceIdAndSourceDetailKey(
                            SettlementLedgerSourceType.RETURN_REQUEST,
                            request.getId(),
                            detailKey(sellerOrder.getId(), COMMISSION_REVERSAL_SUFFIX)
                    )
                    .orElse(null);
            if (existingReversal != null) {
                validateExistingZeroRefundReversal(
                        existingReversal,
                        request,
                        sellerOrder,
                        eligibleAt
                );
                return;
            }
        }

        long cumulativeProductRefund = refundedProductAmountAggregator.calculate(sellerOrder);
        if (cumulativeProductRefund > productSale.getAmount()) {
            throw new SettlementException("누적 상품 환불액이 최초 상품 매출을 초과합니다.");
        }
        long alreadyReversed = positiveSum(
                ledgerRepository.sumAmountBySellerOrderIdAndType(
                        sellerOrder.getId(),
                        SettlementLedgerType.COMMISSION_REVERSAL
                )
        );
        long currentReversal;
        try {
            currentReversal = commissionCalculator.calculateCurrentReversal(
                    cumulativeProductRefund,
                    initialCommission.rateBps(),
                    alreadyReversed,
                    initialCommission.amount()
            );
        } catch (IllegalArgumentException exception) {
            throw new SettlementException("반품 수수료 환입 금액을 계산할 수 없습니다.");
        }

        if (request.getRefundAmount() > 0L) {
            ledgerService.recordReturnRefund(SettlementLedgerCommand.standard(
                    sellerOrder.getSeller().getId(),
                    sellerOrder.getId(),
                    paymentCancellation.getAmount(),
                    paymentCancellation.getId(),
                    detailKey(sellerOrder.getId(), RETURN_REFUND_SUFFIX),
                    paymentCancellation.getCanceledAt(),
                    eligibleAt
            ));
        }
        if (currentReversal > 0L) {
            SettlementLedgerCommand command = SettlementLedgerCommand.commission(
                    sellerOrder.getSeller().getId(),
                    sellerOrder.getId(),
                    currentReversal,
                    request.getRefundAmount() > 0L
                            ? paymentCancellation.getId()
                            : request.getId(),
                    detailKey(sellerOrder.getId(), COMMISSION_REVERSAL_SUFFIX),
                    request.getRefundAmount() > 0L
                            ? paymentCancellation.getCanceledAt()
                            : request.getCompletedAt(),
                    eligibleAt,
                    initialCommission.rateBps(),
                    cumulativeProductRefund
            );
            if (request.getRefundAmount() > 0L) {
                ledgerService.recordCommissionReversal(command);
            } else {
                ledgerService.recordZeroRefundReturnCommissionReversal(command);
            }
        }
    }

    private void validateCompletedReturn(
            ReturnRequest request,
            PaymentCancellation paymentCancellation,
            SellerOrder sellerOrder
    ) {
        if (request == null || request.getId() == null
                || sellerOrder == null || sellerOrder.getId() == null
                || sellerOrder.getSeller() == null || sellerOrder.getSeller().getId() == null
                || request.getStatus() != ReturnRequestStatus.COMPLETED
                || request.getCompletedAt() == null
                || request.getSellerOrder() != sellerOrder
                || request.getOrder() != sellerOrder.getOrder()
                || sellerOrder.getStatus() != SellerOrderStatus.DELIVERED
                || sellerOrder.getDeliveredAt() == null
                || request.getRefundAmount() == null || request.getRefundAmount() < 0L) {
            throw new SettlementException("완료된 반품 정산 정보를 확인할 수 없습니다.");
        }
        if (request.getRefundAmount() == 0L) {
            if (paymentCancellation != null) {
                throw new SettlementException("0원 반품에는 PG 환불 거래가 존재할 수 없습니다.");
            }
            return;
        }
        if (paymentCancellation == null || paymentCancellation.getId() == null
                || paymentCancellation.getStatus() != PaymentCancellationStatus.SUCCEEDED
                || paymentCancellation.getType() != PaymentCancellationType.PARTIAL
                || paymentCancellation.getReturnRequest() != request
                || paymentCancellation.getOrderCancellation() != null
                || paymentCancellation.getPayment() == null
                || paymentCancellation.getPayment().getOrder() != request.getOrder()
                || paymentCancellation.getCanceledAt() == null
                || !Objects.equals(paymentCancellation.getAmount(), request.getRefundAmount())) {
            throw new SettlementException("성공한 반품 PG 환불 정보를 확인할 수 없습니다.");
        }
    }

    private void validateRefundSnapshots(ReturnRequest request, SellerOrder sellerOrder) {
        if (request.getProductRefundAmount() == null || request.getProductRefundAmount() < 0L
                || request.getOriginalShippingRefundAmount() == null
                || request.getOriginalShippingRefundAmount() < 0L
                || request.getReturnShippingCharge() == null
                || request.getReturnShippingCharge() < 0L) {
            throw new SettlementException("반품 환불 snapshot이 올바르지 않습니다.");
        }
        long expected;
        try {
            expected = Math.subtractExact(
                    Math.addExact(
                            request.getProductRefundAmount(),
                            request.getOriginalShippingRefundAmount()
                    ),
                    request.getReturnShippingCharge()
            );
        } catch (ArithmeticException exception) {
            throw new SettlementException("반품 환불 snapshot 합계를 안전하게 계산할 수 없습니다.");
        }
        if (expected != request.getRefundAmount()) {
            throw new SettlementException("반품 환불 snapshot 합계가 최종 환불액과 일치하지 않습니다.");
        }

        List<ReturnRequestItem> items = returnItemRepository
                .findAllByReturnRequestIdOrderByIdAsc(request.getId());
        if (items.isEmpty()) {
            throw new SettlementException("반품 상품 snapshot을 확인할 수 없습니다.");
        }
        long productAmount = 0L;
        for (ReturnRequestItem item : items) {
            OrderItem orderItem = item.getOrderItem();
            if (item.getReturnRequest() != request || orderItem == null
                    || orderItem.getSellerOrder() != sellerOrder
                    || orderItem.getSeller() != sellerOrder.getSeller()
                    || orderItem.getUnitPrice() == null || orderItem.getUnitPrice() <= 0L
                    || item.getQuantity() <= 0) {
                throw new SettlementException("반품 상품의 판매자 주문 귀속이 일치하지 않습니다.");
            }
            try {
                productAmount = Math.addExact(productAmount, Math.multiplyExact(
                        orderItem.getUnitPrice(),
                        (long) item.getQuantity()
                ));
            } catch (ArithmeticException exception) {
                throw new SettlementException("반품 상품 환불액을 안전하게 계산할 수 없습니다.");
            }
        }
        if (productAmount != request.getProductRefundAmount()) {
            throw new SettlementException("반품 상품 환불 snapshot이 주문 금액과 일치하지 않습니다.");
        }
    }

    private LocalDateTime calculateEligibleAt(
            SellerOrder sellerOrder,
            ReturnRequest request,
            PaymentCancellation paymentCancellation
    ) {
        LocalDateTime eligibleAt;
        try {
            eligibleAt = sellerOrder.getDeliveredAt().plusDays(
                    settlementProperties.getHoldDays()
            );
        } catch (DateTimeException | ArithmeticException exception) {
            throw new SettlementException("반품 정산 가능 시각을 계산할 수 없습니다.");
        }
        if (request.getCompletedAt().isAfter(eligibleAt)) {
            eligibleAt = request.getCompletedAt();
        }
        if (paymentCancellation != null
                && paymentCancellation.getCanceledAt().isAfter(eligibleAt)) {
            eligibleAt = paymentCancellation.getCanceledAt();
        }
        return eligibleAt;
    }

    private SettlementLedgerEntry findInitialProductSale(SellerOrder sellerOrder) {
        SettlementLedgerEntry productSale = ledgerRepository
                .findBySourceTypeAndSourceIdAndSourceDetailKey(
                        SettlementLedgerSourceType.SELLER_ORDER,
                        sellerOrder.getId(),
                        InitialSettlementLedgerService.SALE_PRODUCT_SOURCE_DETAIL
                )
                .orElse(null);
        if (productSale != null) {
            return productSale;
        }
        if (!ledgerRepository.existsBySellerOrderIdAndSourceTypeAndSourceId(
                sellerOrder.getId(),
                SettlementLedgerSourceType.SELLER_ORDER,
                sellerOrder.getId()
        )) {
            return null;
        }
        throw new SettlementException("상품 매출 정산 원장을 찾을 수 없습니다.");
    }

    private InitialCommission validateInitialCommission(
            SellerOrder sellerOrder,
            SettlementLedgerEntry productSale
    ) {
        if (productSale.getType() != SettlementLedgerType.SALE_PRODUCT
                || productSale.getSellerOrder() == null
                || !Objects.equals(productSale.getSellerOrder().getId(), sellerOrder.getId())
                || productSale.getSeller() == null
                || !Objects.equals(productSale.getSeller().getId(), sellerOrder.getSeller().getId())
                || productSale.getCommissionRateBps() == null
                || productSale.getCommissionBaseAmount() == null
                || !Objects.equals(productSale.getCommissionBaseAmount(), productSale.getAmount())) {
            throw new SettlementException("상품 매출 원장의 수수료 snapshot이 올바르지 않습니다.");
        }
        long expectedCommission;
        try {
            expectedCommission = commissionCalculator.calculate(
                    productSale.getAmount(),
                    productSale.getCommissionRateBps()
            );
        } catch (IllegalArgumentException exception) {
            throw new SettlementException("최초 수수료 금액을 검증할 수 없습니다.");
        }
        SettlementLedgerEntry commission = ledgerRepository
                .findBySourceTypeAndSourceIdAndSourceDetailKey(
                        SettlementLedgerSourceType.SELLER_ORDER,
                        sellerOrder.getId(),
                        InitialSettlementLedgerService.COMMISSION_SOURCE_DETAIL
                )
                .orElse(null);
        if (expectedCommission == 0L) {
            if (commission != null) {
                throw new SettlementException("0원 수수료 주문에 수수료 원장이 존재합니다.");
            }
            return new InitialCommission(productSale.getCommissionRateBps(), 0L);
        }
        if (commission == null || commission.getType() != SettlementLedgerType.COMMISSION
                || commission.getAmount() == Long.MIN_VALUE
                || -commission.getAmount() != expectedCommission
                || !Objects.equals(commission.getCommissionRateBps(), productSale.getCommissionRateBps())
                || !Objects.equals(commission.getCommissionBaseAmount(), productSale.getAmount())) {
            throw new SettlementException("최초 수수료 원장이 상품 매출 snapshot과 일치하지 않습니다.");
        }
        return new InitialCommission(productSale.getCommissionRateBps(), expectedCommission);
    }

    private void validateExistingRefund(
            SettlementLedgerEntry entry,
            ReturnRequest request,
            PaymentCancellation cancellation,
            SellerOrder sellerOrder
    ) {
        if (entry.getType() != SettlementLedgerType.RETURN_REFUND
                || !Objects.equals(entry.getAmount(), -cancellation.getAmount())
                || entry.getSellerOrder() == null
                || !Objects.equals(entry.getSellerOrder().getId(), sellerOrder.getId())
                || !Objects.equals(entry.getOccurredAt(), cancellation.getCanceledAt())) {
            throw new SettlementException("동일 반품 환불 근거에 다른 정산 payload가 존재합니다.");
        }
    }

    private void validateExistingZeroRefundReversal(
            SettlementLedgerEntry entry,
            ReturnRequest request,
            SellerOrder sellerOrder,
            LocalDateTime eligibleAt
    ) {
        if (entry.getType() != SettlementLedgerType.COMMISSION_REVERSAL
                || entry.getSellerOrder() == null
                || !Objects.equals(entry.getSellerOrder().getId(), sellerOrder.getId())
                || !Objects.equals(entry.getOccurredAt(), request.getCompletedAt())
                || !Objects.equals(entry.getEligibleAt(), eligibleAt)) {
            throw new SettlementException("동일 0원 반품 근거에 다른 정산 payload가 존재합니다.");
        }
    }

    private long positiveSum(Long value) {
        if (value == null || value < 0L) {
            throw new SettlementException("수수료 환입 누계가 올바르지 않습니다.");
        }
        return value;
    }

    private String detailKey(Long sellerOrderId, String suffix) {
        return "SO:" + sellerOrderId + suffix;
    }

    private record InitialCommission(int rateBps, long amount) { }
}
