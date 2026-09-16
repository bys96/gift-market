package com.giftmarket.settlement.repository;

import com.giftmarket.settlement.entity.Settlement;
import com.giftmarket.settlement.entity.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.time.LocalDateTime;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    @Query(value = """
            select s from Settlement s join fetch s.seller seller
            where (:sellerId is null or seller.id = :sellerId)
              and (:status is null or s.status = :status)
              and (:periodStart is null or s.periodStart >= :periodStart)
              and (:periodEnd is null or s.periodEnd <= :periodEnd)
            order by s.periodEnd desc, s.id desc
            """, countQuery = """
            select count(s.id) from Settlement s
            where (:sellerId is null or s.seller.id = :sellerId)
              and (:status is null or s.status = :status)
              and (:periodStart is null or s.periodStart >= :periodStart)
              and (:periodEnd is null or s.periodEnd <= :periodEnd)
            """)
    Page<Settlement> findAdminSettlements(
            @Param("sellerId") Long sellerId,
            @Param("status") SettlementStatus status,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "seller")
    Optional<Settlement> findAdminById(Long id);

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
