package com.giftmarket.order.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReturnRequestTest {

    @Test
    void buyerReasonAutomaticallySetsBuyerResponsibility() {
        Order order = mock(Order.class);
        SellerOrder sellerOrder = mock(SellerOrder.class);
        when(sellerOrder.getOrder()).thenReturn(order);

        ReturnRequest returnRequest = ReturnRequest.createRequested(
                order,
                sellerOrder,
                "return-request-key",
                ReturnReasonType.CHANGE_OF_MIND,
                "단순 변심입니다.",
                "홍길동",
                "010-1234-5678",
                "12345",
                "서울특별시 강남구 테스트로 1",
                "101호",
                LocalDateTime.now()
        );

        assertThat(returnRequest.getStatus())
                .isEqualTo(ReturnRequestStatus.REQUESTED);
        assertThat(returnRequest.getResponsibility())
                .isEqualTo(ReturnResponsibility.BUYER);
    }

    @Test
    void sellerReasonAutomaticallySetsSellerResponsibility() {
        Order order = mock(Order.class);
        SellerOrder sellerOrder = mock(SellerOrder.class);
        when(sellerOrder.getOrder()).thenReturn(order);

        ReturnRequest returnRequest = ReturnRequest.createRequested(
                order,
                sellerOrder,
                "return-request-key",
                ReturnReasonType.DEFECTIVE,
                "상품에 하자가 있습니다.",
                "홍길동",
                "010-1234-5678",
                "12345",
                "서울특별시 강남구 테스트로 1",
                null,
                LocalDateTime.now()
        );

        assertThat(returnRequest.getResponsibility())
                .isEqualTo(ReturnResponsibility.SELLER);
    }

    @Test
    void otherReasonRequiresResponsibilityBeforeApproval() {
        Order order = mock(Order.class);
        SellerOrder sellerOrder = mock(SellerOrder.class);
        when(sellerOrder.getOrder()).thenReturn(order);

        ReturnRequest returnRequest = ReturnRequest.createRequested(
                order,
                sellerOrder,
                "return-request-key",
                ReturnReasonType.OTHER,
                "기타 사유입니다.",
                "홍길동",
                "010-1234-5678",
                "12345",
                "서울특별시 강남구 테스트로 1",
                null,
                LocalDateTime.now()
        );

        assertThat(returnRequest.getResponsibility()).isNull();

        assertThatThrownBy(
                () -> returnRequest.approve(LocalDateTime.now())
        ).isInstanceOf(IllegalStateException.class);

        returnRequest.confirmResponsibility(
                ReturnResponsibility.BUYER
        );
        returnRequest.approve(LocalDateTime.now());

        assertThat(returnRequest.getResponsibility())
                .isEqualTo(ReturnResponsibility.BUYER);
        assertThat(returnRequest.getStatus())
                .isEqualTo(ReturnRequestStatus.APPROVED);
    }

    @Test
    void returnRequestRejectsSellerOrderFromDifferentOrder() {
        Order order = mock(Order.class);
        SellerOrder sellerOrder = mock(SellerOrder.class);
        when(sellerOrder.getOrder())
                .thenReturn(mock(Order.class));

        assertThatThrownBy(() -> ReturnRequest.createRequested(
                order,
                sellerOrder,
                "return-request-key",
                ReturnReasonType.CHANGE_OF_MIND,
                "단순 변심입니다.",
                "홍길동",
                "010-1234-5678",
                "12345",
                "서울특별시 강남구 테스트로 1",
                null,
                LocalDateTime.now()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requestedReturnCanBeRejected() {
        Order order = mock(Order.class);
        SellerOrder sellerOrder = mock(SellerOrder.class);
        when(sellerOrder.getOrder()).thenReturn(order);

        LocalDateTime now = LocalDateTime.now();

        ReturnRequest returnRequest = ReturnRequest.createRequested(
                order,
                sellerOrder,
                "return-request-key",
                ReturnReasonType.CHANGE_OF_MIND,
                "단순 변심입니다.",
                "홍길동",
                "010-1234-5678",
                "12345",
                "서울특별시 강남구 테스트로 1",
                null,
                now
        );

        returnRequest.reject(
                "반품 가능 기간이 지났습니다.",
                now.plusSeconds(1)
        );

        assertThat(returnRequest.getStatus())
                .isEqualTo(ReturnRequestStatus.REJECTED);
        assertThat(returnRequest.getRejectedReason())
                .isEqualTo("반품 가능 기간이 지났습니다.");
    }
}