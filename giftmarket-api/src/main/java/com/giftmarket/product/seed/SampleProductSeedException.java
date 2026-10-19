package com.giftmarket.product.seed;

public class SampleProductSeedException extends RuntimeException {

    public SampleProductSeedException(String message) {
        super(message);
    }

    public SampleProductSeedException(String message, Throwable cause) {
        super(message, cause);
    }
}
