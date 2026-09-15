package com.giftmarket.settlement.repository;

import com.giftmarket.settlement.entity.Settlement;
import com.giftmarket.settlement.entity.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

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
