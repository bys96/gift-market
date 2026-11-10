package com.giftmarket.global.util;

import com.giftmarket.global.exception.MoneyCalculationException;

/** Exact KRW arithmetic; no new-sale limit is applied to historical refund/settlement amounts. */
public final class MoneyMath {
    private MoneyMath() { }

    public static long add(long left, long right) {
        try { return Math.addExact(left, right); }
        catch (ArithmeticException exception) { throw invalid(); }
    }

    public static long multiply(long amount, long quantity) {
        try { return Math.multiplyExact(amount, quantity); }
        catch (ArithmeticException exception) { throw invalid(); }
    }

    public static long nonNegative(Long amount) {
        if (amount == null || amount < 0L) throw invalid();
        return amount;
    }

    public static long unitPrice(Long productPrice, Long additionalPrice) {
        if (productPrice == null || productPrice <= 0L || additionalPrice == null) throw invalid();
        long price = add(productPrice, additionalPrice);
        if (price <= 0L) throw invalid();
        return price;
    }

    public static long lineTotal(long unitPrice, Integer quantity) {
        if (unitPrice <= 0L || quantity == null || quantity <= 0) throw invalid();
        return multiply(unitPrice, quantity.longValue());
    }

    private static MoneyCalculationException invalid() {
        return new MoneyCalculationException("금액을 안전하게 계산할 수 없습니다. 가격·배송비·수량을 확인해주세요.");
    }
}
