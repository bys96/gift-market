package com.giftmarket.payment.exception;

import lombok.Getter;

@Getter
public class PartialCancellationValidationException extends PaymentException {

    private final String validationType;

    public PartialCancellationValidationException(String validationType) {
        super("부분환불 결과가 결제 정보와 일치하지 않습니다.");
        this.validationType = validationType;
    }
}
