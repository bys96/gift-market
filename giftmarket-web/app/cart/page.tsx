"use client";

import Image from "next/image";
import Link from "next/link";
import { useCartStore } from "@/stores/cart-store";
import { useRouter } from "next/navigation";

const DELIVERY_FEE = 3000;

export default function CartPage() {
  const router = useRouter();
  const items = useCartStore((state) => state.items);
  const removeItem = useCartStore((state) => state.removeItem);
  const increaseQuantity = useCartStore((state) => state.increaseQuantity);
  const decreaseQuantity = useCartStore((state) => state.decreaseQuantity);
  const clearCart = useCartStore((state) => state.clearCart);

  const totalProductPrice = items.reduce(
    (total, item) => total + item.price * item.quantity,
    0,
  );

  const hasPaidDeliveryItem = items.some((item) => !item.isFreeShipping);

  const deliveryFee =
    items.length > 0 && hasPaidDeliveryItem ? DELIVERY_FEE : 0;

  const totalPrice = totalProductPrice + deliveryFee;

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

        <button type="button" className="cart-clear-button" onClick={clearCart}>
          전체 삭제
        </button>
      </div>

      <div className="cart-layout">
        <div className="cart-item-list">
          {items.map((item) => (
            <article key={item.productId} className="cart-item">
              <Link
                href={`/products/${item.productId}`}
                className="cart-item-image-wrapper"
              >
                <Image
                  src={item.imageUrl}
                  alt={item.name}
                  fill
                  sizes="140px"
                  className="cart-item-image"
                />
              </Link>

              <div className="cart-item-content">
                <Link
                  href={`/products/${item.productId}`}
                  className="cart-item-name-link"
                >
                  <p className="cart-item-brand">{item.brandName}</p>

                  <h2 className="cart-item-name">{item.name}</h2>
                </Link>

                <strong className="cart-item-price">
                  {item.price.toLocaleString()}원
                </strong>

                <div className="cart-item-bottom">
                  <div className="cart-item-quantity">
                    <button
                      type="button"
                      className="cart-item-quantity-button"
                      onClick={() => decreaseQuantity(item.productId)}
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
                      onClick={() => increaseQuantity(item.productId)}
                      disabled={item.quantity >= item.stockQuantity}
                      aria-label="수량 증가"
                    >
                      +
                    </button>
                  </div>

                  <button
                    type="button"
                    className="cart-item-remove-button"
                    onClick={() => removeItem(item.productId)}
                  >
                    삭제
                  </button>
                </div>
              </div>
            </article>
          ))}
        </div>

        <aside className="cart-summary">
          <h2 className="cart-summary-title">결제 금액</h2>

          <div className="cart-summary-row">
            <span>총 상품 금액</span>
            <strong>{totalProductPrice.toLocaleString()}원</strong>
          </div>

          <div className="cart-summary-row">
            <span>배송비</span>
            <strong>
              {deliveryFee === 0 ? "무료" : `${deliveryFee.toLocaleString()}원`}
            </strong>
          </div>

          <div className="cart-summary-total">
            <span>최종 결제 금액</span>
            <strong>{totalPrice.toLocaleString()}원</strong>
          </div>

          <button
            type="button"
            className="cart-order-button"
            onClick={() => router.push("/order")}
          >
            주문하기
          </button>
        </aside>
      </div>
    </section>
  );
}
