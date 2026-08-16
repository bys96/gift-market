package com.giftmarket.payment.infrastructure.toss;

import com.giftmarket.payment.gateway.GatewayPaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class TossPaymentMapperTest {

    private final TossPaymentMapper mapper = new TossPaymentMapper();

    @Test
    void mapsOnlyDefinitiveFullTerminalStatuses() {
        assertThat(map("DONE")).isEqualTo(GatewayPaymentStatus.PAID);
        assertThat(map("ABORTED")).isEqualTo(GatewayPaymentStatus.FAILED);
        assertThat(map("EXPIRED")).isEqualTo(GatewayPaymentStatus.FAILED);
        assertThat(map("CANCELED")).isEqualTo(GatewayPaymentStatus.CANCELED);
        assertThat(map("PARTIAL_CANCELED")).isEqualTo(
                GatewayPaymentStatus.UNKNOWN
        );
        assertThat(map("IN_PROGRESS")).isEqualTo(GatewayPaymentStatus.PENDING);
    }

    private GatewayPaymentStatus map(String providerStatus) {
        return ReflectionTestUtils.invokeMethod(
                mapper,
                "mapStatus",
                providerStatus
        );
    }
}
