package com.giftmarket.order.service;

import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.OrderCancellationItem;
import com.giftmarket.order.entity.ReturnInspectionResult;
import com.giftmarket.order.entity.ReturnRequestItem;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductVariant;
import com.giftmarket.product.repository.ProductRepository;
import com.giftmarket.product.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderInventoryServiceTest {

    private static final Long ORDER_ID = 1L;
    private static final Long PRODUCT_ID = 2L;
    private static final Long VARIANT_ID = 3L;

    @Mock OrderItemRepository orderItemRepository;
    @Mock ProductRepository productRepository;
    @Mock ProductVariantRepository productVariantRepository;
    @Mock OrderItem orderItem;
    @Mock Product product;
    @Mock ProductVariant variant;
    @Mock OrderCancellationItem cancellationItem;
    @Mock ReturnRequestItem returnItem;

    private OrderInventoryService service;

    @BeforeEach
    void setUp() {
        service = new OrderInventoryService(
                orderItemRepository,
                productRepository,
                productVariantRepository
        );
        given(orderItemRepository.findAllByOrderIdOrderByIdAsc(ORDER_ID))
                .willReturn(List.of(orderItem));
        given(orderItem.getProduct()).willReturn(product);
        given(product.getId()).willReturn(PRODUCT_ID);
        given(productRepository.findByIdForUpdate(PRODUCT_ID))
                .willReturn(Optional.of(product));
        given(orderItem.getQuantity()).willReturn(2);
    }

    @Test
    void restoresProductStockForNonVariantItem() {
        given(orderItem.getVariant()).willReturn(null);

        assertThat(service.restore(ORDER_ID)).containsExactly(orderItem);

        verify(product).increaseStock(2);
    }

    @Test
    void restoresVariantAndSynchronizesProductTotalStock() {
        given(orderItem.getVariant()).willReturn(variant);
        given(variant.getId()).willReturn(VARIANT_ID);
        given(variant.getStockQuantity()).willReturn(7);
        given(productVariantRepository.findWithLockByIdAndProductId(
                VARIANT_ID,
                PRODUCT_ID
        )).willReturn(Optional.of(variant));
        given(productVariantRepository.findAllByProductIdAndActiveTrueOrderByIdAsc(
                PRODUCT_ID
        )).willReturn(List.of(variant));

        service.restore(ORDER_ID);

        verify(variant).increaseStock(2);
        verify(product).changeStockQuantity(7);
    }

    @Test
    void restoresOnlyCancellationItemQuantityForProduct() {
        given(cancellationItem.getOrderItem()).willReturn(orderItem);
        given(cancellationItem.getQuantity()).willReturn(1);
        given(orderItem.getVariant()).willReturn(null);

        service.restoreCancellationItems(List.of(cancellationItem));

        verify(product).increaseStock(1);
    }

    @Test
    void restoresOnlyCancellationItemQuantityForVariantAndSynchronizesProduct() {
        given(cancellationItem.getOrderItem()).willReturn(orderItem);
        given(cancellationItem.getQuantity()).willReturn(1);
        given(orderItem.getVariant()).willReturn(variant);
        given(variant.getId()).willReturn(VARIANT_ID);
        given(variant.getStockQuantity()).willReturn(8);
        given(productVariantRepository.findWithLockByIdAndProductId(VARIANT_ID, PRODUCT_ID))
                .willReturn(Optional.of(variant));
        given(productVariantRepository.findAllByProductIdAndActiveTrueOrderByIdAsc(PRODUCT_ID))
                .willReturn(List.of(variant));

        service.restoreCancellationItems(List.of(cancellationItem));

        verify(variant).increaseStock(1);
        verify(product).changeStockQuantity(8);
    }

    @Test
    void restoresOnlyRestockableReturnQuantity() {
        given(returnItem.getInspectionResult()).willReturn(ReturnInspectionResult.RESTOCKABLE);
        given(returnItem.getOrderItem()).willReturn(orderItem);
        given(returnItem.getQuantity()).willReturn(1);
        given(orderItem.getVariant()).willReturn(null);

        service.restoreReturnItems(List.of(returnItem));

        verify(product).increaseStock(1);
    }

    @Test
    void doesNotRestoreNonRestockableReturnQuantity() {
        given(returnItem.getInspectionResult()).willReturn(ReturnInspectionResult.NON_RESTOCKABLE);

        service.restoreReturnItems(List.of(returnItem));

        verify(product, never()).increaseStock(org.mockito.ArgumentMatchers.anyInt());
    }
}
