package com.giftmarket.product.repository;

import com.giftmarket.product.entity.GiftOccasion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GiftOccasionRepository extends JpaRepository<GiftOccasion, Long> {

    List<GiftOccasion> findAllByActiveTrueOrderByDisplayOrderAscIdAsc();

    List<GiftOccasion> findAllByOrderByDisplayOrderAscIdAsc();

    List<GiftOccasion> findAllByIdInAndActiveTrue(List<Long> ids);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
