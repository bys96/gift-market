package com.giftmarket.product.entity;

import com.giftmarket.global.entity.BaseEntity;
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
        name = "product_variant_option_values",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_variant_option_values_variant_id_option_value_id",
                        columnNames = {
                                "variant_id",
                                "option_value_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_variant_option_values_variant_id",
                        columnList = "variant_id"
                ),
                @Index(
                        name = "idx_variant_option_values_option_value_id",
                        columnList = "option_value_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductVariantOptionValue extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "variant_id",
            nullable = false
    )
    private ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "option_value_id",
            nullable = false
    )
    private ProductOptionValue optionValue;

    @Builder
    private ProductVariantOptionValue(
            ProductVariant variant,
            ProductOptionValue optionValue
    ) {
        this.variant = variant;
        this.optionValue = optionValue;
    }

    public static ProductVariantOptionValue create(
            ProductVariant variant,
            ProductOptionValue optionValue
    ) {
        return ProductVariantOptionValue.builder()
                .variant(variant)
                .optionValue(optionValue)
                .build();
    }
}