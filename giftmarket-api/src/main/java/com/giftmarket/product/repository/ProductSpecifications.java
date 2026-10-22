package com.giftmarket.product.repository;

import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductGiftOccasion;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.seller.entity.SellerStatus;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
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

    public static Specification<Product> activeSeller() {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        root.get("seller").get("status"),
                        SellerStatus.ACTIVE
                );
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

    public static Specification<Product> giftOccasionId(
            Long giftOccasionId
    ) {
        return (root, query, criteriaBuilder) -> {
            if (giftOccasionId == null) {
                return criteriaBuilder.conjunction();
            }

            Subquery<Long> productIdSubquery = query.subquery(Long.class);
            Root<ProductGiftOccasion> relation = productIdSubquery.from(
                    ProductGiftOccasion.class
            );

            productIdSubquery.select(
                    relation.get("product").get("id")
            ).where(
                    criteriaBuilder.equal(
                            relation.get("product").get("id"),
                            root.get("id")
                    ),
                    criteriaBuilder.equal(
                            relation.get("giftOccasion").get("id"),
                            giftOccasionId
                    ),
                    criteriaBuilder.isTrue(
                            relation.get("giftOccasion").get("active")
                    )
            );

            return criteriaBuilder.exists(productIdSubquery);
        };
    }

    public static Specification<Product> priceAtLeast(Long minPrice) {
        return (root, query, criteriaBuilder) -> minPrice == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.greaterThanOrEqualTo(
                        root.get("price"),
                        minPrice
                );
    }

    public static Specification<Product> priceAtMost(Long maxPrice) {
        return (root, query, criteriaBuilder) -> maxPrice == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.lessThanOrEqualTo(
                        root.get("price"),
                        maxPrice
                );
    }
}
