import Image from "next/image";
import Link from "next/link";
import OrderCancellationPanel from "@/components/order/OrderCancellationPanel";
import OrderReturnPanel from "@/components/order/OrderReturnPanel";
import OrderExchangePanel from "@/components/order/OrderExchangePanel";

import { BUYER_SELLER_ORDER_STATUS_LABELS } from "@/lib/order-status";
import type { BuyerSellerOrder, OrderCancellation } from "@/types/order";
import type { ReturnRequest } from "@/types/return";
import type { ExchangeRequest } from "@/types/exchange";
import { resolveImageUrl } from "@/utils/image-url";
import { confirmPurchase } from "@/lib/order-api";
import { useState } from "react";

interface OrderDetailSellerGroupsProps {
  sellerOrders: BuyerSellerOrder[];
  orderId: number;
  cancellations: OrderCancellation[];
  returns: ReturnRequest[];
  returnsLoading: boolean;
  returnsError: string;
  exchanges: ExchangeRequest[];
  exchangesLoading: boolean;
  exchangesError: string;
  userId: number;
  collectionAddress: { recipientName: string; phone: string; postalCode: string; address: string; addressDetail: string | null };
  onChanged: () => Promise<void>;
}

function formatPrice(price: number) {
  return `${price.toLocaleString("ko-KR")}원`;
}

export default function OrderDetailSellerGroups({
  sellerOrders,
  orderId,
  cancellations,
  returns,
  returnsLoading,
  returnsError,
  exchanges,
  exchangesLoading,
  exchangesError,
  userId,
  collectionAddress,
  onChanged,
}: OrderDetailSellerGroupsProps) {
  const [confirmingItemId, setConfirmingItemId] = useState<number | null>(null);
  const [confirmationError, setConfirmationError] = useState("");

  const handleConfirm = async (itemId: number, quantity: number) => {
    if (!window.confirm(`구매확정 후에는 해당 ${quantity}개 상품의 취소·반품·교환을 신청할 수 없습니다.\n구매확정하시겠습니까?`)) return;
    try {
      setConfirmingItemId(itemId);
      setConfirmationError("");
      await confirmPurchase(orderId, itemId);
      await onChanged();
    } catch (error) {
      setConfirmationError(error instanceof Error ? error.message : "구매확정을 처리하지 못했습니다.");
    } finally {
      setConfirmingItemId(null);
    }
  };
  return (
    <div className="order-detail-seller-groups">
      {confirmationError && <p className="order-detail-confirmation-error">{confirmationError}</p>}
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
                          {item.canceledQuantity === item.quantity ? (
                            <span className="order-detail-product-cancelled-badge">취소완료</span>
                          ) : (
                            <>취소 {item.canceledQuantity}개 · 남은 {item.quantity - item.canceledQuantity}개</>
                          )}
                        </p>
                      )}
                      <strong className="order-detail-product-price">
                        {formatPrice(item.totalPrice)}
                      </strong>
                      {item.confirmedQuantity > 0 && (
                        <p className="order-detail-confirmed-label">구매확정 {item.confirmedQuantity}개</p>
                      )}
                      {item.confirmableQuantity > 0 && (
                        <div className="order-detail-confirmation-action">
                          <span>구매확정 가능 {item.confirmableQuantity}개</span>
                          <button type="button" disabled={confirmingItemId !== null}
                            onClick={() => void handleConfirm(item.id, item.confirmableQuantity)}>
                            {confirmingItemId === item.id ? "처리 중..." : "구매확정"}
                          </button>
                        </div>
                      )}
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
            <OrderReturnPanel
              orderId={orderId}
              sellerOrder={sellerOrder}
              returns={returns.filter((value) => value.sellerOrderId === sellerOrder.sellerOrderId)}
              exchanges={exchanges.filter((value) => value.sellerOrderId === sellerOrder.sellerOrderId)}
              isLoading={returnsLoading}
              loadError={returnsError}
              collectionAddress={collectionAddress}
              onChanged={onChanged}
            />
            <OrderExchangePanel
              orderId={orderId}
              sellerOrder={sellerOrder}
              exchanges={exchanges.filter((value) => value.sellerOrderId === sellerOrder.sellerOrderId)}
              returns={returns.filter((value) => value.sellerOrderId === sellerOrder.sellerOrderId)}
              isLoading={exchangesLoading}
              loadError={exchangesError}
              defaultAddress={collectionAddress}
              userId={userId}
              onChanged={onChanged}
            />
          </section>
        );
      })}
    </div>
  );
}
