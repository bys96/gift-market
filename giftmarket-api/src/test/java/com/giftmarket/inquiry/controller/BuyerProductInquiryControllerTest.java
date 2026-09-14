package com.giftmarket.inquiry.controller;

import com.giftmarket.inquiry.dto.ProductInquiryPageResponse;
import com.giftmarket.inquiry.service.ProductInquiryService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BuyerProductInquiryControllerTest {

    @Test
    void delegatesAuthenticatedMyInquiryList() {
        ProductInquiryService service = mock(ProductInquiryService.class);
        ProductInquiryPageResponse page = mock(ProductInquiryPageResponse.class);
        BuyerProductInquiryController controller = new BuyerProductInquiryController(service);
        given(service.getMyInquiries(1L, 0, 10)).willReturn(page);

        assertThat(controller.getMyInquiries(1L, 0, 10).data()).isSameAs(page);
        verify(service).getMyInquiries(1L, 0, 10);
    }
}
