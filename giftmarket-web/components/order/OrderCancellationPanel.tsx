"use client";

import { useMemo, useRef, useState } from "react";
import { createOrderCancellation } from "@/lib/order-api";
import type { BuyerSellerOrder, OrderCancellation, OrderCancellationStatus } from "@/types/order";

interface Props {
  orderId: number;
  sellerOrder: BuyerSellerOrder;
  cancellations: OrderCancellation[];
  onChanged: () => Promise<void>;
}

const STATUS_LABELS: Record<OrderCancellationStatus, string> = {
  REQUESTED: "취소 요청 확인 중", PROCESSING: "취소 처리 중", COMPLETED: "취소 완료",
  REJECTED: "취소 요청 거절", FAILED: "취소 처리 실패",
};

export default function OrderCancellationPanel({ orderId, sellerOrder, cancellations, onChanged }: Props) {
  const [isOpen, setIsOpen] = useState(false);
  const [selected, setSelected] = useState<Record<number, number>>({});
  const [reason, setReason] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [message, setMessage] = useState("");
  const requestKeyRef = useRef<string | null>(null);
  const cancellableItems = useMemo(
    () => sellerOrder.items.filter((item) => item.availableCancellationQuantity > 0),
    [sellerOrder.items],
  );
  const canCreate = ["PAID", "PREPARING"].includes(sellerOrder.status) && cancellableItems.length > 0;

  const toggle = (itemId: number) => setSelected((current) => {
    if (!current[itemId]) return { ...current, [itemId]: 1 };
    const next = { ...current };
    delete next[itemId];
    return next;
  });

  const changeQuantity = (itemId: number, quantity: number, maximum: number) => {
    setSelected((current) => ({ ...current, [itemId]: Math.min(maximum, Math.max(1, quantity)) }));
  };

  const submit = async () => {
    const items = Object.entries(selected).map(([orderItemId, quantity]) => ({ orderItemId: Number(orderItemId), quantity }));
    const normalizedReason = reason.trim();
    if (isSubmitting || items.length === 0 || !normalizedReason) return;
    requestKeyRef.current ??= crypto.randomUUID();
    try {
      setIsSubmitting(true);
      setMessage("");
      const result = await createOrderCancellation(orderId, {
        clientRequestKey: requestKeyRef.current,
        sellerOrderId: sellerOrder.sellerOrderId,
        reason: normalizedReason,
        items,
      });
      setMessage(result.status === "COMPLETED" ? "취소가 완료되었습니다."
        : result.status === "REQUESTED" ? "판매자 확인이 필요한 취소 요청입니다."
          : result.status === "PROCESSING" ? "취소 처리 결과를 확인 중입니다."
            : result.status === "FAILED" ? "취소 처리에 실패했습니다. 다시 시도해주세요."
              : "취소 요청이 거절되었습니다.");
      requestKeyRef.current = null;
      setSelected({});
      setReason("");
      setIsOpen(false);
      await onChanged();
    } catch {
      setMessage("취소 요청을 처리하지 못했습니다. 최신 주문 상태를 확인해주세요.");
      await onChanged();
    } finally {
      setIsSubmitting(false);
    }
  };

  return <div className="order-cancellation-area">
    {cancellations.length > 0 && <div className="order-cancellation-history">
      {cancellations.map((cancellation) => <div key={cancellation.cancellationId} className="order-cancellation-status-row">
        <span className={`order-cancellation-status order-cancellation-status-${cancellation.status.toLowerCase()}`}>{STATUS_LABELS[cancellation.status]}</span>
        <span>{cancellation.items.reduce((sum, item) => sum + item.requestedQuantity, 0)}개 · {cancellation.reason}</span>
        {cancellation.status === "REJECTED" && cancellation.rejectedReason && <small>{cancellation.rejectedReason}</small>}
      </div>)}
    </div>}
    {message && <p className="order-cancellation-message" role="status">{message}</p>}
    {canCreate && !isOpen && <button type="button" className="order-cancellation-open-button" onClick={() => setIsOpen(true)}>
      {sellerOrder.status === "PAID" ? "상품 취소" : "취소 요청"}
    </button>}
    {canCreate && isOpen && <div className="order-cancellation-form">
      <div className="order-cancellation-select-list">{cancellableItems.map((item) => {
        const quantity = selected[item.id];
        return <div key={item.id} className="order-cancellation-select-item">
          <label><input type="checkbox" checked={Boolean(quantity)} onChange={() => toggle(item.id)} /><span>{item.productName}</span></label>
          <span className="order-cancellation-available">취소 가능 {item.availableCancellationQuantity}개</span>
          {quantity && <div className="order-cancellation-stepper" aria-label={`${item.productName} 취소 수량`}>
            <button type="button" onClick={() => changeQuantity(item.id, quantity - 1, item.availableCancellationQuantity)} disabled={quantity <= 1}>−</button>
            <output>{quantity}</output>
            <button type="button" onClick={() => changeQuantity(item.id, quantity + 1, item.availableCancellationQuantity)} disabled={quantity >= item.availableCancellationQuantity}>+</button>
          </div>}
        </div>;
      })}</div>
      <label className="order-cancellation-reason"><span>취소 사유</span>
        <textarea value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} rows={3} placeholder="취소 사유를 입력해주세요." />
        <small>{reason.length}/500</small>
      </label>
      <div className="order-cancellation-actions">
        <button type="button" className="order-cancellation-close-button" onClick={() => setIsOpen(false)} disabled={isSubmitting}>닫기</button>
        <button type="button" className="order-cancellation-submit-button" onClick={() => void submit()} disabled={isSubmitting || Object.keys(selected).length === 0 || !reason.trim()}>
          {isSubmitting ? "처리 중..." : sellerOrder.status === "PAID" ? "선택 상품 취소" : "취소 요청"}
        </button>
      </div>
    </div>}
  </div>;
}
