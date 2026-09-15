package com.giftmarket.settlement.service;

import com.giftmarket.order.entity.OrderCancellation;
import com.giftmarket.order.entity.OrderCancellationItem;
import com.giftmarket.order.entity.OrderCancellationStatus;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.repository.OrderCancellationItemRepository;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.payment.entity.PaymentCancellation;
import com.giftmarket.payment.entity.PaymentCancellationStatus;
import com.giftmarket.payment.entity.PaymentCancellationType;
import com.giftmarket.settlement.entity.SettlementLedgerEntry;
import com.giftmarket.settlement.entity.SettlementLedgerSourceType;
import com.giftmarket.settlement.entity.SettlementLedgerType;
import com.giftmarket.settlement.exception.SettlementException;
import com.giftmarket.settlement.repository.SettlementLedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CancellationSettlementLedgerService {

    private static final String REFUND_DETAIL_SUFFIX = ":CANCELLATION_REFUND";
    private static final String REVERSAL_DETAIL_SUFFIX = ":COMMISSION_REVERSAL";

    private final OrderCancellationItemRepository cancellationItemRepository;
    private final OrderItemRepository orderItemRepository;
    private final SettlementLedgerEntryRepository ledgerRepository;
    private final SettlementLedgerService ledgerService;
    private final SettlementCommissionCalculator commissionCalculator;
    private final SettlementEligibilityService eligibilityService;
    private final RefundedProductAmountAggregator refundedProductAmountAggregator;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCancellation(
            OrderCancellation cancellation,
            PaymentCancellation paymentCancellation,
            SellerOrder sellerOrder
    ) {
        validateCompletedCancellation(cancellation, paymentCancellation, sellerOrder);

        String refundDetailKey = detailKey(sellerOrder.getId(), REFUND_DETAIL_SUFFIX);
        SettlementLedgerEntry existingRefund = ledgerRepository
                .findBySourceTypeAndSourceIdAndSourceDetailKey(
                        SettlementLedgerSourceType.PAYMENT_CANCELLATION,
                        paymentCancellation.getId(),
                        refundDetailKey
                )
                .orElse(null);
        if (existingRefund != null) {
            validateExistingRefund(existingRefund, paymentCancellation, sellerOrder);
            return;
        }

        SettlementLedgerEntry productSale = findInitialProductSale(sellerOrder);
        if (productSale == null) {
            return;
        }
        InitialCommission initialCommission = validateInitialCommission(
                sellerOrder,
                productSale
        );
        calculateAndValidateRefund(
                cancellation,
                paymentCancellation,
                sellerOrder
        );
        long cumulativeProductRefund = refundedProductAmountAggregator.calculate(sellerOrder);
        if (cumulativeProductRefund > productSale.getAmount()) {
            throw new SettlementException("누적 상품 취소 환불액이 최초 상품 매출을 초과합니다.");
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
            throw new SettlementException("취소 수수료 환입 금액을 계산할 수 없습니다.");
        }

        LocalDateTime eligibleAt = sellerOrder.getStatus() == SellerOrderStatus.CANCELLED
                ? paymentCancellation.getCanceledAt()
                : null;
        ledgerService.recordCancellationRefund(SettlementLedgerCommand.standard(
                sellerOrder.getSeller().getId(),
                sellerOrder.getId(),
                paymentCancellation.getAmount(),
                paymentCancellation.getId(),
                refundDetailKey,
                paymentCancellation.getCanceledAt(),
                eligibleAt
        ));
        if (currentReversal > 0L) {
            ledgerService.recordCommissionReversal(SettlementLedgerCommand.commission(
                    sellerOrder.getSeller().getId(),
                    sellerOrder.getId(),
                    currentReversal,
                    paymentCancellation.getId(),
                    detailKey(sellerOrder.getId(), REVERSAL_DETAIL_SUFFIX),
                    paymentCancellation.getCanceledAt(),
                    eligibleAt,
                    initialCommission.rateBps(),
                    cumulativeProductRefund
            ));
        }
        if (sellerOrder.getStatus() == SellerOrderStatus.CANCELLED) {
            eligibilityService.activateFullCancellationEligibility(
                    sellerOrder,
                    paymentCancellation.getCanceledAt()
            );
        }
    }

    private void validateCompletedCancellation(
            OrderCancellation cancellation,
            PaymentCancellation paymentCancellation,
            SellerOrder sellerOrder
    ) {
        if (cancellation == null || cancellation.getId() == null
                || paymentCancellation == null || paymentCancellation.getId() == null
                || sellerOrder == null || sellerOrder.getId() == null
                || sellerOrder.getSeller() == null || sellerOrder.getSeller().getId() == null
                || cancellation.getStatus() != OrderCancellationStatus.COMPLETED
                || paymentCancellation.getStatus() != PaymentCancellationStatus.SUCCEEDED
                || paymentCancellation.getType() != PaymentCancellationType.PARTIAL
                || paymentCancellation.getOrderCancellation() != cancellation
                || cancellation.getSellerOrder() != sellerOrder
                || cancellation.getOrder() != sellerOrder.getOrder()
                || paymentCancellation.getPayment() == null
                || paymentCancellation.getPayment().getOrder() != cancellation.getOrder()
                || paymentCancellation.getAmount() == null
                || paymentCancellation.getAmount() <= 0L
                || paymentCancellation.getCanceledAt() == null) {
            throw new SettlementException("성공한 주문 취소 환불 정보를 확인할 수 없습니다.");
        }
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
            // Settlement 도입 전에 결제된 주문은 별도 backfill 대상이다.
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

    private void calculateAndValidateRefund(
            OrderCancellation cancellation,
            PaymentCancellation paymentCancellation,
            SellerOrder sellerOrder
    ) {
        List<OrderCancellationItem> cancellationItems = cancellationItemRepository
                .findAllByOrderCancellationIdOrderByIdAsc(cancellation.getId());
        List<OrderItem> sellerOrderItems = orderItemRepository
                .findAllBySellerOrderIdForUpdate(sellerOrder.getId());
        if (cancellationItems.isEmpty() || sellerOrderItems.isEmpty()) {
            throw new SettlementException("취소 상품 snapshot을 확인할 수 없습니다.");
        }
        long productRefund = sumProductRefund(cancellationItems, cancellation, sellerOrder);
        long expectedShippingRefund = sellerOrder.getStatus() == SellerOrderStatus.CANCELLED
                ? sumShippingFees(sellerOrderItems, sellerOrder)
                : 0L;
        long expectedTotal;
        try {
            expectedTotal = Math.addExact(productRefund, expectedShippingRefund);
        } catch (ArithmeticException exception) {
            throw new SettlementException("취소 환불 합계를 안전하게 계산할 수 없습니다.");
        }
        if (expectedTotal != paymentCancellation.getAmount()) {
            throw new SettlementException("PG 취소 금액과 상품·배송비 환불 snapshot이 일치하지 않습니다.");
        }
    }

    private long sumProductRefund(
            List<OrderCancellationItem> items,
            OrderCancellation cancellation,
            SellerOrder sellerOrder
    ) {
        long amount = 0L;
        for (OrderCancellationItem item : items) {
            if (item.getOrderCancellation() != cancellation
                    || item.getOrderItem() == null
                    || item.getOrderItem().getSellerOrder() != sellerOrder
                    || item.getOrderItem().getSeller() != sellerOrder.getSeller()) {
                throw new SettlementException("취소 상품의 판매자 주문 귀속이 일치하지 않습니다.");
            }
            try {
                amount = Math.addExact(amount, productRefund(item));
            } catch (ArithmeticException exception) {
                throw new SettlementException("상품 취소 환불액을 안전하게 계산할 수 없습니다.");
            }
        }
        return amount;
    }

    private long productRefund(OrderCancellationItem item) {
        OrderItem orderItem = item.getOrderItem();
        if (orderItem == null || orderItem.getUnitPrice() == null
                || orderItem.getUnitPrice() <= 0L || item.getQuantity() <= 0) {
            throw new SettlementException("취소 상품 금액 snapshot이 올바르지 않습니다.");
        }
        try {
            return Math.multiplyExact(orderItem.getUnitPrice(), (long) item.getQuantity());
        } catch (ArithmeticException exception) {
            throw new SettlementException("상품 취소 환불액을 안전하게 계산할 수 없습니다.");
        }
    }

    private long sumShippingFees(List<OrderItem> items, SellerOrder sellerOrder) {
        long amount = 0L;
        for (OrderItem item : items) {
            if (item.getSellerOrder() != sellerOrder || item.getSeller() != sellerOrder.getSeller()
                    || item.getShippingFee() == null || item.getShippingFee() < 0L) {
                throw new SettlementException("원 배송비 snapshot이 올바르지 않습니다.");
            }
            try {
                amount = Math.addExact(amount, item.getShippingFee());
            } catch (ArithmeticException exception) {
                throw new SettlementException("원 배송비 환불액을 안전하게 계산할 수 없습니다.");
            }
        }
        return amount;
    }

    private void validateExistingRefund(
            SettlementLedgerEntry entry,
            PaymentCancellation paymentCancellation,
            SellerOrder sellerOrder
    ) {
        if (entry.getType() != SettlementLedgerType.CANCELLATION_REFUND
                || !Objects.equals(entry.getAmount(), -paymentCancellation.getAmount())
                || entry.getSellerOrder() == null
                || !Objects.equals(entry.getSellerOrder().getId(), sellerOrder.getId())
                || !Objects.equals(entry.getOccurredAt(), paymentCancellation.getCanceledAt())) {
            throw new SettlementException("동일 취소 환불 근거에 다른 정산 payload가 존재합니다.");
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
