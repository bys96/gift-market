package com.giftmarket.settlement.entity;

import com.giftmarket.global.entity.BaseEntity;
import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "settlement_ledger_entries",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_settlement_ledger_source",
                        columnNames = {"source_type", "source_id", "source_detail_key"}
                ),
                @UniqueConstraint(
                        name = "uk_settlement_ledger_reversal",
                        columnNames = "reversal_of_entry_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_settlement_ledger_seller_eligible",
                        columnList = "seller_id, settlement_id, eligible_at, id"
                ),
                @Index(
                        name = "idx_settlement_ledger_settlement_order",
                        columnList = "settlement_id, seller_order_id, id"
                ),
                @Index(
                        name = "idx_settlement_ledger_order_occurred",
                        columnList = "seller_order_id, occurred_at, id"
                ),
                @Index(
                        name = "idx_settlement_ledger_type_occurred",
                        columnList = "type, occurred_at"
                ),
                @Index(
                        name = "idx_settlement_ledger_source",
                        columnList = "source_type, source_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementLedgerEntry extends BaseEntity {

    public static final String CURRENCY_KRW = "KRW";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "seller_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_settlement_ledger_seller")
    )
    private Seller seller;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "seller_order_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_settlement_ledger_seller_order")
    )
    private SellerOrder sellerOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "settlement_id",
            foreignKey = @ForeignKey(name = "fk_settlement_ledger_settlement")
    )
    private Settlement settlement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SettlementLedgerType type;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 40)
    private SettlementLedgerSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "source_detail_key", nullable = false, length = 100)
    private String sourceDetailKey;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "eligible_at")
    private LocalDateTime eligibleAt;

    @Column(name = "commission_rate_bps")
    private Integer commissionRateBps;

    @Column(name = "commission_base_amount")
    private Long commissionBaseAmount;

    @Column(length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "admin_user_id",
            foreignKey = @ForeignKey(name = "fk_settlement_ledger_admin")
    )
    private User adminUser;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "reversal_of_entry_id",
            foreignKey = @ForeignKey(name = "fk_settlement_ledger_reversal")
    )
    private SettlementLedgerEntry reversalOfEntry;

    @Version
    @Column(nullable = false)
    private Long version;

    public static SettlementLedgerEntry create(
            Seller seller,
            SellerOrder sellerOrder,
            SettlementLedgerType type,
            long amount,
            SettlementLedgerSourceType sourceType,
            Long sourceId,
            String sourceDetailKey,
            LocalDateTime occurredAt,
            LocalDateTime eligibleAt,
            Integer commissionRateBps,
            Long commissionBaseAmount,
            String reason,
            User adminUser,
            SettlementLedgerEntry reversalOfEntry
    ) {
        validateOwnership(seller, sellerOrder);
        if (type == null || sourceType == null) {
            throw new IllegalArgumentException("정산 원장 유형과 근거 유형이 필요합니다.");
        }
        validateAmount(type, amount);
        if (sourceId == null || sourceId <= 0L) {
            throw new IllegalArgumentException("정산 원장 근거 ID가 필요합니다.");
        }
        String normalizedDetailKey = requireText(
                sourceDetailKey,
                100,
                "정산 원장 근거 상세 키가 필요합니다."
        );
        if (occurredAt == null) {
            throw new IllegalArgumentException("정산 원장 발생 시각이 필요합니다.");
        }
        if (eligibleAt != null && eligibleAt.isBefore(occurredAt)) {
            throw new IllegalArgumentException("정산 가능 시각은 발생 시각보다 이전일 수 없습니다.");
        }
        validateCommissionSnapshot(type, commissionRateBps, commissionBaseAmount);
        String normalizedReason = normalizeReason(reason);
        validateManualAdjustment(type, sourceType, normalizedReason, adminUser);
        validateReversal(sourceType, sourceId, reversalOfEntry);

        SettlementLedgerEntry entry = new SettlementLedgerEntry();
        entry.seller = seller;
        entry.sellerOrder = sellerOrder;
        entry.type = type;
        entry.amount = amount;
        entry.currency = CURRENCY_KRW;
        entry.sourceType = sourceType;
        entry.sourceId = sourceId;
        entry.sourceDetailKey = normalizedDetailKey;
        entry.occurredAt = occurredAt;
        entry.eligibleAt = eligibleAt;
        entry.commissionRateBps = commissionRateBps;
        entry.commissionBaseAmount = commissionBaseAmount;
        entry.reason = normalizedReason;
        entry.adminUser = adminUser;
        entry.reversalOfEntry = reversalOfEntry;
        return entry;
    }

    public void activateEligibility(LocalDateTime eligibleAt) {
        ensureMutableLifecycle();
        if (eligibleAt == null || eligibleAt.isBefore(occurredAt)) {
            throw new IllegalArgumentException("올바른 정산 가능 시각이 필요합니다.");
        }
        if (this.eligibleAt == null) {
            this.eligibleAt = eligibleAt;
            return;
        }
        if (!this.eligibleAt.equals(eligibleAt)) {
            throw new IllegalStateException("정산 가능 시각은 이미 확정되었습니다.");
        }
    }

    public void assignTo(Settlement settlement) {
        if (settlement == null) {
            throw new IllegalArgumentException("귀속할 정산이 필요합니다.");
        }
        ensureMutableLifecycle();
        if (settlement.isConfirmed()) {
            throw new IllegalStateException("확정된 정산에는 원장을 귀속할 수 없습니다.");
        }
        if (!sameEntity(seller, settlement.getSeller())) {
            throw new IllegalArgumentException("원장과 정산의 판매자가 일치하지 않습니다.");
        }
        if (this.settlement == null) {
            this.settlement = settlement;
            return;
        }
        if (!sameEntity(this.settlement, settlement)) {
            throw new IllegalStateException("정산 원장은 이미 다른 정산에 귀속되었습니다.");
        }
    }

    private void ensureMutableLifecycle() {
        if (settlement != null && settlement.isConfirmed()) {
            throw new IllegalStateException("확정된 정산의 원장은 변경할 수 없습니다.");
        }
    }

    private static void validateOwnership(Seller seller, SellerOrder sellerOrder) {
        if (seller == null || sellerOrder == null || sellerOrder.getSeller() == null
                || !sameEntity(seller, sellerOrder.getSeller())) {
            throw new IllegalArgumentException("판매자와 판매자 주문의 소유자가 일치하지 않습니다.");
        }
    }

    private static void validateAmount(SettlementLedgerType type, long amount) {
        if (amount == 0L) {
            throw new IllegalArgumentException("0원 경제 이벤트는 정산 원장으로 생성하지 않습니다.");
        }
        boolean valid = switch (type) {
            case SALE_PRODUCT, SALE_SHIPPING, COMMISSION_REVERSAL -> amount > 0L;
            case CANCELLATION_REFUND, RETURN_REFUND, COMMISSION -> amount < 0L;
            case MANUAL_ADJUSTMENT -> true;
        };
        if (!valid) {
            throw new IllegalArgumentException("정산 원장 유형과 금액 부호가 일치하지 않습니다.");
        }
    }

    private static void validateCommissionSnapshot(
            SettlementLedgerType type,
            Integer commissionRateBps,
            Long commissionBaseAmount
    ) {
        if ((commissionRateBps == null) != (commissionBaseAmount == null)) {
            throw new IllegalArgumentException("수수료율과 수수료 기준금액은 함께 기록해야 합니다.");
        }
        if (commissionRateBps != null && (commissionRateBps < 0 || commissionRateBps > 10_000)) {
            throw new IllegalArgumentException("수수료율은 0에서 10000bp 사이여야 합니다.");
        }
        if (commissionBaseAmount != null && commissionBaseAmount < 0L) {
            throw new IllegalArgumentException("수수료 기준금액은 음수일 수 없습니다.");
        }
        if ((type == SettlementLedgerType.COMMISSION
                || type == SettlementLedgerType.COMMISSION_REVERSAL)
                && (commissionRateBps == null || commissionBaseAmount == null)) {
            throw new IllegalArgumentException("수수료 원장에는 수수료 snapshot이 필요합니다.");
        }
        if (type != SettlementLedgerType.SALE_PRODUCT
                && type != SettlementLedgerType.COMMISSION
                && type != SettlementLedgerType.COMMISSION_REVERSAL
                && commissionRateBps != null) {
            throw new IllegalArgumentException("해당 원장 유형에는 수수료 snapshot을 기록할 수 없습니다.");
        }
    }

    private static void validateManualAdjustment(
            SettlementLedgerType type,
            SettlementLedgerSourceType sourceType,
            String reason,
            User adminUser
    ) {
        if (type == SettlementLedgerType.MANUAL_ADJUSTMENT) {
            if (sourceType != SettlementLedgerSourceType.ADMIN_ADJUSTMENT
                    && sourceType != SettlementLedgerSourceType.LEDGER_ENTRY) {
                throw new IllegalArgumentException("수동 조정 원장의 근거 유형이 올바르지 않습니다.");
            }
            if (reason == null || adminUser == null) {
                throw new IllegalArgumentException("수동 조정 사유와 관리자가 필요합니다.");
            }
            return;
        }
        if (reason != null || adminUser != null) {
            throw new IllegalArgumentException("수동 조정 원장만 사유와 관리자를 가질 수 있습니다.");
        }
    }

    private static void validateReversal(
            SettlementLedgerSourceType sourceType,
            Long sourceId,
            SettlementLedgerEntry reversalOfEntry
    ) {
        if (reversalOfEntry == null) {
            return;
        }
        if (sourceType != SettlementLedgerSourceType.LEDGER_ENTRY
                || reversalOfEntry.getId() == null
                || !Objects.equals(sourceId, reversalOfEntry.getId())) {
            throw new IllegalArgumentException("정정 원장의 원본 참조가 올바르지 않습니다.");
        }
    }

    private static String normalizeReason(String reason) {
        if (reason == null) {
            return null;
        }
        return requireText(reason, 500, "정산 원장 사유는 500자 이하여야 합니다.");
    }

    private static String requireText(String value, int maxLength, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static boolean sameEntity(Object first, Object second) {
        if (first == second) {
            return true;
        }
        if (first instanceof Seller firstSeller && second instanceof Seller secondSeller) {
            return firstSeller.getId() != null
                    && firstSeller.getId() > 0L
                    && Objects.equals(firstSeller.getId(), secondSeller.getId());
        }
        if (first instanceof Settlement firstSettlement && second instanceof Settlement secondSettlement) {
            return firstSettlement.getId() != null
                    && firstSettlement.getId() > 0L
                    && Objects.equals(firstSettlement.getId(), secondSettlement.getId());
        }
        return false;
    }
}
