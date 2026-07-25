import type { OrderPayment } from "@/types/order";

interface OrderDetailSummaryProps {
  payment: OrderPayment;
}

function formatPrice(price: number) {
  return `${price.toLocaleString("ko-KR")}원`;
}

function formatDateTime(date: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(date));
}

export default function OrderDetailSummary({
  payment,
}: OrderDetailSummaryProps) {
  return (
    <section className="order-detail-section">
      <h2 className="order-detail-section-title">결제 정보</h2>

      <dl className="order-detail-payment-list">
        <div className="order-detail-payment-row">
          <dt>상품 금액</dt>
          <dd>{formatPrice(payment.productAmount)}</dd>
        </div>

        <div className="order-detail-payment-row">
          <dt>배송비</dt>
          <dd>{formatPrice(payment.deliveryFee)}</dd>
        </div>

        <div className="order-detail-payment-row">
          <dt>할인 금액</dt>
          <dd>- {formatPrice(payment.discountAmount)}</dd>
        </div>

        <div className="order-detail-payment-row order-detail-payment-total">
          <dt>총 결제 금액</dt>
          <dd>{formatPrice(payment.totalAmount)}</dd>
        </div>
      </dl>

      <div className="order-detail-payment-method">
        <p>
          <span>결제 수단</span>
          <strong>{payment.paymentMethod}</strong>
        </p>

        <p>
          <span>결제 일시</span>
          <strong>{formatDateTime(payment.paidAt)}</strong>
        </p>
      </div>
    </section>
  );
}
