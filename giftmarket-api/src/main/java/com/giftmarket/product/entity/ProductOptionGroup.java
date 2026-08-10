package com.giftmarket.product.entity;

import com.giftmarket.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "product_option_groups",
        indexes = {
                @Index(
                        name = "idx_product_option_groups_product_id",
                        columnList = "product_id"
                ),
                @Index(
                        name = "idx_product_option_groups_product_id_sort_order",
                        columnList = "product_id, sort_order"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOptionGroup extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "product_id",
            nullable = false
    )
    private Product product;

    @Column(
            name = "name",
            nullable = false,
            length = 50
    )
    private String name;

    @Column(
            name = "sort_order",
            nullable = false
    )
    private Integer sortOrder;

    @Builder
    private ProductOptionGroup(
            Product product,
            String name,
            Integer sortOrder
    ) {
        this.product = product;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public static ProductOptionGroup create(
            Product product,
            String name,
            Integer sortOrder
    ) {
        return ProductOptionGroup.builder()
                .product(product)
                .name(name)
                .sortOrder(sortOrder)
                .build();
    }

    public void update(
            String name,
            Integer sortOrder
    ) {
        this.name = name;
        this.sortOrder = sortOrder;
    }
}