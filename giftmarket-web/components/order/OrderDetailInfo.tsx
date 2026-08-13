import type { OrderDetail } from "@/types/order";

interface OrderDetailInfoProps {
  order: OrderDetail;
}

export default function OrderDetailInfo({ order }: OrderDetailInfoProps) {
  const fullAddress = [
    `(${order.postalCode})`,
    order.address,
    order.addressDetail,
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <section className="order-detail-section">
      <h2 className="order-detail-section-title">배송지 정보</h2>

      <dl className="order-detail-info-list">
        <div className="order-detail-info-row">
          <dt>받는 분</dt>
          <dd>{order.recipientName}</dd>
        </div>

        <div className="order-detail-info-row">
          <dt>연락처</dt>
          <dd>{order.recipientPhone}</dd>
        </div>

        <div className="order-detail-info-row">
          <dt>배송지</dt>
          <dd>{fullAddress}</dd>
        </div>
      </dl>
    </section>
  );
}
