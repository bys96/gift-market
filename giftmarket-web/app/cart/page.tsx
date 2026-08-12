"use client";

import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";

import { useAuthStore } from "@/stores/auth-store";
import { useCartStore } from "@/stores/cart-store";
import type { CartItem, CartItemAvailability } from "@/types/cart";
import { resolveImageUrl } from "@/utils/image-url";

const AVAILABILITY_LABEL: Record<CartItemAvailability, string> = {
  AVAILABLE: "",
  SOLD_OUT: "품절된 상품입니다.",
  SALE_STOPPED: "현재 판매가 중단된 상품입니다.",
  OPTION_INACTIVE: "현재 판매하지 않는 옵션입니다.",
  INSUFFICIENT_STOCK: "재고가 변경되었습니다.",
};

function getAvailabilityMessage(item: CartItem): string {
  if (item.availability === "INSUFFICIENT_STOCK") {
    return `현재 구매 가능한 수량은 ${item.stockQuantity}개입니다. 수량을 조정해주세요.`;
  }

  return AVAILABILITY_LABEL[item.availability];
}

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
  const removeSelectedItems = useCartStore(
    (state) => state.removeSelectedItems,
  );
  const increaseQuantity = useCartStore((state) => state.increaseQuantity);
  const decreaseQuantity = useCartStore((state) => state.decreaseQuantity);

  const [selectedCartItemIds, setSelectedCartItemIds] = useState<number[]>([]);

  const selectionInitializedRef = useRef(false);
  const previousPurchasableCartItemIdsRef = useRef<number[]>([]);

  useEffect(() => {
    if (!authInitialized) {
      return;
    }

    if (!isAuthenticated) {
      router.replace("/");
      return;
    }

    void loadCart();
  }, [authInitialized, isAuthenticated, loadCart, router]);

  const allCartItemIds = useMemo(
    () => items.map((item) => item.cartItemId),
    [items],
  );

  const purchasableItems = useMemo(
    () => items.filter((item) => item.purchasable),
    [items],
  );

  const purchasableCartItemIds = useMemo(
    () => purchasableItems.map((item) => item.cartItemId),
    [purchasableItems],
  );

  useEffect(() => {
    if (!initialized) {
      return;
    }

    if (!selectionInitializedRef.current) {
      selectionInitializedRef.current = true;
      previousPurchasableCartItemIdsRef.current = purchasableCartItemIds;

      setSelectedCartItemIds(allCartItemIds);
      return;
    }

    const previousPurchasableIds = previousPurchasableCartItemIdsRef.current;

    const newlyPurchasableIds = purchasableCartItemIds.filter(
      (cartItemId) => !previousPurchasableIds.includes(cartItemId),
    );

    setSelectedCartItemIds((current) => {
      const existingSelectedIds = current.filter((cartItemId) =>
        allCartItemIds.includes(cartItemId),
      );

      return Array.from(
        new Set([...existingSelectedIds, ...newlyPurchasableIds]),
      );
    });

    previousPurchasableCartItemIdsRef.current = purchasableCartItemIds;
  }, [initialized, allCartItemIds, purchasableCartItemIds]);

  const selectedItems = useMemo(
    () => items.filter((item) => selectedCartItemIds.includes(item.cartItemId)),
    [items, selectedCartItemIds],
  );

  const selectedPurchasableItems = useMemo(
    () => selectedItems.filter((item) => item.purchasable),
    [selectedItems],
  );

  const selectedProductPrice = useMemo(
    () =>
      selectedPurchasableItems.reduce(
        (total, item) => total + item.price * item.quantity,
        0,
      ),
    [selectedPurchasableItems],
  );

  const selectedShippingFee = useMemo(
    () =>
      selectedPurchasableItems.reduce(
        (total, item) => total + (item.freeShipping ? 0 : item.shippingFee),
        0,
      ),
    [selectedPurchasableItems],
  );

  const selectedTotalPrice = selectedProductPrice + selectedShippingFee;

  const isAllSelected =
    items.length > 0 && selectedCartItemIds.length === items.length;

  const handleToggleAll = () => {
    if (isAllSelected) {
      setSelectedCartItemIds([]);
      return;
    }

    setSelectedCartItemIds(allCartItemIds);
  };

  const handleToggleItem = (item: CartItem) => {
    setSelectedCartItemIds((current) => {
      if (current.includes(item.cartItemId)) {
        return current.filter((selectedId) => selectedId !== item.cartItemId);
      }

      return [...current, item.cartItemId];
    });
  };

  const handleRemoveItem = async (cartItemId: number) => {
    try {
      await removeItem(cartItemId);

      setSelectedCartItemIds((current) =>
        current.filter(
          (selectedCartItemId) => selectedCartItemId !== cartItemId,
        ),
      );
    } catch (error) {
      console.error(error);
    }
  };

  const handleRemoveSelectedItems = async () => {
    if (selectedCartItemIds.length === 0) {
      return;
    }

    const confirmed = window.confirm(
      `선택한 상품 ${selectedCartItemIds.length}개를 장바구니에서 삭제하시겠습니까?`,
    );

    if (!confirmed) {
      return;
    }

    try {
      await removeSelectedItems(selectedCartItemIds);
      setSelectedCartItemIds([]);
    } catch (error) {
      console.error(error);
    }
  };

  const handleIncreaseQuantity = async (item: CartItem) => {
    if (!item.purchasable) {
      return;
    }

    try {
      await increaseQuantity(item.cartItemId);
    } catch (error) {
      console.error(error);
    }
  };

  const handleDecreaseQuantity = async (item: CartItem) => {
    if (!item.purchasable && item.availability !== "INSUFFICIENT_STOCK") {
      return;
    }

    try {
      await decreaseQuantity(item.cartItemId);
    } catch (error) {
      console.error(error);
    }
  };

  const handleOrder = () => {
    if (selectedPurchasableItems.length === 0) {
      alert("주문할 수 있는 상품을 선택해주세요.");
      return;
    }

    const params = new URLSearchParams();

    params.set(
      "cartItemIds",
      selectedPurchasableItems.map((item) => item.cartItemId).join(","),
    );

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
      </div>

      {errorMessage && <p className="cart-error-message">{errorMessage}</p>}

      <div className="cart-selection-bar">
        <label className="cart-checkbox-label">
          <input
            type="checkbox"
            className="cart-checkbox"
            checked={isAllSelected}
            onChange={handleToggleAll}
            disabled={isLoading || items.length === 0}
          />

          <span>
            전체 선택 ({selectedCartItemIds.length}/{items.length})
          </span>
        </label>

        <button
          type="button"
          className="cart-selected-delete-button"
          onClick={() => void handleRemoveSelectedItems()}
          disabled={isLoading || selectedCartItemIds.length === 0}
        >
          선택 삭제
        </button>
      </div>

      <div className="cart-layout">
        <div className="cart-item-list">
          {items.map((item) => {
            const imageUrl = resolveImageUrl(item.representativeImageKey);

            const isSelected = selectedCartItemIds.includes(item.cartItemId);

            const availabilityMessage = getAvailabilityMessage(item);

            const canDecreaseQuantity =
              item.purchasable || item.availability === "INSUFFICIENT_STOCK";

            return (
              <article
                key={item.cartItemId}
                className={[
                  "cart-item",
                  isSelected ? "cart-item-selected" : "",
                  !item.purchasable ? "cart-item-unavailable" : "",
                ]
                  .filter(Boolean)
                  .join(" ")}
              >
                <div className="cart-item-main">
                  <div className="cart-item-checkbox-area">
                    <input
                      type="checkbox"
                      className="cart-checkbox"
                      aria-label={`${item.productName} 선택`}
                      checked={isSelected}
                      disabled={isLoading}
                      onChange={() => handleToggleItem(item)}
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
                        sizes="112px"
                        className="cart-item-image"
                      />
                    ) : (
                      <div className="cart-item-image-empty">이미지 없음</div>
                    )}

                    {!item.purchasable && (
                      <span className="cart-item-image-status">
                        {item.availability === "SOLD_OUT"
                          ? "품절"
                          : "구매 불가"}
                      </span>
                    )}
                  </Link>

                  <div className="cart-item-info">
                    <div className="cart-item-info-header">
                      <Link
                        href={`/products/${item.productId}`}
                        className="cart-item-name-link"
                      >
                        <p className="cart-item-brand">
                          {item.brandName ?? item.storeName}
                        </p>
                      </Link>

                      <button
                        type="button"
                        className="cart-item-remove-button"
                        onClick={() => void handleRemoveItem(item.cartItemId)}
                        disabled={isLoading}
                      >
                        삭제
                      </button>
                    </div>

                    <Link
                      href={`/products/${item.productId}`}
                      className="cart-item-name-link"
                    >
                      <h2 className="cart-item-name">{item.productName}</h2>
                    </Link>

                    <div className="cart-item-option-line">
                      {item.options.length > 0 ? (
                        <span>
                          {item.options
                            .map(
                              (option) =>
                                `${option.optionGroupName}: ${option.optionValue}`,
                            )
                            .join(" / ")}
                        </span>
                      ) : (
                        <span aria-hidden="true">&nbsp;</span>
                      )}
                    </div>

                    <div className="cart-item-status-line">
                      {!item.purchasable && availabilityMessage ? (
                        <span
                          className={[
                            "cart-item-availability",
                            `cart-item-availability-${item.availability.toLowerCase()}`,
                          ].join(" ")}
                        >
                          {availabilityMessage}
                        </span>
                      ) : (
                        <span aria-hidden="true">&nbsp;</span>
                      )}
                    </div>
                  </div>
                </div>

                <div className="cart-item-footer">
                  <div className="cart-item-shipping">
                    {item.freeShipping
                      ? "무료배송"
                      : `배송비 ${item.shippingFee.toLocaleString("ko-KR")}원`}
                  </div>

                  <strong className="cart-item-price">
                    {item.price.toLocaleString("ko-KR")}원
                  </strong>

                  <div className="cart-item-quantity">
                    <button
                      type="button"
                      className="cart-item-quantity-button"
                      onClick={() => void handleDecreaseQuantity(item)}
                      disabled={
                        isLoading || !canDecreaseQuantity || item.quantity <= 1
                      }
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
                      onClick={() => void handleIncreaseQuantity(item)}
                      disabled={
                        isLoading ||
                        !item.purchasable ||
                        item.quantity >= item.stockQuantity
                      }
                      aria-label="수량 증가"
                    >
                      +
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
            주문 가능 상품 {selectedPurchasableItems.length}개
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
            disabled={isLoading || selectedPurchasableItems.length === 0}
            onClick={handleOrder}
          >
            {selectedPurchasableItems.length > 0
              ? `${selectedPurchasableItems.length}개 상품 주문하기`
              : "주문할 상품을 선택해주세요"}
          </button>
        </aside>
      </div>
    </section>
  );
}
