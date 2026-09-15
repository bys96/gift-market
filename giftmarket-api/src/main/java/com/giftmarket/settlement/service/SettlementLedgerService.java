package com.giftmarket.settlement.service;

import com.giftmarket.order.entity.SellerOrder;
import com.giftmarket.order.repository.SellerOrderRepository;
import com.giftmarket.settlement.entity.SettlementLedgerEntry;
import com.giftmarket.settlement.entity.SettlementLedgerSourceType;
import com.giftmarket.settlement.entity.SettlementLedgerType;
import com.giftmarket.settlement.exception.SettlementException;
import com.giftmarket.settlement.repository.SettlementLedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SettlementLedgerService {

    private final SellerOrderRepository sellerOrderRepository;
    private final SettlementLedgerEntryRepository ledgerRepository;

    @Transactional
    public SettlementLedgerEntry recordProductSale(SettlementLedgerCommand command) {
        requireCommand(command);
        return record(
                SettlementLedgerType.SALE_PRODUCT,
                SettlementLedgerSourceType.SELLER_ORDER,
                command.amount(),
                command
        );
    }

    @Transactional
    public SettlementLedgerEntry recordShippingSale(SettlementLedgerCommand command) {
        requireCommand(command);
        return record(
                SettlementLedgerType.SALE_SHIPPING,
                SettlementLedgerSourceType.SELLER_ORDER,
                command.amount(),
                command
        );
    }

    @Transactional
    public SettlementLedgerEntry recordCommission(SettlementLedgerCommand command) {
        requireCommand(command);
        return record(
                SettlementLedgerType.COMMISSION,
                SettlementLedgerSourceType.SELLER_ORDER,
                negate(command.amount()),
                command
        );
    }

    @Transactional
    public SettlementLedgerEntry recordCancellationRefund(SettlementLedgerCommand command) {
        requireCommand(command);
        return record(
                SettlementLedgerType.CANCELLATION_REFUND,
                SettlementLedgerSourceType.PAYMENT_CANCELLATION,
                negate(command.amount()),
                command
        );
    }

    @Transactional
    public SettlementLedgerEntry recordReturnRefund(SettlementLedgerCommand command) {
        requireCommand(command);
        return record(
                SettlementLedgerType.RETURN_REFUND,
                SettlementLedgerSourceType.PAYMENT_CANCELLATION,
                negate(command.amount()),
                command
        );
    }

    @Transactional
    public SettlementLedgerEntry recordCommissionReversal(SettlementLedgerCommand command) {
        requireCommand(command);
        return record(
                SettlementLedgerType.COMMISSION_REVERSAL,
                SettlementLedgerSourceType.PAYMENT_CANCELLATION,
                command.amount(),
                command
        );
    }

    @Transactional
    public SettlementLedgerEntry recordZeroRefundReturnCommissionReversal(
            SettlementLedgerCommand command
    ) {
        requireCommand(command);
        return record(
                SettlementLedgerType.COMMISSION_REVERSAL,
                SettlementLedgerSourceType.RETURN_REQUEST,
                command.amount(),
                command
        );
    }

    private SettlementLedgerEntry record(
            SettlementLedgerType type,
            SettlementLedgerSourceType sourceType,
            long signedAmount,
            SettlementLedgerCommand command
    ) {
        SellerOrder sellerOrder = sellerOrderRepository.findByIdAndSellerIdForUpdate(
                        command.sellerOrderId(),
                        command.sellerId()
                )
                .orElseThrow(() -> new SettlementException(
                        "판매자에게 속한 판매자 주문을 찾을 수 없습니다."
                ));
        if (sellerOrder.getSeller() == null
                || !Objects.equals(sellerOrder.getSeller().getId(), command.sellerId())) {
            throw new SettlementException("판매자와 판매자 주문의 소유자가 일치하지 않습니다.");
        }
        if (sourceType == SettlementLedgerSourceType.SELLER_ORDER
                && !Objects.equals(command.sourceId(), command.sellerOrderId())) {
            throw new SettlementException("판매 매출 원장의 근거가 판매자 주문과 일치하지 않습니다.");
        }

        SettlementLedgerEntry existing = ledgerRepository
                .findBySourceTypeAndSourceIdAndSourceDetailKey(
                        sourceType,
                        command.sourceId(),
                        command.sourceDetailKey()
                )
                .orElse(null);
        if (existing != null) {
            validateSamePayload(existing, sellerOrder, type, sourceType, signedAmount, command);
            return existing;
        }

        return ledgerRepository.save(SettlementLedgerEntry.create(
                sellerOrder.getSeller(),
                sellerOrder,
                type,
                signedAmount,
                sourceType,
                command.sourceId(),
                command.sourceDetailKey(),
                command.occurredAt(),
                command.eligibleAt(),
                command.commissionRateBps(),
                command.commissionBaseAmount(),
                null,
                null,
                null
        ));
    }

    private void validateSamePayload(
            SettlementLedgerEntry existing,
            SellerOrder sellerOrder,
            SettlementLedgerType type,
            SettlementLedgerSourceType sourceType,
            long signedAmount,
            SettlementLedgerCommand command
    ) {
        boolean same = existing.getSeller() != null
                && Objects.equals(existing.getSeller().getId(), command.sellerId())
                && existing.getSellerOrder() != null
                && Objects.equals(existing.getSellerOrder().getId(), sellerOrder.getId())
                && existing.getType() == type
                && Objects.equals(existing.getAmount(), signedAmount)
                && SettlementLedgerEntry.CURRENCY_KRW.equals(existing.getCurrency())
                && existing.getSourceType() == sourceType
                && Objects.equals(existing.getSourceId(), command.sourceId())
                && Objects.equals(existing.getSourceDetailKey(), command.sourceDetailKey())
                && Objects.equals(existing.getOccurredAt(), command.occurredAt())
                && Objects.equals(existing.getEligibleAt(), command.eligibleAt())
                && Objects.equals(existing.getCommissionRateBps(), command.commissionRateBps())
                && Objects.equals(existing.getCommissionBaseAmount(), command.commissionBaseAmount())
                && existing.getReason() == null
                && existing.getAdminUser() == null
                && existing.getReversalOfEntry() == null;
        if (!same) {
            throw new SettlementException(
                    "동일한 정산 원장 근거 키에 서로 다른 경제 payload가 요청되었습니다."
            );
        }
    }

    private long negate(long amount) {
        try {
            return Math.negateExact(amount);
        } catch (ArithmeticException exception) {
            throw new SettlementException("정산 원장 금액의 부호를 안전하게 변환할 수 없습니다.");
        }
    }

    private void requireCommand(SettlementLedgerCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("정산 원장 생성 명령이 필요합니다.");
        }
    }
}
