package com.giftmarket.payment.infrastructure.toss;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class TossPaymentErrorClassificationTest {
    @ParameterizedTest
    @CsvSource({
            "400, INVALID_REJECT_CARD, true", "400, NOT_ALLOWED_POINT_USE, true",
            "400, INVALID_CARD_EXPIRATION, true", "400, EXCEED_MAX_CARD_INSTALLMENT_PLAN, true",
            "403, REJECT_CARD_COMPANY, true", "403, REJECT_ACCOUNT_PAYMENT, true",
            "403, REJECT_CARD_PAYMENT, true", "400, ALREADY_PROCESSING_REQUEST, false",
            "400, ALREADY_PROCESSED_PAYMENT, false", "400, PROVIDER_ERROR, false",
            "409, IDEMPOTENT_REQUEST_PROCESSING, false", "500, UNKNOWN_PAYMENT_ERROR, false",
            "403, UNKNOWN_ERROR, false", "400, REJECT_CARD_COMPANY, false"
    })
    void confirmClassificationUsesCodeAndStatus(int status, String code, boolean declined) {
        assertEquals(declined, TossPaymentClient.isConfirmDecline(status, code));
    }

    @ParameterizedTest
    @CsvSource({
            "400, INVALID_REFUND_ACCOUNT_INFO, true", "400, REFUND_REJECTED, true",
            "403, NOT_CANCELABLE_AMOUNT, true", "403, EXCEED_MAX_REFUND_DUE, true",
            "403, NOT_ALLOWED_PARTIAL_REFUND, true", "403, EXCEED_CANCEL_LIMIT, true",
            "403, NOT_CANCELABLE_PAYMENT, false", "400, ALREADY_CANCELED_PAYMENT, false",
            "400, ALREADY_REFUND_PAYMENT, false", "400, PROVIDER_ERROR, false",
            "409, IDEMPOTENT_REQUEST_PROCESSING, false", "500, FAILED_REFUND_PROCESS, false",
            "400, EXCEED_MAX_REFUND_DUE, false", "403, INVALID_REFUND_ACCOUNT_INFO, false",
            "403, UNKNOWN_ERROR, false"
    })
    void cancelClassificationUsesCodeAndStatus(int status, String code, boolean declined) {
        assertEquals(declined, TossPaymentClient.isCancelDecline(status, code));
    }

    @Test
    void confirmingOrAlreadyProcessedAndUnknownResponsesRemainUncertain() {
        assertFalse(TossPaymentClient.isConfirmDecline(400, "ALREADY_PROCESSING_REQUEST"));
        assertFalse(TossPaymentClient.isConfirmDecline(400, "ALREADY_PROCESSED_PAYMENT"));
        assertFalse(TossPaymentClient.isConfirmDecline(400, "PROVIDER_ERROR"));
        assertFalse(TossPaymentClient.isConfirmDecline(400, "UNPARSEABLE"));
        assertFalse(TossPaymentClient.isConfirmDecline(403, "UNKNOWN_ERROR"));
        assertFalse(TossPaymentClient.isConfirmDecline(409, "IDEMPOTENT_REQUEST_PROCESSING"));
        assertFalse(TossPaymentClient.isConfirmDecline(400, "REJECT_CARD_COMPANY"));
        assertTrue(TossPaymentClient.isConfirmDecline(400, "INVALID_STOPPED_CARD"));
        assertTrue(TossPaymentClient.isConfirmDecline(403, "REJECT_CARD_COMPANY"));
        assertTrue(TossPaymentClient.isConfirmDecline(403, "REJECT_ACCOUNT_PAYMENT"));
        assertTrue(TossPaymentClient.isConfirmDecline(403, "REJECT_CARD_PAYMENT"));
    }

    @Test
    void processingAndAlreadyCanceledAreNotDeclines() {
        assertFalse(TossPaymentClient.isCancelDecline(409, "IDEMPOTENT_REQUEST_PROCESSING"));
        assertFalse(TossPaymentClient.isCancelDecline(400, "ALREADY_CANCELED_PAYMENT"));
        assertFalse(TossPaymentClient.isCancelDecline(400, "ALREADY_REFUND_PAYMENT"));
        // Toss FAQ says NOT_CANCELABLE_PAYMENT can also indicate an already canceled payment.
        assertFalse(TossPaymentClient.isCancelDecline(403, "NOT_CANCELABLE_PAYMENT"));
        assertFalse(TossPaymentClient.isCancelDecline(400, "PROVIDER_ERROR"));
        assertTrue(TossPaymentClient.isCancelDecline(400, "INVALID_REFUND_ACCOUNT_INFO"));
    }
}
