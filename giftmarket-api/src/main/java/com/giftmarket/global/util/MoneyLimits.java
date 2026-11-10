package com.giftmarket.global.util;

import com.giftmarket.global.exception.MoneyCalculationException;

/** Gift Market's new-sale policy, not a universal payment-provider limit. */
public final class MoneyLimits {
    public static final long MAX_AMOUNT = 10_000_000L;
    public static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

    private MoneyLimits() { }

    public static long amount(Long value) {
        long amount = MoneyMath.nonNegative(value);
        if (amount > MAX_AMOUNT) throw new MoneyCalculationException("신규 거래 금액은 최대 1,000만원까지 가능합니다.");
        return amount;
    }

    public static long price(Long value) {
        long price = amount(value);
        if (price == 0L) throw new MoneyCalculationException("판매가격은 1원 이상이어야 합니다.");
        return price;
    }

    public static long unitPrice(Long price, Long additional) {
        price(price);
        if (additional == null || additional < -MAX_AMOUNT || additional > MAX_AMOUNT) {
            throw new MoneyCalculationException("옵션 추가금은 -1,000만원부터 1,000만원까지 입력해주세요.");
        }
        return price(MoneyMath.unitPrice(price, additional));
    }
}
