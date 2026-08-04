package com.giftmarket.product.dto.response;

import com.giftmarket.product.entity.Category;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CategoryResponse {

    private Long id;

    private String name;

    private List<CategoryResponse> children;

    public static CategoryResponse from(
            Category category,
            List<CategoryResponse> children
    ) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .children(children)
                .build();
    }
}