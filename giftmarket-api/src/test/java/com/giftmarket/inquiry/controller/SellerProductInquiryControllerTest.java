package com.giftmarket.inquiry.controller;

import com.giftmarket.inquiry.dto.ProductInquiryAnswerRequest;
import com.giftmarket.inquiry.dto.ProductInquiryPageResponse;
import com.giftmarket.inquiry.dto.ProductInquiryResponse;
import com.giftmarket.inquiry.entity.ProductInquiryStatus;
import com.giftmarket.inquiry.service.SellerProductInquiryService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class SellerProductInquiryControllerTest {
    @Test void delegatesSellerOwnedListDetailAndAnswer() {
        var service = mock(SellerProductInquiryService.class); var controller = new SellerProductInquiryController(service);
        var page = mock(ProductInquiryPageResponse.class); var response = mock(ProductInquiryResponse.class);
        var answer = new ProductInquiryAnswerRequest("답변");
        given(service.getInquiries(1L, ProductInquiryStatus.WAITING, 0, 20)).willReturn(page);
        given(service.getInquiry(1L, 5L)).willReturn(response);
        given(service.answer(1L, 5L, answer)).willReturn(response);
        assertThat(controller.list(1L, ProductInquiryStatus.WAITING, 0, 20).data()).isSameAs(page);
        assertThat(controller.detail(1L, 5L).data()).isSameAs(response);
        assertThat(controller.answer(1L, 5L, answer).data()).isSameAs(response);
    }
}
