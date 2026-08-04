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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "product_option_values",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_product_option_values_group_id_value",
                        columnNames = {"option_group_id", "value"}
                ),
                @UniqueConstraint(
                        name = "uk_product_option_values_group_id_sort_order",
                        columnNames = {"option_group_id", "sort_order"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_product_option_values_option_group_id",
                        columnList = "option_group_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOptionValue extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "option_group_id",
            nullable = false
    )
    private ProductOptionGroup optionGroup;

    @Column(
            name = "value",
            nullable = false,
            length = 100
    )
    private String value;

    @Column(
            name = "sort_order",
            nullable = false
    )
    private Integer sortOrder;

    @Builder
    private ProductOptionValue(
            ProductOptionGroup optionGroup,
            String value,
            Integer sortOrder
    ) {
        this.optionGroup = optionGroup;
        this.value = value;
        this.sortOrder = sortOrder;
    }

    public static ProductOptionValue create(
            ProductOptionGroup optionGroup,
            String value,
            Integer sortOrder
    ) {
        return ProductOptionValue.builder()
                .optionGroup(optionGroup)
                .value(value)
                .sortOrder(sortOrder)
                .build();
    }

    public void update(
            String value,
            Integer sortOrder
    ) {
        this.value = value;
        this.sortOrder = sortOrder;
    }
}