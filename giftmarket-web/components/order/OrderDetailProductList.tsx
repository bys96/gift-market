import Image from "next/image";
import Link from "next/link";

import type { OrderHistoryItem } from "@/types/order";
import { resolveImageUrl } from "@/utils/image-url";

interface OrderDetailProductListProps {
  items: OrderHistoryItem[];
}

function formatPrice(price: number) {
  return `${price.toLocaleString("ko-KR")}원`;
}

export default function OrderDetailProductList({
  items,
}: OrderDetailProductListProps) {
  return (
    <section className="order-detail-section">
      <h2 className="order-detail-section-title">주문 상품</h2>

      <div className="order-detail-product-list">
        {items.map((item) => {
          const imageUrl = resolveImageUrl(item.representativeImageKey);

          return (
            <article key={item.id} className="order-detail-product-item">
              <Link
                href={`/products/${item.productId}`}
                className="order-detail-product-image-wrapper"
              >
                {imageUrl ? (
                  <Image
                    src={imageUrl}
                    alt={item.productName}
                    fill
                    sizes="96px"
                    className="order-detail-product-image"
                  />
                ) : (
                  <div className="order-detail-product-image-empty">
                    이미지 없음
                  </div>
                )}
              </Link>

              <div className="order-detail-product-info">
                <p className="order-detail-product-brand">
                  {item.brandName ?? "브랜드 정보 없음"}
                </p>

                <Link
                  href={`/products/${item.productId}`}
                  className="order-detail-product-name"
                >
                  {item.productName}
                </Link>

                {item.optionSnapshot && (
                  <p className="order-detail-product-option">
                    {item.optionSnapshot}
                  </p>
                )}

                <p className="order-detail-product-quantity">
                  {formatPrice(item.unitPrice)} · 수량 {item.quantity}개
                </p>

                <strong className="order-detail-product-price">
                  {formatPrice(item.totalPrice)}
                </strong>
              </div>
            </article>
          );
        })}
      </div>
    </section>
  );
}
