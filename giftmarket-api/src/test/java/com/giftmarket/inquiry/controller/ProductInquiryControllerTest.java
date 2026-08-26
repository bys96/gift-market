package com.giftmarket.inquiry.controller;

import com.giftmarket.inquiry.dto.ProductInquiryPageResponse;
import com.giftmarket.inquiry.dto.ProductInquiryRequest;
import com.giftmarket.inquiry.dto.ProductInquiryResponse;
import com.giftmarket.inquiry.service.ProductInquiryService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProductInquiryControllerTest {
    @Test void delegatesPublicListAndAuthenticatedMutations() {
        var service = mock(ProductInquiryService.class); var controller = new ProductInquiryController(service);
        var page = mock(ProductInquiryPageResponse.class); var response = mock(ProductInquiryResponse.class);
        var request = new ProductInquiryRequest("제목", "내용", false);
        given(service.getInquiries(null, 10L, 0, 10)).willReturn(page);
        given(service.create(1L, 10L, request)).willReturn(response);
        assertThat(controller.list(null, 10L, 0, 10).data()).isSameAs(page);
        assertThat(controller.create(1L, 10L, request).data()).isSameAs(response);
        assertThat(controller.delete(1L, 10L, 5L).success()).isTrue();
        verify(service).delete(1L, 10L, 5L);
    }
}
