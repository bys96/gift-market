package com.giftmarket.product.dto.request;

import com.giftmarket.product.entity.ProductSort;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record ProductSearchCondition(

        List<
                @Positive(message = "카테고리 ID는 1 이상이어야 합니다.")
                        Long
                > categoryIds,

        @Positive(message = "선물 상황 ID는 1 이상이어야 합니다.")
        Long giftOccasionId,

        String keyword,

        @PositiveOrZero(message = "최소 가격은 0 이상이어야 합니다.")
        Long minPrice,

        @PositiveOrZero(message = "최대 가격은 0 이상이어야 합니다.")
        Long maxPrice,

        ProductSort sort,

        Boolean excludeSoldOut,

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
        Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하이어야 합니다.")
        Integer size

) {

    public List<Long> normalizedCategoryIds() {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return List.of();
        }

        return categoryIds.stream()
                .distinct()
                .toList();
    }

    public String normalizedKeyword() {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }

    public Long normalizedGiftOccasionId() {
        return giftOccasionId;
    }

    public Long normalizedMinPrice() {
        return minPrice;
    }

    public Long normalizedMaxPrice() {
        return maxPrice;
    }

    public ProductSort normalizedSort() {
        return sort == null ? ProductSort.LATEST : sort;
    }

    public boolean normalizedExcludeSoldOut() {
        return Boolean.TRUE.equals(excludeSoldOut);
    }

    public int normalizedPage() {
        return page == null
                ? 0
                : page;
    }

    public int normalizedSize() {
        return size == null
                ? 20
                : size;
    }
}
