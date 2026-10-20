package com.giftmarket.product.controller;

import com.giftmarket.product.dto.request.ProductRegistrationRequest;
import com.giftmarket.product.dto.response.ProductRegistrationResponse;
import com.giftmarket.product.service.ProductModificationService;
import com.giftmarket.product.service.ProductOptionService;
import com.giftmarket.product.service.ProductRegistrationService;
import com.giftmarket.product.service.ProductService;
import com.giftmarket.product.service.ProductVariantService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class SellerProductControllerTest {

    @Test
    void wrapsRegistrationResultInSuccessfulApiResponse() {
        ProductService productService = mock(ProductService.class);
        ProductOptionService productOptionService = mock(ProductOptionService.class);
        ProductVariantService productVariantService = mock(ProductVariantService.class);
        ProductRegistrationService productRegistrationService = mock(ProductRegistrationService.class);
        ProductModificationService productModificationService = mock(ProductModificationService.class);
        ProductRegistrationRequest request = mock(ProductRegistrationRequest.class);
        ProductRegistrationResponse registration = mock(ProductRegistrationResponse.class);
        SellerProductController controller = new SellerProductController(
                productService,
                productOptionService,
                productVariantService,
                productRegistrationService,
                productModificationService
        );
        given(productRegistrationService.registerProduct(7L, request)).willReturn(registration);

        var response = controller.registerProduct(7L, request);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isSameAs(registration);
    }
}
