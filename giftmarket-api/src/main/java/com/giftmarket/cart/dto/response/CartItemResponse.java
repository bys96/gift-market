package com.giftmarket.cart.dto.response;

import com.giftmarket.cart.entity.CartItem;
import com.giftmarket.product.entity.ProductOptionValue;
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
     * 실제 구매 단가.
     * 옵션 상품이면 상품 기본가 + Variant 추가금.
     */
    private Long price;

    private Long additionalPrice;

    private Integer stockQuantity;

    private Integer quantity;

    private boolean freeShipping;

    private Long shippingFee;

    private String representativeImageKey;

    private List<CartItemOptionResponse> options;

    public static CartItemResponse from(
            CartItem cartItem,
            List<ProductVariantOptionValue> variantOptionValues
    ) {
        ProductVariant variant = cartItem.getVariant();

        long additionalPrice =
                variant == null
                        ? 0L
                        : variant.getAdditionalPrice();

        long price =
                cartItem.getProduct().getPrice()
                        + additionalPrice;

        int stockQuantity =
                variant == null
                        ? cartItem.getProduct().getStockQuantity()
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

        return CartItemResponse.builder()
                .cartItemId(cartItem.getId())
                .productId(
                        cartItem.getProduct().getId()
                )
                .variantId(
                        variant == null
                                ? null
                                : variant.getId()
                )
                .sellerId(
                        cartItem.getProduct()
                                .getSeller()
                                .getId()
                )
                .storeName(
                        cartItem.getProduct()
                                .getSeller()
                                .getStoreName()
                )
                .productName(
                        cartItem.getProduct().getName()
                )
                .brandName(
                        cartItem.getProduct().getBrandName()
                )
                .price(price)
                .additionalPrice(additionalPrice)
                .stockQuantity(stockQuantity)
                .quantity(cartItem.getQuantity())
                .freeShipping(
                        cartItem.getProduct().isFreeShipping()
                )
                .shippingFee(
                        cartItem.getProduct().getShippingFee()
                )
                .representativeImageKey(
                        cartItem.getProduct()
                                .getRepresentativeImageKey()
                )
                .options(options)
                .build();
    }
}