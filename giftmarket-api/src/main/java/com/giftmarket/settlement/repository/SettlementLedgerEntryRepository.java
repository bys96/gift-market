package com.giftmarket.settlement.repository;

import com.giftmarket.settlement.entity.SettlementLedgerEntry;
import com.giftmarket.settlement.entity.SettlementLedgerSourceType;
import com.giftmarket.settlement.entity.SettlementLedgerType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SettlementLedgerEntryRepository
        extends JpaRepository<SettlementLedgerEntry, Long> {

    Optional<SettlementLedgerEntry> findBySourceTypeAndSourceIdAndSourceDetailKey(
            SettlementLedgerSourceType sourceType,
            Long sourceId,
            String sourceDetailKey
    );

    List<SettlementLedgerEntry> findAllBySellerOrderIdOrderByOccurredAtAscIdAsc(
            Long sellerOrderId
    );

    boolean existsBySellerOrderIdAndSourceTypeAndSourceId(
            Long sellerOrderId,
            SettlementLedgerSourceType sourceType,
            Long sourceId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e
            from SettlementLedgerEntry e
            where e.sellerOrder.id = :sellerOrderId
              and e.sourceType = :sourceType
              and e.sourceId = :sourceId
              and e.type in :types
            order by e.id asc
            """)
    List<SettlementLedgerEntry> findInitialSalesForUpdate(
            @Param("sellerOrderId") Long sellerOrderId,
            @Param("sourceType") SettlementLedgerSourceType sourceType,
            @Param("sourceId") Long sourceId,
            @Param("types") Collection<SettlementLedgerType> types
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e
            from SettlementLedgerEntry e
            where e.sellerOrder.id = :sellerOrderId
              and e.type in :types
            order by e.occurredAt asc, e.id asc
            """)
    List<SettlementLedgerEntry> findEligibilityEntriesForUpdate(
            @Param("sellerOrderId") Long sellerOrderId,
            @Param("types") Collection<SettlementLedgerType> types
    );

    @Query("""
            select coalesce(sum(e.amount), 0)
            from SettlementLedgerEntry e
            where e.sellerOrder.id = :sellerOrderId
              and e.type = :type
            """)
    Long sumAmountBySellerOrderIdAndType(
            @Param("sellerOrderId") Long sellerOrderId,
            @Param("type") SettlementLedgerType type
    );

    @Query("""
            select coalesce(sum(e.amount), 0)
            from SettlementLedgerEntry e
            where e.sellerOrder.id = :sellerOrderId
            """)
    Long sumAmountBySellerOrderId(@Param("sellerOrderId") Long sellerOrderId);

    @Query("""
            select e.id
            from SettlementLedgerEntry e
            where e.seller.id = :sellerId
              and e.settlement is null
              and e.eligibleAt is not null
              and e.eligibleAt <= :eligibleAt
            order by e.eligibleAt asc, e.id asc
            """)
    List<Long> findUnassignedEligibleIds(
            @Param("sellerId") Long sellerId,
            @Param("eligibleAt") LocalDateTime eligibleAt,
            Pageable pageable
    );

    List<SettlementLedgerEntry> findAllBySettlementIdAndSellerOrderIdOrderByOccurredAtAscIdAsc(
            Long settlementId,
            Long sellerOrderId
    );
}
