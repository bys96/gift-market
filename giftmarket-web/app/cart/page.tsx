"use client";

import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";

import { useAuthStore } from "@/stores/auth-store";
import { useCartStore } from "@/stores/cart-store";
import { resolveImageUrl } from "@/utils/image-url";

export default function CartPage() {
  const router = useRouter();

  const authInitialized = useAuthStore((state) => state.initialized);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const items = useCartStore((state) => state.items);
  const initialized = useCartStore((state) => state.initialized);
  const isLoading = useCartStore((state) => state.isLoading);
  const errorMessage = useCartStore((state) => state.errorMessage);

  const loadCart = useCartStore((state) => state.loadCart);
  const removeItem = useCartStore((state) => state.removeItem);
  const increaseQuantity = useCartStore((state) => state.increaseQuantity);
  const decreaseQuantity = useCartStore((state) => state.decreaseQuantity);
  const clearCart = useCartStore((state) => state.clearCart);

  const [selectedCartItemIds, setSelectedCartItemIds] = useState<number[]>([]);
  const selectionInitializedRef = useRef(false);

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

  useEffect(() => {
    if (!initialized) {
      return;
    }

    const currentCartItemIds = items.map((item) => item.cartItemId);

    if (!selectionInitializedRef.current) {
      selectionInitializedRef.current = true;
      setSelectedCartItemIds(currentCartItemIds);
      return;
    }

    setSelectedCartItemIds((current) =>
      current.filter((cartItemId) => currentCartItemIds.includes(cartItemId)),
    );
  }, [initialized, items]);

  const selectedItems = useMemo(
    () => items.filter((item) => selectedCartItemIds.includes(item.cartItemId)),
    [items, selectedCartItemIds],
  );

  const selectedProductPrice = useMemo(
    () =>
      selectedItems.reduce(
        (total, item) => total + item.price * item.quantity,
        0,
      ),
    [selectedItems],
  );

  const selectedShippingFee = useMemo(
    () =>
      selectedItems.reduce(
        (total, item) => total + (item.freeShipping ? 0 : item.shippingFee),
        0,
      ),
    [selectedItems],
  );

  const selectedTotalPrice = selectedProductPrice + selectedShippingFee;

  const isAllSelected =
    items.length > 0 && selectedCartItemIds.length === items.length;

  const handleToggleAll = () => {
    if (isAllSelected) {
      setSelectedCartItemIds([]);
      return;
    }

    setSelectedCartItemIds(items.map((item) => item.cartItemId));
  };

  const handleToggleItem = (cartItemId: number) => {
    setSelectedCartItemIds((current) => {
      if (current.includes(cartItemId)) {
        return current.filter((selectedId) => selectedId !== cartItemId);
      }

      return [...current, cartItemId];
    });
  };

  const handleClearCart = async () => {
    try {
      await clearCart();
      setSelectedCartItemIds([]);
    } catch (error) {
      console.error(error);
    }
  };

  const handleRemoveItem = async (cartItemId: number) => {
    try {
      await removeItem(cartItemId);
    } catch (error) {
      console.error(error);
    }
  };

  const handleIncreaseQuantity = async (cartItemId: number) => {
    try {
      await increaseQuantity(cartItemId);
    } catch (error) {
      console.error(error);
    }
  };

  const handleDecreaseQuantity = async (cartItemId: number) => {
    try {
      await decreaseQuantity(cartItemId);
    } catch (error) {
      console.error(error);
    }
  };

  const handleOrder = () => {
    if (selectedCartItemIds.length === 0) {
      alert("주문할 상품을 선택해주세요.");
      return;
    }

    const params = new URLSearchParams();

    params.set("cartItemIds", selectedCartItemIds.join(","));

    router.push(`/order?${params.toString()}`);
  };

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

      <div className="cart-selection-bar">
        <label className="cart-checkbox-label">
          <input
            type="checkbox"
            className="cart-checkbox"
            checked={isAllSelected}
            onChange={handleToggleAll}
            disabled={isLoading}
          />

          <span>
            전체 선택 ({selectedCartItemIds.length}/{items.length})
          </span>
        </label>
      </div>

      <div className="cart-layout">
        <div className="cart-item-list">
          {items.map((item) => {
            const imageUrl = resolveImageUrl(item.representativeImageKey);

            const isSelected = selectedCartItemIds.includes(item.cartItemId);

            return (
              <article
                key={item.cartItemId}
                className={["cart-item", isSelected ? "cart-item-selected" : ""]
                  .filter(Boolean)
                  .join(" ")}
              >
                <div className="cart-item-checkbox-area">
                  <input
                    type="checkbox"
                    className="cart-checkbox"
                    aria-label={`${item.productName} 선택`}
                    checked={isSelected}
                    disabled={isLoading}
                    onChange={() => handleToggleItem(item.cartItemId)}
                  />
                </div>

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

                    {item.options.length > 0 && (
                      <p className="cart-item-options">
                        {item.options
                          .map(
                            (option) =>
                              `${option.optionGroupName}: ${option.optionValue}`,
                          )
                          .join(" / ")}
                      </p>
                    )}
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
                          void handleDecreaseQuantity(item.cartItemId)
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
                          void handleIncreaseQuantity(item.cartItemId)
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
                      onClick={() => void handleRemoveItem(item.cartItemId)}
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
          <h2 className="cart-summary-title">주문 금액</h2>

          <div className="cart-summary-selected">
            선택 상품 {selectedItems.length}개
          </div>

          <div className="cart-summary-row">
            <span>총 상품 금액</span>

            <strong>{selectedProductPrice.toLocaleString("ko-KR")}원</strong>
          </div>

          <div className="cart-summary-row">
            <span>배송비</span>

            <strong>
              {selectedShippingFee === 0
                ? "무료"
                : `${selectedShippingFee.toLocaleString("ko-KR")}원`}
            </strong>
          </div>

          <div className="cart-summary-total">
            <span>총 주문 금액</span>

            <strong>{selectedTotalPrice.toLocaleString("ko-KR")}원</strong>
          </div>

          <button
            type="button"
            className="cart-order-button"
            disabled={isLoading || selectedCartItemIds.length === 0}
            onClick={handleOrder}
          >
            {selectedCartItemIds.length > 0
              ? `${selectedCartItemIds.length}개 상품 주문하기`
              : "상품을 선택해주세요"}
          </button>
        </aside>
      </div>
    </section>
  );
}
