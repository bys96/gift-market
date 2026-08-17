package com.giftmarket.payment.infrastructure.toss.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TossCancelRequestTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fullCancellationOmitsCancelAmount() throws Exception {
        String json = objectMapper.writeValueAsString(new TossCancelRequest("고객 요청", null));
        assertThat(json).contains("cancelReason").doesNotContain("cancelAmount");
    }

    @Test
    void partialCancellationIncludesCancelAmount() throws Exception {
        String json = objectMapper.writeValueAsString(new TossCancelRequest("고객 요청", 3_000L));
        assertThat(json).contains("\"cancelAmount\":3000");
    }
}
