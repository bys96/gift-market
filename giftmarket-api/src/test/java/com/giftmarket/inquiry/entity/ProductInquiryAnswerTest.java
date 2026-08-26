package com.giftmarket.inquiry.entity;

import com.giftmarket.seller.entity.Seller;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ProductInquiryAnswerTest {
    @Test void createsAndUpdatesOneAnswerEntity() {
        var inquiry = mock(ProductInquiry.class);
        var seller = mock(Seller.class);
        var answer = ProductInquiryAnswer.create(inquiry, seller, "첫 답변");

        answer.updateContent("수정 답변");

        assertThat(answer.getInquiry()).isSameAs(inquiry);
        assertThat(answer.getSeller()).isSameAs(seller);
        assertThat(answer.getContent()).isEqualTo("수정 답변");
    }
}
