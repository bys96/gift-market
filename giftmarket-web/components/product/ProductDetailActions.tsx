"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";

import { useCartStore } from "@/stores/cart-store";
import { useWishlistStore } from "@/stores/wishlist-store";
import type { Product } from "@/types/product";

interface ProductDetailActionsProps {
  product: {
    id: number;
    name: string;
    brandName: string;
    price: number;
    imageUrl: string;
    stockQuantity: number;
    isFreeShipping: boolean;
  };
}

export default function ProductDetailActions({
  product,
}: ProductDetailActionsProps) {
  const router = useRouter();

  const [quantity, setQuantity] = useState(1);

  const addCartItem = useCartStore((state) => state.addItem);

  const wishlistItems = useWishlistStore((state) => state.items);
  const toggleWishlistItem = useWishlistStore((state) => state.toggleItem);

  const isWishlisted = wishlistItems.some((item) => item.id === product.id);

  const totalPrice = useMemo(
    () => product.price * quantity,
    [product.price, quantity],
  );

  const wishlistProduct: Product = {
    id: product.id,
    name: product.name,
    brandName: product.brandName,
    price: product.price,
    imageUrl: product.imageUrl,
    isFreeShipping: product.isFreeShipping,
  };

  const decreaseQuantity = () => {
    setQuantity((currentQuantity) => Math.max(1, currentQuantity - 1));
  };

  const increaseQuantity = () => {
    setQuantity((currentQuantity) =>
      Math.min(product.stockQuantity, currentQuantity + 1),
    );
  };

  const addSelectedQuantityToCart = () => {
    addCartItem({
      productId: product.id,
      name: product.name,
      brandName: product.brandName,
      price: product.price,
      imageUrl: product.imageUrl,
      quantity,
      stockQuantity: product.stockQuantity,
      isFreeShipping: product.isFreeShipping,
    });
  };

  const handleToggleWishlist = () => {
    toggleWishlistItem(wishlistProduct);
  };

  const handleAddCart = () => {
    addSelectedQuantityToCart();
    alert("장바구니에 상품을 담았습니다.");
  };

  const handleBuyNow = () => {
    addSelectedQuantityToCart();
    router.push("/cart");
  };

  return (
    <div className="product-detail-purchase">
      <div className="product-detail-quantity-section">
        <div>
          <strong className="product-detail-quantity-title">구매 수량</strong>

          <p className="product-detail-quantity-stock">
            현재 재고 {product.stockQuantity.toLocaleString("ko-KR")}개
          </p>
        </div>

        <div
          className="product-detail-quantity-control"
          aria-label="상품 수량 선택"
        >
          <button
            type="button"
            className="product-detail-quantity-button"
            aria-label="수량 줄이기"
            disabled={quantity <= 1}
            onClick={decreaseQuantity}
          >
            −
          </button>

          <span className="product-detail-quantity-value" aria-live="polite">
            {quantity}
          </span>

          <button
            type="button"
            className="product-detail-quantity-button"
            aria-label="수량 늘리기"
            disabled={quantity >= product.stockQuantity}
            onClick={increaseQuantity}
          >
            +
          </button>
        </div>
      </div>

      <div className="product-detail-total">
        <span>총 상품 금액</span>

        <strong>{totalPrice.toLocaleString("ko-KR")}원</strong>
      </div>

      <div className="product-detail-actions">
        <button
          type="button"
          className={[
            "product-detail-wishlist-button",
            isWishlisted ? "product-detail-wishlist-button-active" : "",
          ]
            .filter(Boolean)
            .join(" ")}
          aria-label={isWishlisted ? "찜 목록에서 제거" : "찜 목록에 추가"}
          aria-pressed={isWishlisted}
          onClick={handleToggleWishlist}
        >
          <span aria-hidden="true">{isWishlisted ? "♥" : "♡"}</span>

          <span>찜</span>
        </button>

        <button
          type="button"
          className="product-detail-cart-button"
          onClick={handleAddCart}
        >
          장바구니
        </button>

        <button
          type="button"
          className="product-detail-gift-button"
          onClick={handleBuyNow}
        >
          바로 구매
        </button>
      </div>
    </div>
  );
}
