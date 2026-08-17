package com.giftmarket.payment.infrastructure.toss;

import com.giftmarket.payment.gateway.GatewayPaymentStatus;
import com.giftmarket.payment.infrastructure.toss.dto.TossPaymentResponse;
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
                GatewayPaymentStatus.PARTIALLY_CANCELED
        );
        assertThat(map("IN_PROGRESS")).isEqualTo(GatewayPaymentStatus.PENDING);
    }

    @Test
    void mapsFullCancellationBalanceAndTransaction() {
        TossPaymentResponse response = new TossPaymentResponse(
                "payment-key", "merchant-id", "CANCELED", 25000L,
                "KRW", "카드", null, "last-transaction", null, 0L,
                java.util.List.of(new TossPaymentResponse.TossCancelResponse(
                        25000L, "2026-08-16T16:00:00+09:00", "cancel-transaction", "DONE"
                ))
        );
        var result = mapper.toCancelResult(response);
        assertThat(result.status()).isEqualTo(GatewayPaymentStatus.CANCELED);
        assertThat(result.remainingAmount()).isZero();
        assertThat(result.providerTransactionId()).isEqualTo("cancel-transaction");
    }

    private GatewayPaymentStatus map(String providerStatus) {
        return ReflectionTestUtils.invokeMethod(
                mapper,
                "mapStatus",
                providerStatus
        );
    }
}
