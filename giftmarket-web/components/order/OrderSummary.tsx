interface OrderSummaryProps {
  productAmount: number;
  deliveryFee: number;
  totalAmount: number;
  isSubmitting: boolean;
  onSubmit: () => void;
}

export default function OrderSummary({
  productAmount,
  deliveryFee,
  totalAmount,
  isSubmitting,
  onSubmit,
}: OrderSummaryProps) {
  return (
    <aside className="order-summary">
      <h2 className="order-summary-title">결제 금액</h2>

      <div className="order-summary-content">
        <div className="order-summary-row">
          <span>상품 금액</span>
          <span>{productAmount.toLocaleString()}원</span>
        </div>

        <div className="order-summary-row">
          <span>배송비</span>
          <span>
            {deliveryFee === 0 ? "무료" : `${deliveryFee.toLocaleString()}원`}
          </span>
        </div>

        <div className="order-summary-divider" />

        <div className="order-summary-total">
          <span>총 결제 금액</span>
          <strong>{totalAmount.toLocaleString()}원</strong>
        </div>
      </div>

      <div className="order-summary-agreement">
        주문 내용을 확인했으며 결제에 동의합니다.
      </div>

      <button
        className="order-submit-button"
        type="button"
        disabled={isSubmitting}
        onClick={onSubmit}
      >
        {isSubmitting
          ? "주문 처리 중..."
          : `${totalAmount.toLocaleString()}원 주문하기`}
      </button>
    </aside>
  );
}
