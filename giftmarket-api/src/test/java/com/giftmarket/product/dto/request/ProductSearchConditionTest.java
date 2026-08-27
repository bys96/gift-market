package com.giftmarket.product.dto.request;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSearchConditionTest {

    @Test
    void defaultsPageSizeToTwentyAndAcceptsMaximumOneHundred() {
        var defaultCondition = new ProductSearchCondition(
                List.of(), null, false, null, null);
        var maximumCondition = new ProductSearchCondition(
                List.of(), null, false, 0, 100);

        assertThat(defaultCondition.normalizedSize()).isEqualTo(20);
        assertThat(Validation.buildDefaultValidatorFactory()
                .getValidator()
                .validate(maximumCondition))
                .isEmpty();
    }

    @Test
    void rejectsPageSizeOverOneHundred() {
        var condition = new ProductSearchCondition(
                List.of(), null, false, 0, 101);

        assertThat(Validation.buildDefaultValidatorFactory()
                .getValidator()
                .validate(condition))
                .anyMatch(violation ->
                        violation.getPropertyPath().toString().equals("size")
                                && violation.getMessage().equals(
                                "페이지 크기는 100 이하이어야 합니다."
                        )
                );
    }
}
