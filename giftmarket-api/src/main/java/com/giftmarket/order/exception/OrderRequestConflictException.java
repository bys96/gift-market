package com.giftmarket.order.exception;

public class OrderRequestConflictException extends RuntimeException {
    public OrderRequestConflictException() {
        super("같은 주문 요청 키에 다른 구매 항목이 사용되었습니다. 주문을 다시 요청해주세요.");
    }
}
