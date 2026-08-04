package com.giftmarket.product.service;

import com.giftmarket.product.dto.response.CategoryResponse;
import com.giftmarket.product.entity.Category;
import com.giftmarket.product.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> getActiveCategories() {
        return categoryRepository
                .findAllByParentIsNullAndActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(this::createCategoryResponse)
                .toList();
    }

    private CategoryResponse createCategoryResponse(Category category) {
        List<CategoryResponse> children = categoryRepository
                .findAllByParentIdAndActiveTrueOrderBySortOrderAsc(
                        category.getId()
                )
                .stream()
                .map(this::createCategoryResponse)
                .toList();

        return CategoryResponse.from(
                category,
                children
        );
    }
}