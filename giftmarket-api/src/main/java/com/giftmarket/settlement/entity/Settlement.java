package com.giftmarket.settlement.entity;

import com.giftmarket.global.entity.BaseEntity;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "settlements",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_settlements_number",
                        columnNames = "settlement_number"
                ),
                @UniqueConstraint(
                        name = "uk_settlements_seller_period",
                        columnNames = {"seller_id", "period_start", "period_end"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_settlements_seller_period",
                        columnList = "seller_id, period_end, id"
                ),
                @Index(
                        name = "idx_settlements_status_period",
                        columnList = "status, period_end, id"
                ),
                @Index(
                        name = "idx_settlements_seller_status_period",
                        columnList = "seller_id, status, period_end"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement extends BaseEntity {

    public static final String CURRENCY_KRW = "KRW";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "seller_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_settlements_seller")
    )
    private Seller seller;

    @Column(name = "settlement_number", nullable = false, length = 50)
    private String settlementNumber;

    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(name = "total_product_sales_amount", nullable = false)
    private Long totalProductSalesAmount;

    @Column(name = "total_shipping_sales_amount", nullable = false)
    private Long totalShippingSalesAmount;

    @Column(name = "total_cancellation_amount", nullable = false)
    private Long totalCancellationAmount;

    @Column(name = "total_return_amount", nullable = false)
    private Long totalReturnAmount;

    @Column(name = "total_commission_amount", nullable = false)
    private Long totalCommissionAmount;

    @Column(name = "total_adjustment_amount", nullable = false)
    private Long totalAdjustmentAmount;

    @Column(name = "settlement_amount", nullable = false)
    private Long settlementAmount;

    @Column(name = "ledger_entry_count", nullable = false)
    private Integer ledgerEntryCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementStatus status;

    @Column(name = "hold_reason", length = 500)
    private String holdReason;

    @Column(name = "held_at")
    private LocalDateTime heldAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "held_by_admin_user_id",
            foreignKey = @ForeignKey(name = "fk_settlements_held_admin")
    )
    private User heldByAdminUser;

    @Column(name = "hold_released_at")
    private LocalDateTime holdReleasedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "hold_released_by_admin_user_id",
            foreignKey = @ForeignKey(name = "fk_settlements_released_admin")
    )
    private User holdReleasedByAdminUser;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "confirmed_by_admin_user_id",
            foreignKey = @ForeignKey(name = "fk_settlements_confirmed_admin")
    )
    private User confirmedByAdminUser;

    @Version
    @Column(nullable = false)
    private Long version;

    public static Settlement create(
            Seller seller,
            String settlementNumber,
            LocalDateTime periodStart,
            LocalDateTime periodEnd,
            long totalProductSalesAmount,
            long totalShippingSalesAmount,
            long totalCancellationAmount,
            long totalReturnAmount,
            long totalCommissionAmount,
            long totalAdjustmentAmount,
            int ledgerEntryCount
    ) {
        if (seller == null) {
            throw new IllegalArgumentException("정산 판매자가 필요합니다.");
        }
        String normalizedNumber = requireText(
                settlementNumber,
                50,
                "정산번호가 필요합니다."
        );
        if (periodStart == null || periodEnd == null || !periodStart.isBefore(periodEnd)) {
            throw new IllegalArgumentException("정산 시작 시각은 종료 시각보다 이전이어야 합니다.");
        }
        if (totalProductSalesAmount < 0L || totalShippingSalesAmount < 0L
                || totalCancellationAmount < 0L || totalReturnAmount < 0L) {
            throw new IllegalArgumentException("정산 매출 및 환불 합계는 음수일 수 없습니다.");
        }
        if (ledgerEntryCount <= 0) {
            throw new IllegalArgumentException("정산에는 하나 이상의 원장이 필요합니다.");
        }

        long calculatedAmount = calculateSettlementAmount(
                totalProductSalesAmount,
                totalShippingSalesAmount,
                totalCancellationAmount,
                totalReturnAmount,
                totalCommissionAmount,
                totalAdjustmentAmount
        );

        Settlement settlement = new Settlement();
        settlement.seller = seller;
        settlement.settlementNumber = normalizedNumber;
        settlement.periodStart = periodStart;
        settlement.periodEnd = periodEnd;
        settlement.currency = CURRENCY_KRW;
        settlement.totalProductSalesAmount = totalProductSalesAmount;
        settlement.totalShippingSalesAmount = totalShippingSalesAmount;
        settlement.totalCancellationAmount = totalCancellationAmount;
        settlement.totalReturnAmount = totalReturnAmount;
        settlement.totalCommissionAmount = totalCommissionAmount;
        settlement.totalAdjustmentAmount = totalAdjustmentAmount;
        settlement.settlementAmount = calculatedAmount;
        settlement.ledgerEntryCount = ledgerEntryCount;
        settlement.status = SettlementStatus.READY;
        return settlement;
    }

    public void confirm(User adminUser, LocalDateTime confirmedAt) {
        requireStatus(SettlementStatus.READY);
        if (adminUser == null || confirmedAt == null) {
            throw new IllegalArgumentException("정산 확정 관리자와 시각이 필요합니다.");
        }
        status = SettlementStatus.CONFIRMED;
        this.confirmedAt = confirmedAt;
        this.confirmedByAdminUser = adminUser;
    }

    public void hold(String reason, User adminUser, LocalDateTime heldAt) {
        requireStatus(SettlementStatus.READY);
        if (adminUser == null || heldAt == null) {
            throw new IllegalArgumentException("정산 보류 관리자와 시각이 필요합니다.");
        }
        status = SettlementStatus.ON_HOLD;
        holdReason = requireText(reason, 500, "정산 보류 사유가 필요합니다.");
        this.heldAt = heldAt;
        heldByAdminUser = adminUser;
        holdReleasedAt = null;
        holdReleasedByAdminUser = null;
    }

    public void releaseHold(User adminUser, LocalDateTime releasedAt) {
        requireStatus(SettlementStatus.ON_HOLD);
        if (adminUser == null || releasedAt == null) {
            throw new IllegalArgumentException("정산 보류 해제 관리자와 시각이 필요합니다.");
        }
        status = SettlementStatus.READY;
        holdReleasedAt = releasedAt;
        holdReleasedByAdminUser = adminUser;
    }

    public boolean isConfirmed() {
        return status == SettlementStatus.CONFIRMED;
    }

    private void requireStatus(SettlementStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    status + " 상태에서는 요청한 정산 상태 변경을 수행할 수 없습니다."
            );
        }
    }

    private static long calculateSettlementAmount(
            long productSales,
            long shippingSales,
            long cancellation,
            long returnAmount,
            long commission,
            long adjustment
    ) {
        try {
            long sales = Math.addExact(productSales, shippingSales);
            long afterRefunds = Math.subtractExact(
                    Math.subtractExact(sales, cancellation),
                    returnAmount
            );
            return Math.addExact(
                    Math.subtractExact(afterRefunds, commission),
                    adjustment
            );
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("정산 합계 금액을 안전하게 계산할 수 없습니다.", exception);
        }
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
}
