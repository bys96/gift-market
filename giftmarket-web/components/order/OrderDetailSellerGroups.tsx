import Image from "next/image";
import Link from "next/link";
import OrderCancellationPanel from "@/components/order/OrderCancellationPanel";

import { BUYER_SELLER_ORDER_STATUS_LABELS } from "@/lib/order-status";
import type { BuyerSellerOrder, OrderCancellation } from "@/types/order";
import { resolveImageUrl } from "@/utils/image-url";

interface OrderDetailSellerGroupsProps {
  sellerOrders: BuyerSellerOrder[];
  orderId: number;
  cancellations: OrderCancellation[];
  onChanged: () => Promise<void>;
}

function formatPrice(price: number) {
  return `${price.toLocaleString("ko-KR")}원`;
}

export default function OrderDetailSellerGroups({
  sellerOrders,
  orderId,
  cancellations,
  onChanged,
}: OrderDetailSellerGroupsProps) {
  return (
    <div className="order-detail-seller-groups">
      {sellerOrders.map((sellerOrder) => {
        const showsTracking =
          ["SHIPPED", "DELIVERED"].includes(sellerOrder.status) &&
          sellerOrder.shippingCompany &&
          sellerOrder.trackingNumber;

        return (
          <section
            key={sellerOrder.sellerOrderId}
            className="order-detail-section order-detail-seller-group"
          >
            <header className="order-detail-seller-header">
              <div>
                <span>판매자</span>
                <h2>{sellerOrder.sellerName}</h2>
              </div>
              <strong
                className={`order-detail-delivery-badge order-detail-delivery-${sellerOrder.status.toLowerCase()}`}
              >
                {BUYER_SELLER_ORDER_STATUS_LABELS[sellerOrder.status]}
              </strong>
            </header>

            <div className="order-detail-product-list">
              {sellerOrder.items.map((item) => {
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
                      {item.canceledQuantity > 0 && (
                        <p className="order-detail-product-cancellation-quantity">
                          취소 {item.canceledQuantity}개 · 남은 수량 {item.quantity - item.canceledQuantity}개
                          {item.availableCancellationQuantity === 0 && item.canceledQuantity === item.quantity && (
                            <span className="order-detail-product-cancelled-badge">취소완료</span>
                          )}
                        </p>
                      )}
                      <strong className="order-detail-product-price">
                        {formatPrice(item.totalPrice)}
                      </strong>
                    </div>
                  </article>
                );
              })}
            </div>

            <div className="order-detail-seller-shipping">
              <span>배송 상태</span>
              <strong>
                {BUYER_SELLER_ORDER_STATUS_LABELS[sellerOrder.status]}
              </strong>
              {showsTracking && (
                <dl>
                  <div>
                    <dt>배송사</dt>
                    <dd>{sellerOrder.shippingCompany}</dd>
                  </div>
                  <div>
                    <dt>운송장번호</dt>
                    <dd>{sellerOrder.trackingNumber}</dd>
                  </div>
                </dl>
              )}
            </div>

            <OrderCancellationPanel
              orderId={orderId}
              sellerOrder={sellerOrder}
              cancellations={cancellations.filter((value) => value.sellerOrderId === sellerOrder.sellerOrderId)}
              onChanged={onChanged}
            />
          </section>
        );
      })}
    </div>
  );
}
