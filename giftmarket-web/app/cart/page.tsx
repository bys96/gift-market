"use client";

import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect } from "react";

import { useAuthStore } from "@/stores/auth-store";
import { useCartStore } from "@/stores/cart-store";
import { resolveImageUrl } from "@/utils/image-url";

export default function CartPage() {
  const router = useRouter();

  const authInitialized = useAuthStore((state) => state.initialized);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const items = useCartStore((state) => state.items);
  const totalProductPrice = useCartStore((state) => state.totalProductPrice);
  const totalShippingFee = useCartStore((state) => state.totalShippingFee);
  const totalPrice = useCartStore((state) => state.totalPrice);
  const initialized = useCartStore((state) => state.initialized);
  const isLoading = useCartStore((state) => state.isLoading);
  const errorMessage = useCartStore((state) => state.errorMessage);

  const loadCart = useCartStore((state) => state.loadCart);
  const removeItem = useCartStore((state) => state.removeItem);
  const increaseQuantity = useCartStore((state) => state.increaseQuantity);
  const decreaseQuantity = useCartStore((state) => state.decreaseQuantity);
  const clearCart = useCartStore((state) => state.clearCart);

  useEffect(() => {
    if (!authInitialized) {
      return;
    }

    if (!isAuthenticated) {
      router.replace("/");
      return;
    }

    if (!initialized) {
      void loadCart();
    }
  }, [authInitialized, isAuthenticated, initialized, loadCart, router]);

  if (!authInitialized || (isAuthenticated && !initialized)) {
    return (
      <section className="cart-page">
        <div className="cart-empty">
          <h1 className="cart-empty-title">장바구니를 불러오는 중입니다.</h1>
        </div>
      </section>
    );
  }

  if (!isAuthenticated) {
    return null;
  }

  if (errorMessage && items.length === 0) {
    return (
      <section className="cart-page">
        <div className="cart-empty">
          <h1 className="cart-empty-title">장바구니를 불러오지 못했습니다.</h1>

          <p className="cart-empty-description">{errorMessage}</p>

          <button
            type="button"
            className="cart-empty-link"
            onClick={() => void loadCart()}
          >
            다시 시도
          </button>
        </div>
      </section>
    );
  }

  if (items.length === 0) {
    return (
      <section className="cart-page">
        <div className="cart-empty">
          <h1 className="cart-empty-title">장바구니가 비어 있습니다.</h1>

          <p className="cart-empty-description">
            원하는 상품을 장바구니에 담아보세요.
          </p>

          <Link href="/products" className="cart-empty-link">
            상품 보러 가기
          </Link>
        </div>
      </section>
    );
  }

  const handleClearCart = async () => {
    try {
      await clearCart();
    } catch (error) {
      console.error(error);
    }
  };

  const handleRemoveItem = async (productId: number) => {
    try {
      await removeItem(productId);
    } catch (error) {
      console.error(error);
    }
  };

  const handleIncreaseQuantity = async (productId: number) => {
    try {
      await increaseQuantity(productId);
    } catch (error) {
      console.error(error);
    }
  };

  const handleDecreaseQuantity = async (productId: number) => {
    try {
      await decreaseQuantity(productId);
    } catch (error) {
      console.error(error);
    }
  };

  return (
    <section className="cart-page">
      <div className="cart-header">
        <h1 className="cart-title">장바구니</h1>

        <button
          type="button"
          className="cart-clear-button"
          onClick={() => void handleClearCart()}
          disabled={isLoading}
        >
          전체 삭제
        </button>
      </div>

      {errorMessage && <p className="cart-error-message">{errorMessage}</p>}

      <div className="cart-layout">
        <div className="cart-item-list">
          {items.map((item) => {
            const imageUrl = resolveImageUrl(item.representativeImageKey);

            return (
              <article key={item.cartItemId} className="cart-item">
                <Link
                  href={`/products/${item.productId}`}
                  className="cart-item-image-wrapper"
                >
                  {imageUrl ? (
                    <Image
                      src={imageUrl}
                      alt={item.productName}
                      fill
                      sizes="140px"
                      className="cart-item-image"
                    />
                  ) : (
                    <div className="cart-item-image-empty">이미지 없음</div>
                  )}
                </Link>

                <div className="cart-item-content">
                  <Link
                    href={`/products/${item.productId}`}
                    className="cart-item-name-link"
                  >
                    <p className="cart-item-brand">
                      {item.brandName ?? item.storeName}
                    </p>

                    <h2 className="cart-item-name">{item.productName}</h2>
                  </Link>

                  <strong className="cart-item-price">
                    {item.price.toLocaleString("ko-KR")}원
                  </strong>

                  <p className="cart-item-shipping">
                    {item.freeShipping
                      ? "무료배송"
                      : `배송비 ${item.shippingFee.toLocaleString("ko-KR")}원`}
                  </p>

                  <div className="cart-item-bottom">
                    <div className="cart-item-quantity">
                      <button
                        type="button"
                        className="cart-item-quantity-button"
                        onClick={() =>
                          void handleDecreaseQuantity(item.productId)
                        }
                        disabled={isLoading}
                        aria-label="수량 감소"
                      >
                        −
                      </button>

                      <span className="cart-item-quantity-value">
                        {item.quantity}
                      </span>

                      <button
                        type="button"
                        className="cart-item-quantity-button"
                        onClick={() =>
                          void handleIncreaseQuantity(item.productId)
                        }
                        disabled={
                          isLoading || item.quantity >= item.stockQuantity
                        }
                        aria-label="수량 증가"
                      >
                        +
                      </button>
                    </div>

                    <button
                      type="button"
                      className="cart-item-remove-button"
                      onClick={() => void handleRemoveItem(item.productId)}
                      disabled={isLoading}
                    >
                      삭제
                    </button>
                  </div>
                </div>
              </article>
            );
          })}
        </div>

        <aside className="cart-summary">
          <h2 className="cart-summary-title">결제 금액</h2>

          <div className="cart-summary-row">
            <span>총 상품 금액</span>

            <strong>{totalProductPrice.toLocaleString("ko-KR")}원</strong>
          </div>

          <div className="cart-summary-row">
            <span>배송비</span>

            <strong>
              {totalShippingFee === 0
                ? "무료"
                : `${totalShippingFee.toLocaleString("ko-KR")}원`}
            </strong>
          </div>

          <div className="cart-summary-total">
            <span>최종 결제 금액</span>

            <strong>{totalPrice.toLocaleString("ko-KR")}원</strong>
          </div>

          <button
            type="button"
            className="cart-order-button"
            onClick={() => router.push("/order")}
            disabled={isLoading}
          >
            주문하기
          </button>
        </aside>
      </div>
    </section>
  );
}
