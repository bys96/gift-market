package com.giftmarket.product.dto.response;

import com.giftmarket.product.entity.Category;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminCategoryResponse {

    private Long id;

    private Long parentId;

    private String parentName;

    private String name;

    private Integer sortOrder;

    private boolean active;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static AdminCategoryResponse from(Category category) {
        Category parent = category.getParent();

        return AdminCategoryResponse.builder()
                .id(category.getId())
                .parentId(
                        parent == null
                                ? null
                                : parent.getId()
                )
                .parentName(
                        parent == null
                                ? null
                                : parent.getName()
                )
                .name(category.getName())
                .sortOrder(category.getSortOrder())
                .active(category.isActive())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}