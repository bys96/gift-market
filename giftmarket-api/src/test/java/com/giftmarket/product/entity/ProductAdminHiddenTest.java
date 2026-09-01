package com.giftmarket.product.entity;

import com.giftmarket.seller.entity.Seller;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ProductAdminHiddenTest {

    @Test
    void hidesWithTrimmedReasonAndPreservesOnSaleStatus() {
        Product product = product(ProductStatus.ON_SALE);

        product.hideByAdmin("  운영 정책 위반  ");

        assertThat(product.isAdminHidden()).isTrue();
        assertThat(product.getAdminHiddenReason()).isEqualTo("운영 정책 위반");
        assertThat(product.getAdminHiddenAt()).isNotNull();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    void rejectsInvalidReason() {
        assertThatThrownBy(() -> product(ProductStatus.ON_SALE).hideByAdmin(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> product(ProductStatus.ON_SALE).hideByAdmin("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> product(ProductStatus.ON_SALE).hideByAdmin("a".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicateHideAndUnhide() {
        Product product = product(ProductStatus.ON_SALE);
        product.hideByAdmin("사유");

        assertThatThrownBy(() -> product.hideByAdmin("다른 사유"))
                .isInstanceOf(IllegalStateException.class);
        product.unhideByAdmin();
        assertThatThrownBy(product::unhideByAdmin)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unhidesAndPreservesSellerHiddenStatus() {
        Product product = product(ProductStatus.HIDDEN);
        product.hideByAdmin("사유");

        product.unhideByAdmin();

        assertThat(product.isAdminHidden()).isFalse();
        assertThat(product.getAdminHiddenReason()).isNull();
        assertThat(product.getAdminHiddenAt()).isNull();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
    }

    @Test
    void sellerStatusChangesNeverClearAdminHidden() {
        Product product = product(ProductStatus.ON_SALE);
        product.hideByAdmin("관리자 제재");

        product.changeStatus(ProductStatus.HIDDEN);
        assertThat(product.isAdminHidden()).isTrue();
        product.changeStatus(ProductStatus.ON_SALE);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(product.isAdminHidden()).isTrue();
        assertThat(product.getAdminHiddenReason()).isEqualTo("관리자 제재");
    }

    private Product product(ProductStatus status) {
        Product product = Product.createDraft(
                mock(Seller.class), mock(Category.class), "상품", null, null, null,
                10_000L, 10, null, true, 0L, null, null, null
        );
        product.changeStatus(status);
        return product;
    }
}
