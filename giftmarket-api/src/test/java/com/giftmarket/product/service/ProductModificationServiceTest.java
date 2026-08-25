package com.giftmarket.product.service;

import com.giftmarket.product.draft.service.ProductDraftService;
import com.giftmarket.product.dto.request.ProductModificationRequest;
import com.giftmarket.product.dto.request.ProductModificationVariantRequest;
import com.giftmarket.product.dto.request.ProductOptionReferenceRequest;
import com.giftmarket.product.dto.request.ProductOptionUpdateRequest;
import com.giftmarket.product.dto.request.ProductVariantUpdateRequest;
import com.giftmarket.product.dto.response.ProductOptionGroupResponse;
import com.giftmarket.product.dto.response.ProductOptionResponse;
import com.giftmarket.product.dto.response.ProductOptionValueResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductModificationServiceTest {

    @Mock ProductService productService;
    @Mock ProductOptionService productOptionService;
    @Mock ProductVariantService productVariantService;
    @Mock ProductDraftService productDraftService;
    @InjectMocks ProductModificationService productModificationService;

    @Test
    void retiresRemovedMappingsBeforeSavingOptionsAndVariants() {
        ProductModificationRequest request = mock(ProductModificationRequest.class);
        ProductOptionUpdateRequest options = new ProductOptionUpdateRequest(List.of());
        ProductModificationVariantRequest variant = new ProductModificationVariantRequest(
                10L, "SKU-30-WOODY", List.of(new ProductOptionReferenceRequest(0, 0)),
                0L, 7, true
        );
        ProductOptionResponse savedOptions = mock(ProductOptionResponse.class);
        ProductOptionGroupResponse group = mock(ProductOptionGroupResponse.class);
        ProductOptionValueResponse value = mock(ProductOptionValueResponse.class);

        when(request.options()).thenReturn(options);
        when(request.normalizedVariants()).thenReturn(List.of(variant));
        when(request.draftId()).thenReturn(null);
        when(savedOptions.getOptionGroups()).thenReturn(List.of(group));
        when(group.getSortOrder()).thenReturn(0);
        when(group.getValues()).thenReturn(List.of(value));
        when(value.getSortOrder()).thenReturn(0);
        when(value.getId()).thenReturn(20L);
        when(productOptionService.updateProductOptions(1L, 2L, options)).thenReturn(savedOptions);

        productModificationService.modifyProduct(1L, 2L, request);

        InOrder order = inOrder(productOptionService, productVariantService);
        order.verify(productOptionService).retireVariantsUsingRemovedOptions(1L, 2L, options);
        order.verify(productOptionService).updateProductOptions(1L, 2L, options);
        order.verify(productVariantService).updateProductVariants(any(), any(), any(ProductVariantUpdateRequest.class));
    }

    @Test
    void removingAllOptionsStillDeactivatesEveryExistingVariant() {
        ProductModificationRequest request = mock(ProductModificationRequest.class);
        ProductOptionUpdateRequest options = new ProductOptionUpdateRequest(List.of());
        ProductOptionResponse savedOptions = mock(ProductOptionResponse.class);

        when(request.options()).thenReturn(options);
        when(request.normalizedVariants()).thenReturn(List.of());
        when(request.draftId()).thenReturn(null);
        when(savedOptions.getOptionGroups()).thenReturn(List.of());
        when(productOptionService.updateProductOptions(1L, 2L, options)).thenReturn(savedOptions);

        productModificationService.modifyProduct(1L, 2L, request);

        verify(productVariantService).updateProductVariants(
                1L, 2L, new ProductVariantUpdateRequest(List.of())
        );
    }
}
