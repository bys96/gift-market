package com.giftmarket.product.repository;

import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> notDeleted() {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.isNull(
                        root.get("deletedAt")
                );
    }

    public static Specification<Product> notAdminHidden() {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.isFalse(root.get("adminHidden"));
    }

    public static Specification<Product> statusIn(
            List<ProductStatus> statuses
    ) {
        return (root, query, criteriaBuilder) -> {
            if (statuses == null || statuses.isEmpty()) {
                return criteriaBuilder.conjunction();
            }

            return root.get("status").in(statuses);
        };
    }

    public static Specification<Product> categoryIdIn(
            List<Long> categoryIds
    ) {
        return (root, query, criteriaBuilder) -> {
            if (categoryIds == null || categoryIds.isEmpty()) {
                return criteriaBuilder.conjunction();
            }

            return root
                    .get("category")
                    .get("id")
                    .in(categoryIds);
        };
    }

    public static Specification<Product> nameContains(
            String keyword
    ) {
        return (root, query, criteriaBuilder) -> {
            if (keyword == null || keyword.isBlank()) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.like(
                    criteriaBuilder.lower(
                            root.get("name")
                    ),
                    "%" + keyword.trim().toLowerCase() + "%"
            );
        };
    }
}
