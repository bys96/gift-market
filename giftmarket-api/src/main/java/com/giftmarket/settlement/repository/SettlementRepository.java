package com.giftmarket.settlement.repository;

import com.giftmarket.settlement.entity.Settlement;
import com.giftmarket.settlement.entity.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    boolean existsBySellerIdAndPeriodStartAndPeriodEnd(
            Long sellerId,
            java.time.LocalDateTime periodStart,
            java.time.LocalDateTime periodEnd
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Settlement s where s.id = :settlementId")
    Optional<Settlement> findByIdForUpdate(@Param("settlementId") Long settlementId);

    Optional<Settlement> findBySettlementNumber(String settlementNumber);

    Optional<Settlement> findByIdAndSellerId(Long settlementId, Long sellerId);

    Page<Settlement> findAllBySellerIdOrderByPeriodEndDescIdDesc(
            Long sellerId,
            Pageable pageable
    );

    Page<Settlement> findAllBySellerIdAndStatusOrderByPeriodEndDescIdDesc(
            Long sellerId,
            SettlementStatus status,
            Pageable pageable
    );

    Page<Settlement> findAllByStatusOrderByPeriodEndDescIdDesc(
            SettlementStatus status,
            Pageable pageable
    );
}
