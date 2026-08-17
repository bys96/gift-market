package com.giftmarket.payment.infrastructure.toss.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TossCancelRequest(String cancelReason, Long cancelAmount) {
}
