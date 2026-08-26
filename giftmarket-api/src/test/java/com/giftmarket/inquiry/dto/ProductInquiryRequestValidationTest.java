package com.giftmarket.inquiry.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductInquiryRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test void rejectsBlankTitleAndContent() {
        assertThat(validator.validate(new ProductInquiryRequest(" ", " ", false)))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("title", "content");
    }

    @Test void rejectsQuestionOverMaximumLength() {
        assertThat(validator.validate(new ProductInquiryRequest("a".repeat(101), "b".repeat(2001), false))).hasSize(2);
    }

    @Test void rejectsBlankOrLongAnswer() {
        assertThat(validator.validate(new ProductInquiryAnswerRequest(" "))).hasSize(1);
        assertThat(validator.validate(new ProductInquiryAnswerRequest("a".repeat(2001)))).hasSize(1);
    }
}
