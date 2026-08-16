package com.giftmarket.product.service;

import com.giftmarket.product.exception.ProductException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductVariantServiceTest {

    @Test
    void allowsNegativeAdditionalPriceWhenFinalPriceIsPositive() {
        assertThatCode(() ->
                ProductVariantService.validateFinalVariantPrice(
                        10_000L,
                        -1_000L
                )
        ).doesNotThrowAnyException();
    }

    @Test
    void allowsFinalPriceOfOneWon() {
        assertThatCode(() ->
                ProductVariantService.validateFinalVariantPrice(
                        10_000L,
                        -9_999L
                )
        ).doesNotThrowAnyException();
    }

    @Test
    void rejectsFinalPriceOfZeroWon() {
        assertThatThrownBy(() ->
                ProductVariantService.validateFinalVariantPrice(
                        10_000L,
                        -10_000L
                )
        )
                .isInstanceOf(ProductException.class)
                .hasMessage("옵션 적용 후 최종 판매가격은 1원 이상이어야 합니다.");
    }

    @Test
    void rejectsOverflowedFinalPrice() {
        assertThatThrownBy(() ->
                ProductVariantService.validateFinalVariantPrice(
                        Long.MAX_VALUE,
                        1L
                )
        )
                .isInstanceOf(ProductException.class)
                .hasMessage("옵션 적용 후 최종 판매가격을 확인해주세요.");
    }
}
