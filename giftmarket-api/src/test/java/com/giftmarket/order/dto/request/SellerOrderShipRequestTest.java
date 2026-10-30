package com.giftmarket.order.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SellerOrderShipRequestTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsTrimmedAlphanumericAndHyphenTrackingNumbers() {
        var request = new SellerOrderShipRequest("택배사", "  AB-123  ");
        assertThat(request.trackingNumber()).isEqualTo("AB-123");
        assertThat(validator.validate(request)).isEmpty();
        assertThat(validator.validate(new SellerOrderShipRequest("택배사", "9".repeat(100)))).isEmpty();
    }

    @Test
    void rejectsBlankInvalidAndOverlongTrackingNumbers() {
        for (String tracking : new String[] {"", "   ", "한글123", "AB 123", "AB_123", "-123", "123-", "1".repeat(101)}) {
            assertThat(validator.validate(new SellerOrderShipRequest("택배사", tracking))).isNotEmpty();
        }
    }
}
