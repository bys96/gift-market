package com.giftmarket.payment.infrastructure.toss;

import com.giftmarket.payment.entity.EasyPayProvider;
import com.giftmarket.payment.entity.PaymentMethod;
import com.giftmarket.payment.gateway.GatewayConfirmResult;
import com.giftmarket.payment.gateway.GatewayCancelResult;
import com.giftmarket.payment.gateway.GatewayPaymentQueryResult;
import com.giftmarket.payment.gateway.GatewayPaymentStatus;
import com.giftmarket.payment.infrastructure.toss.dto.TossPaymentResponse;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;

@Component
public class TossPaymentMapper {

    private static final ZoneId SEOUL_ZONE =
            ZoneId.of("Asia/Seoul");

    public GatewayConfirmResult toConfirmResult(
            TossPaymentResponse response
    ) {
        return new GatewayConfirmResult(
                mapStatus(response.status()),
                response.paymentKey(),
                response.lastTransactionKey(),
                response.orderId(),
                response.totalAmount(),
                response.currency(),
                mapMethod(response.method()),
                mapEasyPayProvider(response.easyPay()),
                response.status(),
                parseApprovedAt(response.approvedAt())
        );
    }

    public GatewayCancelResult toCancelResult(TossPaymentResponse response) {
        TossPaymentResponse.TossCancelResponse cancellation = latestCancellation(response);
        return new GatewayCancelResult(
                mapStatus(response.status()), response.paymentKey(),
                cancellation == null ? response.lastTransactionKey() : cancellation.transactionKey(),
                response.orderId(), response.totalAmount(), response.balanceAmount(), response.currency(),
                response.status(), parseCanceledAt(response)
        );
    }

    private TossPaymentResponse.TossCancelResponse latestCancellation(TossPaymentResponse response) {
        if (response.cancels() == null || response.cancels().isEmpty()) return null;
        return response.cancels().get(response.cancels().size() - 1);
    }

    private LocalDateTime parseCanceledAt(TossPaymentResponse response) {
        TossPaymentResponse.TossCancelResponse value = latestCancellation(response);
        return value == null || value.canceledAt() == null ? null : parseDateTime(value.canceledAt());
    }

    public GatewayPaymentQueryResult toQueryResult(
            TossPaymentResponse response
    ) {
        return new GatewayPaymentQueryResult(
                mapStatus(response.status()),
                response.paymentKey(),
                response.lastTransactionKey(),
                response.orderId(),
                response.totalAmount(),
                response.currency(),
                mapMethod(response.method()),
                mapEasyPayProvider(response.easyPay()),
                response.status(),
                parseApprovedAt(response.approvedAt()),
                response.balanceAmount(),
                parseCanceledAt(response)
        );
    }

    private GatewayPaymentStatus mapStatus(String status) {
        if (status == null) {
            return GatewayPaymentStatus.UNKNOWN;
        }

        return switch (status.toUpperCase(Locale.ROOT)) {
            case "DONE" -> GatewayPaymentStatus.PAID;
            case "ABORTED", "EXPIRED" ->
                    GatewayPaymentStatus.FAILED;
            case "CANCELED" -> GatewayPaymentStatus.CANCELED;
            // 부분 취소는 전체 실패/재고 전체 복원으로 처리할 수 없습니다.
            case "PARTIAL_CANCELED" -> GatewayPaymentStatus.PARTIALLY_CANCELED;
            case "READY", "IN_PROGRESS", "WAITING_FOR_DEPOSIT" ->
                    GatewayPaymentStatus.PENDING;
            default -> GatewayPaymentStatus.UNKNOWN;
        };
    }

    private PaymentMethod mapMethod(String method) {
        if (method == null) {
            return PaymentMethod.OTHER;
        }

        String normalized = method
                .replace("_", "")
                .replace(" ", "")
                .toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case "카드", "CARD" -> PaymentMethod.CARD;
            case "간편결제", "EASYPAY" -> PaymentMethod.EASY_PAY;
            case "계좌이체", "TRANSFER" -> PaymentMethod.TRANSFER;
            case "가상계좌", "VIRTUALACCOUNT" ->
                    PaymentMethod.VIRTUAL_ACCOUNT;
            case "휴대폰", "MOBILE" -> PaymentMethod.MOBILE;
            default -> PaymentMethod.OTHER;
        };
    }

    private EasyPayProvider mapEasyPayProvider(
            TossPaymentResponse.TossEasyPayResponse easyPay
    ) {
        if (easyPay == null || easyPay.provider() == null) {
            return null;
        }

        String normalized = easyPay.provider()
                .replace("_", "")
                .replace(" ", "")
                .toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case "네이버페이", "NAVERPAY" -> EasyPayProvider.NAVERPAY;
            case "카카오페이", "KAKAOPAY" -> EasyPayProvider.KAKAOPAY;
            case "토스페이", "TOSSPAY" -> EasyPayProvider.TOSSPAY;
            case "페이코", "PAYCO" -> EasyPayProvider.PAYCO;
            case "삼성페이", "SAMSUNGPAY" -> EasyPayProvider.SAMSUNGPAY;
            case "애플페이", "APPLEPAY" -> EasyPayProvider.APPLEPAY;
            default -> EasyPayProvider.OTHER;
        };
    }

    private LocalDateTime parseApprovedAt(String approvedAt) {
        if (approvedAt == null) {
            return null;
        }

        return parseDateTime(approvedAt);
    }

    private LocalDateTime parseDateTime(String value) {
        return OffsetDateTime.parse(value)
                .atZoneSameInstant(SEOUL_ZONE)
                .toLocalDateTime();
    }
}
