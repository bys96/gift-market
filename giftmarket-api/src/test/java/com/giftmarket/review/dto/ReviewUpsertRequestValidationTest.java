package com.giftmarket.review.dto;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ReviewUpsertRequestValidationTest {
    private final jakarta.validation.Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    @Test void sixImagesAreRejected() { var request=new ReviewUpsertRequest(1L,5,"후기",List.of("1","2","3","4","5","6")); assertThat(validator.validate(request)).isNotEmpty(); }
    @Test void duplicateKeysAreLeftForOwnershipServiceValidation() { var request=new ReviewUpsertRequest(1L,5,"후기",List.of("same","same")); assertThat(validator.validate(request)).isEmpty(); }
    @Test void decimalRatingCannotBeRepresentedByContract() { var request=new ReviewUpsertRequest(1L,4,"후기",List.of()); assertThat(request.rating()).isInstanceOf(Integer.class); }
}
