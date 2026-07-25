import Image from "next/image";
import Link from "next/link";
import type { OrderDetailItem } from "@/types/order";

interface OrderDetailProductListProps {
  items: OrderDetailItem[];
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
        {items.map((item) => (
          <article key={item.id} className="order-detail-product-item">
            <Link
              href={`/products/${item.productId}`}
              className="order-detail-product-image-wrapper"
            >
              <Image
                src={item.productImageUrl}
                alt={item.productName}
                fill
                sizes="96px"
                className="order-detail-product-image"
              />
            </Link>

            <div className="order-detail-product-info">
              <Link
                href={`/products/${item.productId}`}
                className="order-detail-product-name"
              >
                {item.productName}
              </Link>

              <p className="order-detail-product-quantity">
                수량 {item.quantity}개
              </p>

              <strong className="order-detail-product-price">
                {formatPrice(item.price * item.quantity)}
              </strong>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
