package com.giftmarket.order.entity;

public enum ReturnReasonType {

    CHANGE_OF_MIND,
    OPTION_MISTAKE,
    DEFECTIVE,
    WRONG_ITEM,
    DAMAGED,
    DESCRIPTION_MISMATCH,
    OTHER;

    public ReturnResponsibility defaultResponsibility() {
        return switch (this) {
            case CHANGE_OF_MIND, OPTION_MISTAKE -> ReturnResponsibility.BUYER;
            case DEFECTIVE, WRONG_ITEM, DAMAGED, DESCRIPTION_MISMATCH -> ReturnResponsibility.SELLER;
            case OTHER -> null;
        };
    }
}
