package com.giftmarket.product.draft.controller;

import com.giftmarket.product.draft.service.ProductDraftService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class SellerProductDraftControllerTest {

    @Test
    void returnsSuccessfulNullDataWhenModificationDraftDoesNotExist() {
        ProductDraftService productDraftService = mock(ProductDraftService.class);
        SellerProductDraftController controller =
                new SellerProductDraftController(productDraftService);
        given(productDraftService.getProductDraft(7L, 13L)).willReturn(null);

        var response = controller.getDrafts(7L, 13L, false);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isNull();
    }
}
