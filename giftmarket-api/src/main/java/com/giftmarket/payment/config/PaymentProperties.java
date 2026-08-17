package com.giftmarket.payment.config;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {

    @Positive
    private long reservationMinutes = 30;

    @Positive
    private long expirationCheckIntervalMillis = 60_000;

    @Positive
    private int expirationBatchSize = 100;

    @Positive
    private long reconciliationDelaySeconds = 30;

    @Positive
    private long reconciliationCheckIntervalMillis = 60_000;

    @Positive
    private int reconciliationBatchSize = 100;

    @Positive
    private long cancelReconciliationDelaySeconds = 30;

    @Positive
    private long cancelReconciliationCheckIntervalMillis = 60_000;

    @Positive
    private int cancelReconciliationBatchSize = 100;

    @Positive
    private long partialCancellationReconciliationDelaySeconds = 30;

    @Positive
    private long partialCancellationReconciliationCheckIntervalMillis = 60_000;

    @Positive
    private int partialCancellationReconciliationBatchSize = 100;
}
