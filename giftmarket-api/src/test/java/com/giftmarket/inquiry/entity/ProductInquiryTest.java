package com.giftmarket.inquiry.entity;

import com.giftmarket.product.entity.Product;
import com.giftmarket.user.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ProductInquiryTest {
    @Test void createsWaitingInquiry() {
        var inquiry = ProductInquiry.create(mock(Product.class), mock(User.class), "제목", "내용", true);
        assertThat(inquiry.getStatus()).isEqualTo(ProductInquiryStatus.WAITING);
        assertThat(inquiry.isPrivateInquiry()).isTrue();
    }
    @Test void markAnsweredChangesStatus() {
        var inquiry = ProductInquiry.create(mock(Product.class), mock(User.class), "제목", "내용", false);
        inquiry.markAnswered();
        assertThat(inquiry.getStatus()).isEqualTo(ProductInquiryStatus.ANSWERED);
    }
    @Test void answeredInquiryCannotChangeQuestion() {
        var inquiry = ProductInquiry.create(mock(Product.class), mock(User.class), "제목", "내용", false);
        inquiry.markAnswered();
        assertThatThrownBy(() -> inquiry.updateQuestion("수정", "수정", false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(inquiry::ensureWaiting).isInstanceOf(IllegalStateException.class);
    }

    @Test void softDeleteIsIdempotent() {
        var inquiry = ProductInquiry.create(mock(Product.class), mock(User.class), "제목", "내용", false);
        inquiry.softDelete();
        var deletedAt = inquiry.getDeletedAt();
        inquiry.softDelete();
        assertThat(inquiry.isDeleted()).isTrue();
        assertThat(inquiry.getDeletedAt()).isEqualTo(deletedAt);
    }
}
