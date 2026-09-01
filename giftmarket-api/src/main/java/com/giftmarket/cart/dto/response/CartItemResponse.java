package com.giftmarket.cart.dto.response;

import com.giftmarket.cart.entity.CartItem;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductOptionValue;
import com.giftmarket.product.entity.ProductStatus;
import com.giftmarket.product.entity.ProductVariant;
import com.giftmarket.product.entity.ProductVariantOptionValue;
import lombok.Builder;
import lombok.Getter;

import java.util.Comparator;
import java.util.List;

@Getter
@Builder
public class CartItemResponse {

    private Long cartItemId;

    private Long productId;

    private Long variantId;

    private Long sellerId;

    private String storeName;

    private String productName;

    private String brandName;

    /**
     * 현재 실제 구매 단가.
     * 옵션 상품이면 상품 기본가 + Variant 추가금.
     */
    private Long price;

    private Long additionalPrice;

    /**
     * 현재 실제 구매 가능 재고.
     * 옵션 상품이면 Variant 재고.
     */
    private Integer stockQuantity;

    /**
     * 장바구니에 저장된 수량.
     */
    private Integer quantity;

    private boolean freeShipping;

    private Long shippingFee;

    private String representativeImageKey;

    private List<CartItemOptionResponse> options;

    /**
     * 현재 시점에 이 CartItem을 구매할 수 있는지 여부.
     */
    private boolean purchasable;

    /**
     * 구매 불가 사유.
     * 구매 가능하면 AVAILABLE.
     */
    private CartItemAvailability availability;

    public static CartItemResponse from(
            CartItem cartItem,
            List<ProductVariantOptionValue> variantOptionValues
    ) {
        Product product = cartItem.getProduct();
        ProductVariant variant = cartItem.getVariant();

        long additionalPrice =
                variant == null
                        ? 0L
                        : variant.getAdditionalPrice();

        long price =
                product.getPrice()
                        + additionalPrice;

        int stockQuantity =
                variant == null
                        ? product.getStockQuantity()
                        : variant.getStockQuantity();

        List<CartItemOptionResponse> options =
                variant == null
                        ? List.of()
                        : variantOptionValues.stream()
                        .map(ProductVariantOptionValue::getOptionValue)
                        .sorted(
                                Comparator
                                        .comparing(
                                                (ProductOptionValue value) ->
                                                        value.getOptionGroup()
                                                                .getSortOrder()
                                        )
                                        .thenComparing(
                                                ProductOptionValue::getSortOrder
                                        )
                        )
                        .map(CartItemOptionResponse::from)
                        .toList();

        CartItemAvailability availability =
                resolveAvailability(
                        product,
                        variant,
                        cartItem.getQuantity(),
                        stockQuantity
                );

        return CartItemResponse.builder()
                .cartItemId(cartItem.getId())
                .productId(product.getId())
                .variantId(
                        variant == null
                                ? null
                                : variant.getId()
                )
                .sellerId(
                        product.getSeller().getId()
                )
                .storeName(
                        product.getSeller().getStoreName()
                )
                .productName(product.getName())
                .brandName(product.getBrandName())
                .price(price)
                .additionalPrice(additionalPrice)
                .stockQuantity(stockQuantity)
                .quantity(cartItem.getQuantity())
                .freeShipping(product.isFreeShipping())
                .shippingFee(product.getShippingFee())
                .representativeImageKey(
                        product.getRepresentativeImageKey()
                )
                .options(options)
                .purchasable(
                        availability
                                == CartItemAvailability.AVAILABLE
                )
                .availability(availability)
                .build();
    }

    private static CartItemAvailability resolveAvailability(
            Product product,
            ProductVariant variant,
            int quantity,
            int stockQuantity
    ) {
        if (product.isAdminHidden()
                || product.getStatus() == ProductStatus.HIDDEN) {
            return CartItemAvailability.SALE_STOPPED;
        }

        if (product.getStatus() == ProductStatus.SOLD_OUT
                || stockQuantity <= 0) {
            return CartItemAvailability.SOLD_OUT;
        }

        if (product.getStatus() != ProductStatus.ON_SALE) {
            return CartItemAvailability.SALE_STOPPED;
        }

        if (variant != null && !variant.isActive()) {
            return CartItemAvailability.OPTION_INACTIVE;
        }

        if (quantity > stockQuantity) {
            return CartItemAvailability.INSUFFICIENT_STOCK;
        }

        return CartItemAvailability.AVAILABLE;
    }
}
