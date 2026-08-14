import Image from "next/image";

import type { OrderProductItem } from "@/types/order";
import { resolveImageUrl } from "@/utils/image-url";

interface OrderProductListProps {
  items: OrderProductItem[];
}

export default function OrderProductList({ items }: OrderProductListProps) {
  return (
    <section className="order-section">
      <div className="order-section-header">
        <h2 className="order-section-title">주문 상품</h2>

        <span className="order-section-count">총 {items.length}개</span>
      </div>

      <div className="order-product-list">
        {items.map((item) => {
          const imageUrl = resolveImageUrl(item.representativeImageKey);

          return (
            <article key={item.key} className="order-product-item">
              <div className="order-product-image-wrapper">
                {imageUrl ? (
                  <Image
                    className="order-product-image"
                    src={imageUrl}
                    alt={item.productName}
                    fill
                    sizes="96px"
                  />
                ) : (
                  <div className="order-product-image-empty">이미지 없음</div>
                )}
              </div>

              <div className="order-product-info">
                <p className="order-product-brand">
                  {item.brandName ?? item.storeName}
                </p>

                <h3 className="order-product-name">{item.productName}</h3>

                {item.optionText && (
                  <p className="order-product-options">
                    {item.optionText}
                  </p>
                )}

                <p className="order-product-quantity">수량 {item.quantity}개</p>

                <p className="order-product-shipping">
                  {item.freeShipping
                    ? "무료배송"
                    : `배송비 ${item.shippingFee.toLocaleString("ko-KR")}원`}
                </p>
              </div>

              <strong className="order-product-price">
                {(item.price * item.quantity).toLocaleString("ko-KR")}원
              </strong>
            </article>
          );
        })}
      </div>
    </section>
  );
}
