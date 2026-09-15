package com.giftmarket.settlement.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementPropertiesTest {

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void acceptsDefaultConfiguration() {
        SettlementProperties properties = new SettlementProperties();

        assertThat(properties.getHoldDays()).isEqualTo(7);
        assertThat(properties.getCommissionRateBps()).isEqualTo(1_000);
        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void rejectsConfigurationOutsideAllowedRanges() {
        SettlementProperties properties = new SettlementProperties();
        properties.setHoldDays(91);
        properties.setCommissionRateBps(10_001);

        assertThat(validator.validate(properties)).hasSize(2);
    }
}
