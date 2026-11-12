import { getBuyerOrderStatus } from "@/lib/buyer-order-status";
import type { OrderHistoryItem } from "@/types/order";
import type { ReturnRequest } from "@/types/return";

interface Props {
  status: string;
  label: string;
  items: OrderHistoryItem[];
  returns: ReturnRequest[];
  loading?: boolean;
  error?: boolean;
  className: string;
}

export default function BuyerOrderStatusBadge({ className, ...props }: Props) {
  const display = getBuyerOrderStatus(
    props.status, props.label, props.items, props.returns, props.loading, props.error,
  );
  return <span className={`${className} ${display.tone ? `buyer-order-status-${display.tone}` : ""}`}>{display.label}</span>;
}
