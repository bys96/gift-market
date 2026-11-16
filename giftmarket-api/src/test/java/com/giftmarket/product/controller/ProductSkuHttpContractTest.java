package com.giftmarket.product.controller;

import com.giftmarket.global.exception.GlobalExceptionHandler;
import com.giftmarket.product.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductSkuHttpContractTest {
    private final ProductVariantService variants = mock(ProductVariantService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new SellerProductController(
            mock(ProductService.class), mock(ProductOptionService.class), variants,
            mock(ProductRegistrationService.class), mock(ProductModificationService.class)))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void oldTabsReceiveConflictOnEverySkuWriteRoute() throws Exception {
        String variant = "{\"skuCode\":\"GM-system-uuid\",\"optionValueIds\":[1],\"options\":[{\"optionGroupSortOrder\":0,\"optionValueSortOrder\":0}],\"additionalPrice\":0,\"stockQuantity\":10,\"active\":true}";
        mvc.perform(put("/api/seller/products/1/variants").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variants\":[" + variant + "]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("message").value(org.hamcrest.Matchers.containsString("sellerSkuCode")));
        mvc.perform(post("/api/seller/products/registration").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variants\":[" + variant + "]}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/seller/products/1/modification").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variants\":[" + variant + "]}"))
                .andExpect(status().isConflict());
        verifyNoInteractions(variants);
    }

    @Test
    void explicitSellerCodeWorksAndBlankOrMissingCodeCannotPass() throws Exception {
        String fields = ",\"optionValueIds\":[1],\"additionalPrice\":0,\"active\":true";
        mvc.perform(put("/api/seller/products/1/variants").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variants\":[{\"sellerSkuCode\":\"F1\"" + fields + "}]}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/seller/products/1/variants").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variants\":[{\"sellerSkuCode\":\" \"" + fields + "}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/seller/products/1/variants").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variants\":[{\"optionValueIds\":[1],\"additionalPrice\":0,\"active\":true}]}"))
                .andExpect(status().isConflict());
    }
}
