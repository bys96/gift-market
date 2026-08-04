package com.giftmarket.product.repository;

import java.util.Optional;

import com.giftmarket.product.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByIdAndActiveTrue(Long id);

    List<Category> findAllByParentIsNullAndActiveTrueOrderBySortOrderAsc();

    List<Category> findAllByParentIdAndActiveTrueOrderBySortOrderAsc(
            Long parentId
    );
}