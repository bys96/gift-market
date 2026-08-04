package com.giftmarket.product.entity;

import com.giftmarket.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "categories",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_categories_parent_id_name",
                        columnNames = {"parent_id", "name"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_categories_parent_id",
                        columnList = "parent_id"
                ),
                @Index(
                        name = "idx_categories_active_sort_order",
                        columnList = "active, sort_order"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(
            name = "sort_order",
            nullable = false
    )
    private Integer sortOrder;

    @Column(nullable = false)
    private boolean active;

    @Builder
    private Category(
            Category parent,
            String name,
            Integer sortOrder,
            boolean active
    ) {
        this.parent = parent;
        this.name = name;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    public static Category create(
            Category parent,
            String name,
            Integer sortOrder
    ) {
        return Category.builder()
                .parent(parent)
                .name(name)
                .sortOrder(sortOrder)
                .active(true)
                .build();
    }

    public void update(
            Category parent,
            String name,
            Integer sortOrder
    ) {
        this.parent = parent;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}