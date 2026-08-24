package com.giftmarket.order.service;

import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.entity.OrderCancellationItem;
import com.giftmarket.order.entity.ReturnInspectionResult;
import com.giftmarket.order.entity.ReturnRequestItem;
import com.giftmarket.order.entity.ExchangeRequestItem;
import com.giftmarket.order.entity.ExchangeInspectionResult;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.product.entity.ProductStatus;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;

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
    @Mock ExchangeRequestItem exchangeItem;

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

    @Test
    void restoresOnlyRestockableExchangeOriginalProductQuantity() {
        given(exchangeItem.getInspectionResult()).willReturn(ExchangeInspectionResult.RESTOCKABLE);
        given(exchangeItem.getOrderItem()).willReturn(orderItem);
        given(exchangeItem.getQuantity()).willReturn(1);
        given(orderItem.getVariant()).willReturn(null);

        service.restoreExchangeOriginalItems(List.of(exchangeItem));

        verify(product).increaseStock(1);
    }

    @Test
    void restoresExchangeOriginalVariantAndSynchronizesProduct() {
        given(exchangeItem.getInspectionResult()).willReturn(ExchangeInspectionResult.RESTOCKABLE);
        given(exchangeItem.getOrderItem()).willReturn(orderItem);
        given(exchangeItem.getQuantity()).willReturn(1);
        given(orderItem.getVariant()).willReturn(variant);
        given(variant.getId()).willReturn(VARIANT_ID);
        given(variant.getStockQuantity()).willReturn(9);
        given(productVariantRepository.findWithLockByIdAndProductId(VARIANT_ID, PRODUCT_ID))
                .willReturn(Optional.of(variant));
        given(productVariantRepository.findAllByProductIdAndActiveTrueOrderByIdAsc(PRODUCT_ID))
                .willReturn(List.of(variant));

        service.restoreExchangeOriginalItems(List.of(exchangeItem));

        verify(variant).increaseStock(1);
        verify(product).changeStockQuantity(9);
    }

    @Test
    void doesNotRestoreNonRestockableExchangeOriginal() {
        given(exchangeItem.getInspectionResult()).willReturn(ExchangeInspectionResult.NON_RESTOCKABLE);

        service.restoreExchangeOriginalItems(List.of(exchangeItem));

        verify(product, never()).increaseStock(anyInt());
        verify(variant, never()).increaseStock(anyInt());
    }

    @Test
    void reservesOptionlessExchangeTargetAndBooksQuantity() {
        given(exchangeItem.getTargetProduct()).willReturn(product);
        given(exchangeItem.getTargetVariant()).willReturn(null);
        given(exchangeItem.getOrderItem()).willReturn(orderItem);
        given(exchangeItem.getQuantity()).willReturn(2);
        given(orderItem.getId()).willReturn(10L);
        given(orderItem.getVariant()).willReturn(null);
        given(orderItem.getUnitPrice()).willReturn(10_000L);
        given(product.getPrice()).willReturn(10_000L);
        given(product.getStockQuantity()).willReturn(2);
        given(product.getStatus()).willReturn(ProductStatus.ON_SALE);

        service.reserveExchangeTargets(List.of(exchangeItem));

        verify(product).decreaseStock(2);
        verify(exchangeItem).reserveTargetStock(2);
    }

    @Test
    void aggregatesSameVariantAndSynchronizesProductStock() {
        ExchangeRequestItem second = org.mockito.Mockito.mock(ExchangeRequestItem.class);
        OrderItem secondOrderItem = org.mockito.Mockito.mock(OrderItem.class);
        given(exchangeItem.getTargetProduct()).willReturn(product);
        given(second.getTargetProduct()).willReturn(product);
        given(exchangeItem.getTargetVariant()).willReturn(variant);
        given(second.getTargetVariant()).willReturn(variant);
        given(exchangeItem.getOrderItem()).willReturn(orderItem);
        given(second.getOrderItem()).willReturn(secondOrderItem);
        given(exchangeItem.getQuantity()).willReturn(2);
        given(second.getQuantity()).willReturn(2);
        given(orderItem.getId()).willReturn(10L);
        given(secondOrderItem.getId()).willReturn(11L);
        given(orderItem.getVariant()).willReturn(variant);
        given(secondOrderItem.getProduct()).willReturn(product);
        given(secondOrderItem.getVariant()).willReturn(variant);
        given(orderItem.getUnitPrice()).willReturn(10_000L);
        given(secondOrderItem.getUnitPrice()).willReturn(10_000L);
        given(variant.getId()).willReturn(VARIANT_ID);
        given(variant.getProduct()).willReturn(product);
        given(variant.getAdditionalPrice()).willReturn(0L);
        given(variant.getStockQuantity()).willReturn(4);
        given(variant.isActive()).willReturn(true);
        given(product.getPrice()).willReturn(10_000L);
        given(product.getStatus()).willReturn(ProductStatus.ON_SALE);
        given(productRepository.findByIdForUpdate(PRODUCT_ID)).willReturn(Optional.of(product));
        given(productVariantRepository.findWithLockByIdAndProductId(VARIANT_ID, PRODUCT_ID))
                .willReturn(Optional.of(variant));
        given(productVariantRepository.findAllByProductIdAndActiveTrueOrderByIdAsc(PRODUCT_ID))
                .willReturn(List.of(variant));

        service.reserveExchangeTargets(List.of(second, exchangeItem));

        verify(variant).decreaseStock(4);
        verify(product).changeStockQuantity(4);
        verify(exchangeItem).reserveTargetStock(2);
        verify(second).reserveTargetStock(2);
    }

    @Test
    void validatesAllTargetsBeforeAnyStockMutation() {
        ExchangeRequestItem insufficient = org.mockito.Mockito.mock(ExchangeRequestItem.class);
        Product otherProduct = org.mockito.Mockito.mock(Product.class);
        OrderItem otherOrderItem = org.mockito.Mockito.mock(OrderItem.class);
        given(exchangeItem.getTargetProduct()).willReturn(product);
        given(exchangeItem.getTargetVariant()).willReturn(null);
        given(exchangeItem.getOrderItem()).willReturn(orderItem);
        given(exchangeItem.getQuantity()).willReturn(1);
        given(orderItem.getId()).willReturn(10L);
        given(orderItem.getVariant()).willReturn(null);
        given(orderItem.getUnitPrice()).willReturn(10_000L);
        given(product.getPrice()).willReturn(10_000L);
        given(product.getStockQuantity()).willReturn(10);
        given(product.getStatus()).willReturn(ProductStatus.ON_SALE);
        given(insufficient.getTargetProduct()).willReturn(otherProduct);
        given(insufficient.getTargetVariant()).willReturn(null);
        given(insufficient.getOrderItem()).willReturn(otherOrderItem);
        given(insufficient.getQuantity()).willReturn(2);
        given(otherOrderItem.getId()).willReturn(11L);
        given(otherOrderItem.getProduct()).willReturn(otherProduct);
        given(otherOrderItem.getVariant()).willReturn(null);
        given(otherOrderItem.getUnitPrice()).willReturn(10_000L);
        given(otherProduct.getId()).willReturn(20L);
        given(otherProduct.getPrice()).willReturn(10_000L);
        given(otherProduct.getStockQuantity()).willReturn(1);
        given(otherProduct.getStatus()).willReturn(ProductStatus.ON_SALE);
        given(productRepository.findByIdForUpdate(20L)).willReturn(Optional.of(otherProduct));

        assertThatThrownBy(() -> service.reserveExchangeTargets(List.of(exchangeItem, insufficient)))
                .isInstanceOf(OrderException.class).hasMessageContaining("재고가 부족");
        verify(product, never()).decreaseStock(anyInt());
        verify(otherProduct, never()).decreaseStock(anyInt());
        verify(exchangeItem, never()).reserveTargetStock(anyInt());
        verify(insufficient, never()).reserveTargetStock(anyInt());
    }

    @Test
    void blocksChangedPriceAndInactiveVariantBeforeMutation() {
        given(exchangeItem.getTargetProduct()).willReturn(product);
        given(exchangeItem.getTargetVariant()).willReturn(variant);
        given(exchangeItem.getOrderItem()).willReturn(orderItem);
        given(exchangeItem.getQuantity()).willReturn(1);
        given(orderItem.getId()).willReturn(10L);
        given(orderItem.getVariant()).willReturn(variant);
        given(orderItem.getUnitPrice()).willReturn(10_000L);
        given(variant.getId()).willReturn(VARIANT_ID);
        given(variant.getProduct()).willReturn(product);
        given(variant.getAdditionalPrice()).willReturn(1_000L);
        given(variant.getStockQuantity()).willReturn(5);
        given(product.getPrice()).willReturn(10_000L);
        given(product.getStatus()).willReturn(ProductStatus.ON_SALE);
        given(productVariantRepository.findWithLockByIdAndProductId(VARIANT_ID, PRODUCT_ID))
                .willReturn(Optional.of(variant));

        assertThatThrownBy(() -> service.reserveExchangeTargets(List.of(exchangeItem)))
                .isInstanceOf(OrderException.class).hasMessageContaining("사용할 수 없는");

        given(variant.isActive()).willReturn(true);
        assertThatThrownBy(() -> service.reserveExchangeTargets(List.of(exchangeItem)))
                .isInstanceOf(OrderException.class).hasMessageContaining("가격이 변경");
        verify(variant, never()).decreaseStock(anyInt());
        verify(exchangeItem, never()).reserveTargetStock(anyInt());
    }
}
