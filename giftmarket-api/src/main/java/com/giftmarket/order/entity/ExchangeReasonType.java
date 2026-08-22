package com.giftmarket.order.entity;

public enum ExchangeReasonType {

    CHANGE_OF_MIND,
    OPTION_MISTAKE,
    DEFECTIVE,
    WRONG_ITEM,
    DAMAGED,
    DESCRIPTION_MISMATCH,
    OTHER;

    public ExchangeResponsibility defaultResponsibility() {
        return switch (this) {
            case CHANGE_OF_MIND, OPTION_MISTAKE -> ExchangeResponsibility.BUYER;
            case DEFECTIVE, WRONG_ITEM, DAMAGED, DESCRIPTION_MISMATCH -> ExchangeResponsibility.SELLER;
            case OTHER -> null;
        };
    }
}
