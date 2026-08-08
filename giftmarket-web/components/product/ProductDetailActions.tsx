"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { usePathname } from "next/navigation";

import { useAuthStore } from "@/stores/auth-store";
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
  const pathname = usePathname();

  const [quantity, setQuantity] = useState(1);
  const [isAddingCart, setIsAddingCart] = useState(false);
  const [isBuyingNow, setIsBuyingNow] = useState(false);

  const addCartItem = useCartStore((state) => state.addItem);

  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const wishlistItems = useWishlistStore((state) => state.items);
  const wishlistHydrated = useWishlistStore((state) => state.hydrated);
  const toggleWishlistItem = useWishlistStore((state) => state.toggleItem);

  const isWishlisted =
    wishlistHydrated && wishlistItems.some((item) => item.id === product.id);

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

  const handleToggleWishlist = () => {
    if (!wishlistHydrated) {
      return;
    }

    toggleWishlistItem(wishlistProduct);
  };

  const handleAddCart = async () => {
    if (!isAuthenticated) {
      router.push(`/login?redirect=${encodeURIComponent(pathname)}`);
      return;
    }

    if (isAddingCart || isBuyingNow) {
      return;
    }

    try {
      setIsAddingCart(true);

      await addCartItem({
        productId: product.id,
        quantity,
      });

      alert("장바구니에 상품을 담았습니다.");
    } catch (error) {
      alert(
        error instanceof Error
          ? error.message
          : "장바구니에 상품을 담지 못했습니다.",
      );
    } finally {
      setIsAddingCart(false);
    }
  };

  const handleBuyNow = () => {
    if (!isAuthenticated) {
      router.push(`/login?redirect=${encodeURIComponent(pathname)}`);
      return;
    }

    if (isAddingCart || isBuyingNow) {
      return;
    }

    try {
      setIsBuyingNow(true);

      /*
       * 바로 구매는 장바구니에 저장하지 않습니다.
       *
       * 주문 기능 구현 시 /order 페이지에서
       * productId + quantity를 이용해 주문 상품을 조회합니다.
       */
      router.push(`/order?productId=${product.id}&quantity=${quantity}`);
    } finally {
      setIsBuyingNow(false);
    }
  };

  const isActionDisabled =
    isAddingCart || isBuyingNow || product.stockQuantity <= 0;

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
            disabled={isActionDisabled || quantity <= 1}
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
            disabled={isActionDisabled || quantity >= product.stockQuantity}
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
          disabled={!wishlistHydrated || isAddingCart || isBuyingNow}
          onClick={handleToggleWishlist}
        >
          <span aria-hidden="true">{isWishlisted ? "♥" : "♡"}</span>

          <span>찜</span>
        </button>

        <button
          type="button"
          className="product-detail-cart-button"
          onClick={() => void handleAddCart()}
          disabled={isActionDisabled}
        >
          {isAddingCart ? "담는 중..." : "장바구니"}
        </button>

        <button
          type="button"
          className="product-detail-gift-button"
          onClick={handleBuyNow}
          disabled={isActionDisabled}
        >
          {isBuyingNow ? "이동 중..." : "바로 구매"}
        </button>
      </div>
    </div>
  );
}
