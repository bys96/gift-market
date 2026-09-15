package com.giftmarket.settlement.service;

import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.entity.SellerOrderStatus;
import com.giftmarket.order.entity.Shipment;
import com.giftmarket.order.entity.ShipmentStatus;
import com.giftmarket.order.entity.ShipmentType;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SettlementEligibilityService {

    private static final Set<SettlementLedgerType> INITIAL_SALE_TYPES = Set.of(
            SettlementLedgerType.SALE_PRODUCT,
            SettlementLedgerType.SALE_SHIPPING,
            SettlementLedgerType.COMMISSION
    );
    private static final Set<SettlementLedgerType> DELIVERY_ELIGIBILITY_TYPES = Set.of(
            SettlementLedgerType.SALE_PRODUCT,
            SettlementLedgerType.SALE_SHIPPING,
            SettlementLedgerType.COMMISSION,
            SettlementLedgerType.CANCELLATION_REFUND,
            SettlementLedgerType.COMMISSION_REVERSAL
    );

    private final SettlementLedgerEntryRepository ledgerRepository;
    private final SettlementProperties settlementProperties;

    @Transactional(propagation = Propagation.MANDATORY)
    public void activateInitialSalesEligibility(
            SellerOrder sellerOrder,
            Shipment originalOutboundShipment
    ) {
        LocalDateTime deliveredAt = validateDelivery(sellerOrder, originalOutboundShipment);
        LocalDateTime eligibleAt = calculateEligibleAt(deliveredAt);
        Long sellerOrderId = sellerOrder.getId();

        List<SettlementLedgerEntry> entries = ledgerRepository.findInitialSalesForUpdate(
                sellerOrderId,
                SettlementLedgerSourceType.SELLER_ORDER,
                sellerOrderId,
                INITIAL_SALE_TYPES
        );
        if (entries.isEmpty() && !ledgerRepository.existsBySellerOrderIdAndSourceTypeAndSourceId(
                sellerOrderId,
                SettlementLedgerSourceType.SELLER_ORDER,
                sellerOrderId
        )) {
            // Settlement 도입 전에 결제된 주문은 별도 backfill 대상이다.
            return;
        }

        Map<SettlementLedgerType, SettlementLedgerEntry> byType = validateInitialEntries(
                sellerOrder,
                entries
        );
        SettlementLedgerEntry productSale = byType.get(SettlementLedgerType.SALE_PRODUCT);
        validateProductCommissionSnapshot(productSale);
        validateOptionalEntries(byType, productSale);

        for (SettlementLedgerEntry entry : entries) {
            activate(entry, eligibleAt);
        }

        List<SettlementLedgerEntry> cancellationEntries = ledgerRepository
                .findEligibilityEntriesForUpdate(sellerOrderId, Set.of(
                        SettlementLedgerType.CANCELLATION_REFUND,
                        SettlementLedgerType.COMMISSION_REVERSAL
                ));
        for (SettlementLedgerEntry entry : cancellationEntries) {
            LocalDateTime cancellationEligibleAt = entry.getOccurredAt().isAfter(eligibleAt)
                    ? entry.getOccurredAt()
                    : eligibleAt;
            activate(entry, cancellationEligibleAt);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void activateFullCancellationEligibility(
            SellerOrder sellerOrder,
            LocalDateTime canceledAt
    ) {
        if (sellerOrder == null || sellerOrder.getId() == null
                || sellerOrder.getStatus() != SellerOrderStatus.CANCELLED
                || sellerOrder.getDeliveredAt() != null || canceledAt == null) {
            throw new SettlementException("배송 전 전량취소 정산 정보를 확인할 수 없습니다.");
        }
        List<SettlementLedgerEntry> entries = ledgerRepository
                .findEligibilityEntriesForUpdate(
                        sellerOrder.getId(),
                        DELIVERY_ELIGIBILITY_TYPES
                );
        if (entries.isEmpty()) {
            return;
        }
        boolean hasProductSale = entries.stream().anyMatch(entry ->
                entry.getType() == SettlementLedgerType.SALE_PRODUCT
                        && entry.getSourceType() == SettlementLedgerSourceType.SELLER_ORDER
                        && Objects.equals(entry.getSourceId(), sellerOrder.getId())
        );
        if (!hasProductSale) {
            throw new SettlementException("상품 매출 정산 원장을 찾을 수 없습니다.");
        }
        for (SettlementLedgerEntry entry : entries) {
            activate(entry, canceledAt);
        }
    }

    private LocalDateTime validateDelivery(
            SellerOrder sellerOrder,
            Shipment shipment
    ) {
        if (sellerOrder == null || sellerOrder.getId() == null
                || shipment == null || shipment.getSellerOrder() == null
                || !Objects.equals(shipment.getSellerOrder().getId(), sellerOrder.getId())
                || shipment.getType() != ShipmentType.ORIGINAL_OUTBOUND
                || shipment.getStatus() != ShipmentStatus.DELIVERED
                || shipment.getDeliveredAt() == null
                || sellerOrder.getStatus() != SellerOrderStatus.DELIVERED
                || sellerOrder.getDeliveredAt() == null) {
            throw new SettlementException("최초 출고 배송완료 정보를 확인할 수 없습니다.");
        }
        if (!shipment.getDeliveredAt().equals(sellerOrder.getDeliveredAt())) {
            throw new SettlementException("배송과 판매자 주문의 배송완료 시각이 일치하지 않습니다.");
        }
        return shipment.getDeliveredAt();
    }

    private LocalDateTime calculateEligibleAt(LocalDateTime deliveredAt) {
        try {
            return deliveredAt.plusDays(settlementProperties.getHoldDays());
        } catch (DateTimeException | ArithmeticException exception) {
            throw new SettlementException("정산 가능 예정 시각을 계산할 수 없습니다.");
        }
    }

    private Map<SettlementLedgerType, SettlementLedgerEntry> validateInitialEntries(
            SellerOrder sellerOrder,
            List<SettlementLedgerEntry> entries
    ) {
        Map<SettlementLedgerType, SettlementLedgerEntry> byType = new EnumMap<>(
                SettlementLedgerType.class
        );
        for (SettlementLedgerEntry entry : entries) {
            if (entry.getSellerOrder() == null
                    || !Objects.equals(entry.getSellerOrder().getId(), sellerOrder.getId())
                    || entry.getSeller() == null || sellerOrder.getSeller() == null
                    || !Objects.equals(entry.getSeller().getId(), sellerOrder.getSeller().getId())
                    || entry.getSourceType() != SettlementLedgerSourceType.SELLER_ORDER
                    || !Objects.equals(entry.getSourceId(), sellerOrder.getId())
                    || !entry.getType().name().equals(entry.getSourceDetailKey())
                    || byType.put(entry.getType(), entry) != null) {
                throw new SettlementException("최초 판매 정산 원장의 귀속 또는 근거가 올바르지 않습니다.");
            }
        }
        if (!byType.containsKey(SettlementLedgerType.SALE_PRODUCT)) {
            throw new SettlementException("상품 매출 정산 원장을 찾을 수 없습니다.");
        }
        return byType;
    }

    private void validateProductCommissionSnapshot(SettlementLedgerEntry productSale) {
        if (productSale.getCommissionRateBps() == null
                || productSale.getCommissionRateBps() < 0
                || productSale.getCommissionRateBps() > 10_000
                || productSale.getCommissionBaseAmount() == null
                || !Objects.equals(productSale.getCommissionBaseAmount(), productSale.getAmount())) {
            throw new SettlementException("상품 매출 원장의 수수료 snapshot이 올바르지 않습니다.");
        }
    }

    private void validateOptionalEntries(
            Map<SettlementLedgerType, SettlementLedgerEntry> byType,
            SettlementLedgerEntry productSale
    ) {
        SettlementLedgerEntry shippingSale = byType.get(SettlementLedgerType.SALE_SHIPPING);
        if (shippingSale != null && (shippingSale.getCommissionRateBps() != null
                || shippingSale.getCommissionBaseAmount() != null)) {
            throw new SettlementException("배송비 매출 원장에는 수수료 snapshot을 기록할 수 없습니다.");
        }

        SettlementLedgerEntry commission = byType.get(SettlementLedgerType.COMMISSION);
        if (commission != null
                && (!Objects.equals(
                        commission.getCommissionRateBps(),
                        productSale.getCommissionRateBps()
                ) || !Objects.equals(
                        commission.getCommissionBaseAmount(),
                        productSale.getCommissionBaseAmount()
                ))) {
            throw new SettlementException("매출과 수수료 원장의 수수료 snapshot이 일치하지 않습니다.");
        }
    }

    private void activate(SettlementLedgerEntry entry, LocalDateTime eligibleAt) {
        if (entry.getEligibleAt() != null) {
            if (entry.getEligibleAt().equals(eligibleAt)) {
                return;
            }
            throw new SettlementException("정산 가능 예정 시각이 이미 다른 값으로 확정되었습니다.");
        }
        if (entry.getSettlement() != null && entry.getSettlement().isConfirmed()) {
            throw new SettlementException("확정된 정산에 귀속된 원장은 변경할 수 없습니다.");
        }
        try {
            entry.activateEligibility(eligibleAt);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new SettlementException(exception.getMessage());
        }
    }
}
