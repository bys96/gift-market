import type { SellerOrderItem } from "@/types/seller-order";

interface SellerOrderItemProcessingProps {
  item: Pick<SellerOrderItem, "quantity" | "canceledQuantity" | "remainingQuantity">;
}

export default function SellerOrderItemProcessing({ item }: SellerOrderItemProcessingProps) {
  if (item.quantity > 0 && item.canceledQuantity === item.quantity) {
    return <span className="seller-order-item-cancelled">취소완료</span>;
  }

  const processing = item.remainingQuantity === 0
    ? "처리완료"
    : `처리 예정 ${item.remainingQuantity}개`;

  return <>{item.canceledQuantity > 0 ? `취소 ${item.canceledQuantity}개 · ` : ""}{processing}</>;
}
