import type { OrderCustomer, OrderRecipient } from "@/types/order";

interface OrderDetailInfoProps {
  customer: OrderCustomer;
  recipient: OrderRecipient;
}

export default function OrderDetailInfo({
  customer,
  recipient,
}: OrderDetailInfoProps) {
  const fullAddress = [
    `(${recipient.zipCode})`,
    recipient.address,
    recipient.addressDetail,
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <div className="order-detail-info-grid">
      <section className="order-detail-section">
        <h2 className="order-detail-section-title">주문자 정보</h2>

        <dl className="order-detail-info-list">
          <div className="order-detail-info-row">
            <dt>이름</dt>
            <dd>{customer.name}</dd>
          </div>

          <div className="order-detail-info-row">
            <dt>이메일</dt>
            <dd>{customer.email}</dd>
          </div>

          <div className="order-detail-info-row">
            <dt>연락처</dt>
            <dd>{customer.phoneNumber}</dd>
          </div>
        </dl>
      </section>

      <section className="order-detail-section">
        <h2 className="order-detail-section-title">받는 사람 정보</h2>

        <dl className="order-detail-info-list">
          <div className="order-detail-info-row">
            <dt>이름</dt>
            <dd>{recipient.name}</dd>
          </div>

          <div className="order-detail-info-row">
            <dt>연락처</dt>
            <dd>{recipient.phoneNumber}</dd>
          </div>

          <div className="order-detail-info-row">
            <dt>배송지</dt>
            <dd>{fullAddress}</dd>
          </div>

          <div className="order-detail-info-row">
            <dt>배송 메시지</dt>
            <dd>{recipient.deliveryMessage || "배송 메시지 없음"}</dd>
          </div>
        </dl>
      </section>
    </div>
  );
}
