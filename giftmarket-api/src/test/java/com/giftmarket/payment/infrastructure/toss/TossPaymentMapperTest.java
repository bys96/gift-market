package com.giftmarket.payment.infrastructure.toss;

import com.giftmarket.payment.gateway.GatewayPaymentStatus;
import com.giftmarket.payment.gateway.GatewayCancelCommand;
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

    @Test
    void mapsPartialCancellationByLastTransactionKey() {
        TossPaymentResponse response = new TossPaymentResponse(
                "payment-key", "order-id", "PARTIAL_CANCELED", 10_000L,
                "KRW", "카드", null, "new-transaction", null, 7_000L,
                java.util.List.of(
                        new TossPaymentResponse.TossCancelResponse(1_000L,
                                "2026-08-17T12:00:00+09:00", "old-transaction", "DONE"),
                        new TossPaymentResponse.TossCancelResponse(3_000L,
                                "2026-08-17T13:00:00+09:00", "new-transaction", "DONE")
                ), true
        );
        var result = mapper.toCancelResult(response, GatewayCancelCommand.partial(
                "payment-key", "order-id", 10_000L, "KRW", "사유", "key", 3_000L));
        assertThat(result.status()).isEqualTo(GatewayPaymentStatus.PARTIALLY_CANCELED);
        assertThat(result.providerTransactionId()).isEqualTo("new-transaction");
        assertThat(result.canceledAmount()).isEqualTo(3_000L);
        assertThat(result.remainingAmount()).isEqualTo(7_000L);
    }

    @Test
    void mapsFinalPartialCancellationTransactionRefundableAmount() {
        TossPaymentResponse response = new TossPaymentResponse(
                "payment-key", "order-id", "PARTIAL_CANCELED", 2_000L,
                "KRW", "카드", null, "second-transaction", null, 0L,
                java.util.List.of(new TossPaymentResponse.TossCancelResponse(
                        1_000L, "고객 요청", "2026-08-18T10:00:00+09:00",
                        "second-transaction", "DONE", 0L)), false);

        var result = mapper.toCancelResult(response, GatewayCancelCommand.partial(
                "payment-key", "order-id", 2_000L, "KRW", "고객 요청", "key", 1_000L));

        assertThat(result.status()).isEqualTo(GatewayPaymentStatus.PARTIALLY_CANCELED);
        assertThat(result.remainingAmount()).isZero();
        assertThat(result.transactionRemainingAmount()).isZero();
        assertThat(result.canceledAmount()).isEqualTo(1_000L);
        assertThat(result.cancellationStatus()).isEqualTo("DONE");
        assertThat(result.providerTransactionId()).isEqualTo("second-transaction");
    }

    private GatewayPaymentStatus map(String providerStatus) {
        return ReflectionTestUtils.invokeMethod(
                mapper,
                "mapStatus",
                providerStatus
        );
    }
}
