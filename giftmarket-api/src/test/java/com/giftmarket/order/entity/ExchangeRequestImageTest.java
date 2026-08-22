package com.giftmarket.order.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ExchangeRequestImageTest {
    @Test
    void createsImageWithTrimmedKeyAndSortOrder() {
        ExchangeRequest request = mock(ExchangeRequest.class);
        ExchangeRequestImage image = ExchangeRequestImage.create(request, " exchanges/1/image.jpg ", 4);
        assertThat(image.getExchangeRequest()).isSameAs(request);
        assertThat(image.getObjectKey()).isEqualTo("exchanges/1/image.jpg");
        assertThat(image.getSortOrder()).isEqualTo(4);
    }

    @Test
    void rejectsBlankKeyAndOutOfRangeSortOrder() {
        ExchangeRequest request = mock(ExchangeRequest.class);
        assertThatThrownBy(() -> ExchangeRequestImage.create(request, " ", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExchangeRequestImage.create(request, "exchanges/1/a.jpg", 5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
