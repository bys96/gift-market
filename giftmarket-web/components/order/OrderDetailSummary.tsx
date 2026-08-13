import type { OrderDetail } from "@/types/order";

interface OrderDetailSummaryProps {
  order: OrderDetail;
}

function formatPrice(price: number) {
  return `${price.toLocaleString("ko-KR")}원`;
}

export default function OrderDetailSummary({ order }: OrderDetailSummaryProps) {
  return (
    <section className="order-detail-section">
      <h2 className="order-detail-section-title">주문 금액</h2>

      <dl className="order-detail-payment-list">
        <div className="order-detail-payment-row">
          <dt>상품 금액</dt>

          <dd>{formatPrice(order.totalProductAmount)}</dd>
        </div>

        <div className="order-detail-payment-row">
          <dt>배송비</dt>

          <dd>
            {order.totalShippingFee === 0
              ? "무료"
              : formatPrice(order.totalShippingFee)}
          </dd>
        </div>

        <div className="order-detail-payment-row order-detail-payment-total">
          <dt>총 주문 금액</dt>

          <dd>{formatPrice(order.totalAmount)}</dd>
        </div>
      </dl>
    </section>
  );
}
