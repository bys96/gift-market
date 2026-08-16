interface OrderSummaryProps {
  productAmount: number;
  shippingFee: number;
  totalAmount: number;
  disabled: boolean;
  submitLabel: string;
  onSubmit: () => void;
}

export default function OrderSummary({
  productAmount,
  shippingFee,
  totalAmount,
  disabled,
  submitLabel,
  onSubmit,
}: OrderSummaryProps) {
  return (
    <aside className="order-summary">
      <h2 className="order-summary-title">주문 금액</h2>

      <div className="order-summary-content">
        <div className="order-summary-row">
          <span>상품 금액</span>

          <span>{productAmount.toLocaleString("ko-KR")}원</span>
        </div>

        <div className="order-summary-row">
          <span>배송비</span>

          <span>
            {shippingFee === 0
              ? "무료"
              : `${shippingFee.toLocaleString("ko-KR")}원`}
          </span>
        </div>

        <div className="order-summary-divider" />

        <div className="order-summary-total">
          <span>총 주문 금액</span>

          <strong>{totalAmount.toLocaleString("ko-KR")}원</strong>
        </div>
      </div>

      <div className="order-summary-agreement">
        주문 상품과 배송지 정보를 확인했습니다.
      </div>

      <button
        className="order-submit-button"
        type="button"
        disabled={disabled}
        onClick={onSubmit}
      >
        {submitLabel}
      </button>
    </aside>
  );
}
