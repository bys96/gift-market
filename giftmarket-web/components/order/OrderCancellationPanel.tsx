"use client";

import { useMemo, useRef, useState } from "react";
import Modal from "@/components/common/modal/Modal";
import { createOrderCancellation } from "@/lib/order-api";
import type { BuyerSellerOrder, OrderCancellation, OrderCancellationStatus } from "@/types/order";

interface Props {
  orderId: number;
  sellerOrder: BuyerSellerOrder;
  cancellations: OrderCancellation[];
  onChanged: () => Promise<void>;
}

interface CancellationConfirmation {
  items: Array<{
    orderItemId: number;
    quantity: number;
    productName: string;
    optionSnapshot: string | null;
  }>;
  reason: string;
}

const STATUS_LABELS: Record<OrderCancellationStatus, string> = {
  REQUESTED: "취소 요청 확인 중", PROCESSING: "취소 처리 중", COMPLETED: "취소 완료",
  REJECTED: "취소 요청 거절", FAILED: "취소 처리 실패",
};

const REQUESTER_LABELS = {
  BUYER: "구매자 취소",
  SELLER: "판매자 취소",
} as const;

export default function OrderCancellationPanel({ orderId, sellerOrder, cancellations, onChanged }: Props) {
  const [isOpen, setIsOpen] = useState(false);
  const [selected, setSelected] = useState<Record<number, number>>({});
  const [reason, setReason] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [message, setMessage] = useState("");
  const [confirmation, setConfirmation] = useState<CancellationConfirmation | null>(null);
  const requestKeyRef = useRef<string | null>(null);
  const submittingRef = useRef(false);
  const confirmationCancelButtonRef = useRef<HTMLButtonElement>(null);
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

  const openConfirmation = () => {
    if (isSubmitting || submittingRef.current) return;

    const normalizedReason = reason.trim();
    const cancellableItemById = new Map(cancellableItems.map((item) => [item.id, item]));
    const items = Object.entries(selected).map(([orderItemId, quantity]) => {
      const item = cancellableItemById.get(Number(orderItemId));
      if (!item || !Number.isInteger(quantity) || quantity < 1 || quantity > item.availableCancellationQuantity) {
        return null;
      }

      return {
        orderItemId: item.id,
        quantity,
        productName: item.productName,
        optionSnapshot: item.optionSnapshot,
      };
    });

    if (items.length === 0 || items.some((item) => item === null) || !normalizedReason || normalizedReason.length > 500) {
      return;
    }

    setConfirmation({
      items: items.filter((item): item is NonNullable<typeof item> => item !== null),
      reason: normalizedReason,
    });
  };

  const submit = async (confirmed: CancellationConfirmation) => {
    if (isSubmitting || submittingRef.current) return;

    submittingRef.current = true;
    requestKeyRef.current ??= crypto.randomUUID();
    try {
      setIsSubmitting(true);
      setMessage("");
      const result = await createOrderCancellation(orderId, {
        clientRequestKey: requestKeyRef.current,
        sellerOrderId: sellerOrder.sellerOrderId,
        reason: confirmed.reason,
        items: confirmed.items.map(({ orderItemId, quantity }) => ({ orderItemId, quantity })),
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
      setConfirmation(null);
      await onChanged();
    } catch {
      setMessage("취소 요청을 처리하지 못했습니다. 최신 주문 상태를 확인해주세요.");
      setConfirmation(null);
      await onChanged();
    } finally {
      submittingRef.current = false;
      setIsSubmitting(false);
    }
  };

  return <div className="order-cancellation-area">
    {cancellations.length > 0 && <div className="order-cancellation-history">
      {cancellations.map((cancellation) => <div key={cancellation.cancellationId} className="order-cancellation-status-row">
        <span className={`order-cancellation-status order-cancellation-status-${cancellation.status.toLowerCase()}`}>{STATUS_LABELS[cancellation.status]}</span>
        <span className={`order-cancellation-requester order-cancellation-requester-${cancellation.requesterType.toLowerCase()}`}>{REQUESTER_LABELS[cancellation.requesterType]}</span>
        {cancellation.requesterType === "SELLER" ? (
          <span className="order-cancellation-seller-reason">판매자에 의해 취소된 주문입니다. 사유: {cancellation.reason}</span>
        ) : (
          <span>{cancellation.items.reduce((sum, item) => sum + item.requestedQuantity, 0)}개 · {cancellation.reason}</span>
        )}
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
          <label><input type="checkbox" checked={Boolean(quantity)} onChange={() => toggle(item.id)} /><span className="order-cancellation-select-product"><strong>{item.productName}</strong>{item.optionSnapshot && <small>{item.optionSnapshot}</small>}</span></label>
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
        <button type="button" className="order-cancellation-submit-button" onClick={openConfirmation} disabled={isSubmitting || Object.keys(selected).length === 0 || !reason.trim()}>
          {isSubmitting ? "처리 중..." : sellerOrder.status === "PAID" ? "선택 상품 취소" : "취소 요청"}
        </button>
      </div>
    </div>}
    {confirmation && <Modal
      overlayClassName="order-cancellation-confirm-overlay"
      contentClassName="order-cancellation-confirm-modal"
      ariaLabelledBy="order-cancellation-confirm-title"
      ariaDescribedBy="order-cancellation-confirm-description"
      initialFocusRef={confirmationCancelButtonRef}
      closeOnEscape={!isSubmitting}
      closeOnBackdrop={!isSubmitting}
      onClose={() => {
        if (!isSubmitting) setConfirmation(null);
      }}
    >
      <h2 id="order-cancellation-confirm-title">주문 취소를 요청하시겠습니까?</h2>
      <p id="order-cancellation-confirm-description">
        선택한 상품과 수량, 취소 사유를 다시 확인해주세요.
      </p>
      <ul className="order-cancellation-confirm-items">
        {confirmation.items.map((item) => <li key={item.orderItemId}>
          <span>
            <strong>{item.productName}</strong>
            {item.optionSnapshot && <small>{item.optionSnapshot}</small>}
          </span>
          <b>{item.quantity}개</b>
        </li>)}
      </ul>
      <div className="order-cancellation-confirm-reason">
        <strong>취소 사유</strong>
        <p>{confirmation.reason}</p>
      </div>
      <p className="order-cancellation-confirm-notice">
        {sellerOrder.status === "PAID"
          ? "결제가 완료된 주문은 취소 처리와 함께 실제 환불이 진행될 수 있습니다."
          : "판매자 확인이 필요한 주문은 요청 처리 결과를 주문 상세에서 확인할 수 있습니다."}
      </p>
      <div className="order-cancellation-confirm-actions">
        <button
          ref={confirmationCancelButtonRef}
          type="button"
          onClick={() => setConfirmation(null)}
          disabled={isSubmitting}
        >
          돌아가기
        </button>
        <button type="button" onClick={() => void submit(confirmation)} disabled={isSubmitting}>
          {isSubmitting ? "처리 중..." : "취소 요청"}
        </button>
      </div>
    </Modal>}
  </div>;
}
