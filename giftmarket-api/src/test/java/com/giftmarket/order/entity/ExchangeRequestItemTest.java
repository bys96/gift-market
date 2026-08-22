package com.giftmarket.order.entity;

import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductVariant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExchangeRequestItemTest {
    @Test
    void createsSameProductVariantTargetWithSnapshots() {
        Fixture fixture = fixture(true);
        ProductVariant target = variant(fixture.product);
        ExchangeRequestItem item = create(fixture, 2, fixture.product, target);
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getTargetProductName()).isEqualTo("교환 상품");
        assertThat(item.getTargetOptionSnapshot()).isEqualTo("색상: 블루");
        assertThat(item.getTargetUnitPrice()).isEqualTo(10_000L);
    }

    @Test
    void rejectsZeroExcessAndDifferentSellerOrder() {
        Fixture fixture = fixture(false);
        assertThatThrownBy(() -> create(fixture, 0, fixture.product, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(fixture, 4, fixture.product, null))
                .isInstanceOf(IllegalArgumentException.class);
        when(fixture.orderItem.getSellerOrder()).thenReturn(mock(SellerOrder.class));
        assertThatThrownBy(() -> create(fixture, 1, fixture.product, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDifferentProductAndForeignVariant() {
        Fixture fixture = fixture(true);
        Product other = mock(Product.class);
        assertThatThrownBy(() -> create(fixture, 1, other, variant(other)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(fixture, 1, fixture.product, variant(other)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresVariantForVariantOrderAndNullForOptionlessOrder() {
        Fixture variantFixture = fixture(true);
        assertThatThrownBy(() -> create(variantFixture, 1, variantFixture.product, null))
                .isInstanceOf(IllegalArgumentException.class);
        Fixture optionless = fixture(false);
        assertThatThrownBy(() -> create(optionless, 1, optionless.product, variant(optionless.product)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reservationCanBeReservedThenReleasedExactlyOnce() {
        Fixture fixture = fixture(false);
        ExchangeRequestItem item = create(fixture, 2, fixture.product, null);
        item.reserveTargetStock(2);
        assertThat(item.getEffectiveReservedQuantity()).isEqualTo(2);
        item.releaseTargetStockReservation(2);
        assertThat(item.getEffectiveReservedQuantity()).isZero();
        assertThatThrownBy(() -> item.releaseTargetStockReservation(2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item.reserveTargetStock(2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reservationCanBeConsumedExactlyOnceForOutbound() {
        Fixture fixture = fixture(false);
        ExchangeRequestItem item = create(fixture, 2, fixture.product, null);
        item.reserveTargetStock(2);
        item.consumeTargetStockReservation(2);
        assertThat(item.getConsumedQuantity()).isEqualTo(2);
        assertThat(item.getEffectiveReservedQuantity()).isZero();
        assertThatThrownBy(() -> item.consumeTargetStockReservation(2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reservationCanBePartiallyReleasedThenConsumedWithinEffectiveQuantity() {
        Fixture fixture = fixture(false);
        ExchangeRequestItem item = create(fixture, 3, fixture.product, null);
        item.reserveTargetStock(3);

        item.releaseTargetStockReservation(1);
        item.consumeTargetStockReservation(2);

        assertThat(item.getReleasedQuantity()).isEqualTo(1);
        assertThat(item.getConsumedQuantity()).isEqualTo(2);
        assertThat(item.getEffectiveReservedQuantity()).isZero();
        assertThatThrownBy(() -> item.consumeTargetStockReservation(1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reservationCanBePartiallyConsumedThenReleasedWithinEffectiveQuantity() {
        Fixture fixture = fixture(false);
        ExchangeRequestItem item = create(fixture, 3, fixture.product, null);
        item.reserveTargetStock(3);

        item.consumeTargetStockReservation(2);
        item.releaseTargetStockReservation(1);

        assertThat(item.getConsumedQuantity()).isEqualTo(2);
        assertThat(item.getReleasedQuantity()).isEqualTo(1);
        assertThat(item.getEffectiveReservedQuantity()).isZero();
        assertThatThrownBy(() -> item.releaseTargetStockReservation(1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reservationOperationsRejectZeroNegativeAndQuantityExcess() {
        Fixture fixture = fixture(false);
        ExchangeRequestItem item = create(fixture, 2, fixture.product, null);

        assertThatThrownBy(() -> item.reserveTargetStock(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item.reserveTargetStock(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item.reserveTargetStock(3)).isInstanceOf(IllegalArgumentException.class);

        item.reserveTargetStock(2);
        assertThatThrownBy(() -> item.releaseTargetStockReservation(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item.releaseTargetStockReservation(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item.consumeTargetStockReservation(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item.consumeTargetStockReservation(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void combinedReleasedAndConsumedQuantityCannotExceedReservation() {
        Fixture fixture = fixture(false);
        ExchangeRequestItem item = create(fixture, 2, fixture.product, null);
        item.reserveTargetStock(2);
        ReflectionTestUtils.setField(item, "releasedQuantity", 2);
        ReflectionTestUtils.setField(item, "consumedQuantity", 2);

        assertThatThrownBy(item::getEffectiveReservedQuantity)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void restockableInspectionRestoresOnlyRequestedQuantity() {
        Fixture fixture = fixture(false);
        ExchangeRequestItem item = create(fixture, 2, fixture.product, null);
        item.inspect(ExchangeInspectionResult.RESTOCKABLE);
        item.increaseRestockedQuantity(2);
        assertThat(item.getRestockedQuantity()).isEqualTo(2);
        assertThatThrownBy(() -> item.increaseRestockedQuantity(1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonRestockableDoesNotFailExchangeButCannotRestock() {
        Fixture fixture = fixture(false);
        ExchangeRequestItem item = create(fixture, 1, fixture.product, null);
        item.inspect(ExchangeInspectionResult.NON_RESTOCKABLE);
        assertThat(item.getInspectionResult()).isEqualTo(ExchangeInspectionResult.NON_RESTOCKABLE);
        assertThatThrownBy(() -> item.increaseRestockedQuantity(1))
                .isInstanceOf(IllegalStateException.class);
    }

    private Fixture fixture(boolean withVariant) {
        SellerOrder sellerOrder = mock(SellerOrder.class);
        ExchangeRequest request = mock(ExchangeRequest.class);
        Product product = mock(Product.class);
        OrderItem orderItem = mock(OrderItem.class);
        when(request.getSellerOrder()).thenReturn(sellerOrder);
        when(orderItem.getSellerOrder()).thenReturn(sellerOrder);
        when(orderItem.getProduct()).thenReturn(product);
        ProductVariant originalVariant = withVariant ? variant(product) : null;
        when(orderItem.getVariant()).thenReturn(originalVariant);
        when(orderItem.getExchangeableQuantity()).thenReturn(3);
        return new Fixture(request, orderItem, product);
    }

    private ExchangeRequestItem create(Fixture fixture, int quantity, Product product, ProductVariant variant) {
        return ExchangeRequestItem.create(fixture.request, fixture.orderItem, quantity, product, variant,
                "교환 상품", variant == null ? null : "색상: 블루", 10_000L);
    }

    private ProductVariant variant(Product product) {
        ProductVariant variant = mock(ProductVariant.class);
        when(variant.getProduct()).thenReturn(product);
        return variant;
    }

    private record Fixture(ExchangeRequest request, OrderItem orderItem, Product product) {
    }
}
