package com.giftmarket.global.util;

import com.giftmarket.global.config.MoneyJsonConfiguration;
import com.giftmarket.global.exception.MoneyCalculationException;
import com.giftmarket.payment.dto.request.PaymentConfirmRequest;
import com.giftmarket.product.dto.request.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;

class MoneyLimitsValidationTest {
    @Test
    void validatesNewProductAndShippingAmountsAtTheirExactBoundaries() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (Class<?> type : new Class<?>[] {ProductCreateRequest.class, ProductUpdateRequest.class}) {
                assertThat(validator.validateValue(type, "price", 1L)).isEmpty();
                assertThat(validator.validateValue(type, "price", 0L)).isNotEmpty();
                for (String field : new String[] {"price", "shippingFee", "returnShippingFee", "exchangeShippingFee"}) {
                    assertThat(validator.validateValue(type, field, MoneyLimits.MAX_AMOUNT)).isEmpty();
                    assertThat(validator.validateValue(type, field, MoneyLimits.MAX_AMOUNT + 1L)).isNotEmpty();
                    assertThat(validator.validateValue(type, field, Long.MAX_VALUE)).isNotEmpty();
                    assertThat(validator.validateValue(type, field, -1L)).isNotEmpty();
                }
            }
            for (Class<?> type : new Class<?>[] {ProductVariantRequest.class, ProductRegistrationVariantRequest.class,
                    ProductModificationVariantRequest.class}) {
                assertThat(validator.validateValue(type, "additionalPrice", -MoneyLimits.MAX_AMOUNT)).isEmpty();
                assertThat(validator.validateValue(type, "additionalPrice", MoneyLimits.MAX_AMOUNT)).isEmpty();
                assertThat(validator.validateValue(type, "additionalPrice", MoneyLimits.MAX_AMOUNT + 1L)).isNotEmpty();
                assertThat(validator.validateValue(type, "additionalPrice", Long.MIN_VALUE)).isNotEmpty();
            }
        }
    }

    @Test
    void preservesDiscountOptionsButRejectsNonPositiveOrOverLimitFinalUnitPrices() {
        assertThat(MoneyLimits.unitPrice(10_000L, -9_999L)).isEqualTo(1L);
        assertThat(MoneyLimits.unitPrice(MoneyLimits.MAX_AMOUNT, 0L)).isEqualTo(MoneyLimits.MAX_AMOUNT);
        assertThatThrownBy(() -> MoneyLimits.unitPrice(10_000L, -10_000L)).isInstanceOf(MoneyCalculationException.class);
        assertThatThrownBy(() -> MoneyLimits.unitPrice(MoneyLimits.MAX_AMOUNT, 1L)).isInstanceOf(MoneyCalculationException.class);
        assertThatThrownBy(() -> MoneyLimits.price(MoneyMath.add(MoneyLimits.MAX_AMOUNT, 1L)))
                .isInstanceOf(MoneyCalculationException.class);
    }

    @Test
    void paymentCallbackTransportLimitDoesNotRetroactivelyApplyNewOrderLimit() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validateValue(PaymentConfirmRequest.class, "amount", MoneyLimits.MAX_AMOUNT + 1L)).isEmpty();
            assertThat(validator.validateValue(PaymentConfirmRequest.class, "amount", MoneyLimits.MAX_SAFE_INTEGER)).isEmpty();
            assertThat(validator.validateValue(PaymentConfirmRequest.class, "amount", MoneyLimits.MAX_SAFE_INTEGER + 1L)).isNotEmpty();
        }
    }

    @Test
    void fractionalKrwAndOutOfLongRangeJsonAreRejectedInsteadOfTruncated() {
        var builder = JsonMapper.builder();
        new MoneyJsonConfiguration().strictIntegerRequests().customize(builder);
        var mapper = builder.build();
        assertThat(mapper.readValue("{\"providerPaymentKey\":\"test-key\",\"merchantPaymentId\":\"test-order\",\"amount\":1000}",
                PaymentConfirmRequest.class).amount()).isEqualTo(1_000L);
        assertThatThrownBy(() -> mapper.readValue("{\"providerPaymentKey\":\"test-key\",\"merchantPaymentId\":\"test-order\",\"amount\":1000.5}",
                PaymentConfirmRequest.class)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> mapper.readValue("{\"providerPaymentKey\":\"test-key\",\"merchantPaymentId\":\"test-order\",\"amount\":9223372036854775808}",
                PaymentConfirmRequest.class)).isInstanceOf(RuntimeException.class);
    }
}
