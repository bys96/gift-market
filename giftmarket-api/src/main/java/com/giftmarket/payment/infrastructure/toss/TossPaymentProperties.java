package com.giftmarket.payment.infrastructure.toss;

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
@ConfigurationProperties(prefix = "payment.toss")
public class TossPaymentProperties {

    private String secretKey;

    private String baseUrl = "https://api.tosspayments.com";

    @Positive
    private int connectTimeoutMillis = 3_000;

    @Positive
    private int readTimeoutMillis = 10_000;
}
