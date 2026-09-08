package com.giftmarket.seller.repository;

import com.giftmarket.seller.entity.SellerStore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface SellerStoreRepository extends JpaRepository<SellerStore, Long> {
    Optional<SellerStore> findBySellerId(Long sellerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from SellerStore s where s.seller.id = :sellerId")
    Optional<SellerStore> findBySellerIdForUpdate(@org.springframework.data.repository.query.Param("sellerId") Long sellerId);
    boolean existsByStoreNameAndIdNot(String storeName, Long id);
    boolean existsByStoreName(String storeName);
}
