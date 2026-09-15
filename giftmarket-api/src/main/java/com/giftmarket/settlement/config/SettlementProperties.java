package com.giftmarket.settlement.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "settlement")
public class SettlementProperties {

    @Min(0)
    @Max(90)
    private int holdDays = 7;

    @Min(0)
    @Max(10_000)
    private int commissionRateBps = 1_000;
}
