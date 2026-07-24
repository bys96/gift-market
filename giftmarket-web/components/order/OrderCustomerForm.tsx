"use client";

interface OrderCustomerFormProps {
  name: string;
  email: string;
  phone: string;
  onChange: (field: "name" | "email" | "phone", value: string) => void;
}

export default function OrderCustomerForm({
  name,
  email,
  phone,
  onChange,
}: OrderCustomerFormProps) {
  return (
    <section className="order-section">
      <div className="order-section-header">
        <h2 className="order-section-title">주문자 정보</h2>
      </div>

      <div className="order-form">
        <div className="order-form-group">
          <label className="order-form-label" htmlFor="customer-name">
            이름
          </label>

          <input
            id="customer-name"
            className="order-form-input"
            type="text"
            value={name}
            onChange={(event) => onChange("name", event.target.value)}
            placeholder="주문자 이름"
            autoComplete="name"
          />
        </div>

        <div className="order-form-group">
          <label className="order-form-label" htmlFor="customer-email">
            이메일
          </label>

          <input
            id="customer-email"
            className="order-form-input"
            type="email"
            value={email}
            onChange={(event) => onChange("email", event.target.value)}
            placeholder="example@email.com"
            autoComplete="email"
          />
        </div>

        <div className="order-form-group">
          <label className="order-form-label" htmlFor="customer-phone">
            휴대폰 번호
          </label>

          <input
            id="customer-phone"
            className="order-form-input"
            type="tel"
            value={phone}
            onChange={(event) => onChange("phone", event.target.value)}
            placeholder="010-0000-0000"
            autoComplete="tel"
          />
        </div>
      </div>
    </section>
  );
}
