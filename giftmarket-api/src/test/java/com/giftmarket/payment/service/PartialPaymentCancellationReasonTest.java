package com.giftmarket.payment.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PartialPaymentCancellationReasonTest {

    @Test
    void createsShortDeterministicReasonThatDistinguishesCancellationId() {
        String first = PartialPaymentCancellationReason.create(4L);
        String second = PartialPaymentCancellationReason.create(5L);

        assertThat(first).isEqualTo("구매자 주문 부분취소 요청 #4");
        assertThat(first).hasSizeLessThanOrEqualTo(200);
        assertThat(second).isNotEqualTo(first).endsWith("#5");
    }
}
