import Image from "next/image";
import type { CartItem } from "@/stores/cart-store";

interface OrderProductListProps {
  items: CartItem[];
}

export default function OrderProductList({ items }: OrderProductListProps) {
  return (
    <section className="order-section">
      <div className="order-section-header">
        <h2 className="order-section-title">주문 상품</h2>

        <span className="order-section-count">총 {items.length}개</span>
      </div>

      <div className="order-product-list">
        {items.map((item) => (
          <article key={item.productId} className="order-product-item">
            <div className="order-product-image-wrapper">
              <Image
                className="order-product-image"
                src={item.imageUrl}
                alt={item.name}
                fill
                sizes="96px"
              />
            </div>

            <div className="order-product-info">
              <p className="order-product-brand">{item.brandName}</p>

              <h3 className="order-product-name">{item.name}</h3>

              <p className="order-product-quantity">수량 {item.quantity}개</p>

              <p className="order-product-shipping">
                {item.isFreeShipping ? "무료배송" : "배송비 3,000원"}
              </p>
            </div>

            <strong className="order-product-price">
              {(item.price * item.quantity).toLocaleString()}원
            </strong>
          </article>
        ))}
      </div>
    </section>
  );
}
