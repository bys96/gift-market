"use client";

import Script from "next/script";
import { useMemo, useRef, useState } from "react";
import { createReturnRequest } from "@/lib/return-api";
import type { BuyerSellerOrder } from "@/types/order";
import type { ReturnReasonType, ReturnRequest, ReturnRequestStatus, ReturnShipmentStatus } from "@/types/return";
import { formatKoreanPhoneNumber } from "@/utils/phone";

interface Props {
  orderId: number;
  sellerOrder: BuyerSellerOrder;
  returns: ReturnRequest[];
  isLoading: boolean;
  loadError: string;
  collectionAddress: { recipientName: string; phone: string; postalCode: string; address: string; addressDetail: string | null };
  onChanged: () => Promise<void>;
}

interface PostcodeData { zonecode: string; address: string; roadAddress: string; jibunAddress: string }
type PostcodeConstructor = new (options: { oncomplete: (data: PostcodeData) => void }) => { open: () => void };
type PostcodeWindow = Window & { daum?: { Postcode?: PostcodeConstructor } };

const STATUS_LABELS: Record<ReturnRequestStatus, string> = {
  REQUESTED: "반품 요청", APPROVED: "반품 승인", COLLECTING: "상품 회수 중", RECEIVED: "판매자 입고 완료",
  INSPECTED: "검수 완료", REFUNDING: "환불 처리 중", COMPLETED: "반품 완료", REJECTED: "반품 거절",
  CANCELED: "반품 철회", FAILED: "반품 처리 실패",
};
const REASON_LABELS: Record<ReturnReasonType, string> = {
  CHANGE_OF_MIND: "단순 변심", OPTION_MISTAKE: "옵션 선택 실수", DEFECTIVE: "상품 불량", WRONG_ITEM: "오배송",
  DAMAGED: "배송 중 파손", DESCRIPTION_MISMATCH: "상품 설명과 다름", OTHER: "기타",
};
const SHIPMENT_LABELS: Record<ReturnShipmentStatus, string> = {
  READY: "회수 준비", SHIPPED: "회수 중", DELIVERED: "회수 완료", CANCELED: "회수 취소",
};
const HOLDING_STATUSES = new Set<ReturnRequestStatus>(["REQUESTED", "APPROVED", "COLLECTING", "RECEIVED", "INSPECTED", "REFUNDING"]);
const formatPrice = (value: number) => `${value.toLocaleString("ko-KR")}원`;

export default function OrderReturnPanel({ orderId, sellerOrder, returns, isLoading, loadError, collectionAddress, onChanged }: Props) {
  const [isOpen, setIsOpen] = useState(false);
  const [selected, setSelected] = useState<Record<number, number>>({});
  const [reasonType, setReasonType] = useState<ReturnReasonType>("CHANGE_OF_MIND");
  const [reason, setReason] = useState("");
  const [recipientName, setRecipientName] = useState(collectionAddress.recipientName);
  const [phone, setPhone] = useState(collectionAddress.phone);
  const [postalCode, setPostalCode] = useState(collectionAddress.postalCode);
  const [address, setAddress] = useState(collectionAddress.address);
  const [addressDetail, setAddressDetail] = useState(collectionAddress.addressDetail ?? "");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [message, setMessage] = useState("");
  const [formError, setFormError] = useState("");
  const requestKeyRef = useRef<string | null>(null);

  const availableByItem = useMemo(() => {
    const result = new Map<number, number>();
    for (const item of sellerOrder.items) {
      const returned = Math.max(0, ...returns.flatMap((request) => request.items.filter((value) => value.orderItemId === item.id).map((value) => value.returnedQuantity)));
      const held = returns.filter((request) => HOLDING_STATUSES.has(request.status)).flatMap((request) => request.items).filter((value) => value.orderItemId === item.id).reduce((sum, value) => sum + value.quantity, 0);
      result.set(item.id, Math.max(0, item.quantity - item.canceledQuantity - returned - held));
    }
    return result;
  }, [returns, sellerOrder.items]);
  const returnableItems = sellerOrder.items.filter((item) => (availableByItem.get(item.id) ?? 0) > 0);
  const canCreate = sellerOrder.status === "DELIVERED" && returnableItems.length > 0;
  const changed = () => { requestKeyRef.current = null; setFormError(""); };
  const toggle = (itemId: number) => { changed(); setSelected((current) => { const next = { ...current }; if (next[itemId]) delete next[itemId]; else next[itemId] = 1; return next; }); };
  const changeQuantity = (itemId: number, quantity: number) => { changed(); const maximum = availableByItem.get(itemId) ?? 1; setSelected((current) => ({ ...current, [itemId]: Math.min(maximum, Math.max(1, quantity)) })); };

  const searchAddress = () => {
    if (isSubmitting) return;
    const Postcode = (window as PostcodeWindow).daum?.Postcode;
    if (!Postcode) { setFormError("주소 검색 서비스를 불러오지 못했습니다. 잠시 후 다시 시도해주세요."); return; }
    new Postcode({ oncomplete: (data) => { changed(); setPostalCode(data.zonecode); setAddress(data.roadAddress || data.jibunAddress || data.address); setAddressDetail(""); } }).open();
  };

  const submit = async () => {
    const items = Object.entries(selected).map(([orderItemId, quantity]) => ({ orderItemId: Number(orderItemId), quantity }));
    if (isSubmitting) return;
    if (!items.length) { setFormError("반품할 상품을 한 개 이상 선택해주세요."); return; }
    if (!reason.trim()) { setFormError("상세 반품 사유를 입력해주세요."); return; }
    if (!recipientName.trim() || !phone.trim() || !postalCode.trim() || !address.trim()) { setFormError("회수 주소의 필수 정보를 모두 입력해주세요."); return; }
    if (!/^0\d{1,2}-\d{3,4}-\d{4}$/.test(phone)) { setFormError("올바른 연락처를 입력해주세요."); return; }
    requestKeyRef.current ??= crypto.randomUUID();
    try {
      setIsSubmitting(true); setFormError(""); setMessage("");
      await createReturnRequest(orderId, sellerOrder.sellerOrderId, {
        clientRequestKey: requestKeyRef.current, reasonType, reason: reason.trim(), collectionRecipientName: recipientName.trim(),
        collectionPhone: phone.trim(), collectionPostalCode: postalCode.trim(), collectionAddress: address.trim(),
        collectionAddressDetail: addressDetail.trim() || null, items,
      });
      requestKeyRef.current = null; setSelected({}); setReason(""); setIsOpen(false);
      setMessage("반품 요청이 접수되었습니다.");
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "반품 요청을 처리하지 못했습니다.");
      try { await onChanged(); } catch { /* 기존 화면을 유지하고 원래 요청 오류를 표시한다. */ }
      setIsSubmitting(false);
      return;
    }
    try { await onChanged(); } catch { setMessage("반품 요청은 접수되었지만 최신 내역을 불러오지 못했습니다."); }
    finally { setIsSubmitting(false); }
  };

  return <div className="order-return-area">
    <Script src="https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js" strategy="afterInteractive" />
    <div className="order-return-heading"><strong>반품</strong>{sellerOrder.status === "DELIVERED" && <span>배송 완료 상품은 반품을 신청할 수 있습니다.</span>}</div>
    {isLoading && <p className="order-return-notice">반품 내역을 불러오고 있습니다.</p>}
    {loadError && <p className="order-return-error" role="alert">{loadError}</p>}
    {!isLoading && returns.map((request) => <article key={request.returnRequestId} className="order-return-history">
      <header><span className={`order-return-status order-return-status-${request.status.toLowerCase()}`}>{STATUS_LABELS[request.status]}</span><span>{REASON_LABELS[request.reasonType]}</span></header>
      <p className="order-return-reason">{request.reason}</p>
      <ul>{request.items.map((item) => <li key={item.orderItemId}><span><strong>{item.productName}</strong>{item.optionSnapshot && <small>{item.optionSnapshot}</small>}</span><span>신청 {item.quantity}개{item.returnedQuantity > 0 ? ` · 반품 완료 누적 ${item.returnedQuantity}개` : ""}</span>{item.inspectionResult && <em>검수 완료 · {item.inspectionResult === "RESTOCKABLE" ? "재판매 가능" : "재판매 불가"}</em>}</li>)}</ul>
      {request.status === "REJECTED" && request.rejectedReason && <p className="order-return-rejected">거절 사유: {request.rejectedReason}</p>}
      {request.status === "FAILED" && <p className="order-return-rejected">반품 처리 중 문제가 발생했습니다. 고객센터로 문의해주세요.</p>}
      <div className="order-return-detail-grid">
        <div><h4>회수지</h4><p>{request.collectionRecipientName} · {request.collectionPhone}<br />({request.collectionPostalCode}) {request.collectionAddress} {request.collectionAddressDetail}</p></div>
        <div><h4>반품 회수 배송</h4>{request.collectionShipment ? <p>{request.collectionShipment.shippingCompany ?? "택배사 확인 중"} · {request.collectionShipment.trackingNumber ?? "송장번호 미등록"}<br />{SHIPMENT_LABELS[request.collectionShipment.status]}</p> : <p>아직 회수 송장이 등록되지 않았습니다.</p>}</div>
      </div>
      <div className="order-return-refund"><h4>환불 정보</h4>{request.refundAmount === null ? <p>환불금액 계산 전</p> : <dl><div><dt>상품 환불금액</dt><dd>{formatPrice(request.productRefundAmount ?? 0)}</dd></div><div><dt>원 배송비 환불</dt><dd>{formatPrice(request.originalShippingRefundAmount ?? 0)}</dd></div><div><dt>반품 배송비</dt><dd>-{formatPrice(request.returnShippingCharge ?? 0)}</dd></div><div className="order-return-refund-total"><dt>최종 환불금액</dt><dd>{formatPrice(request.refundAmount)}</dd></div></dl>}</div>
    </article>)}
    {message && <p className="order-return-success" role="status">{message}</p>}
    {canCreate && !isOpen && <button type="button" className="order-return-open-button" onClick={() => { setIsOpen(true); setMessage(""); }}>반품 신청</button>}
    {sellerOrder.status === "DELIVERED" && !isLoading && !canCreate && <p className="order-return-notice">현재 추가로 반품 신청할 수 있는 수량이 없습니다.</p>}
    {canCreate && isOpen && <div className="order-return-form">
      <h3>반품 상품 선택</h3>
      <div className="order-return-select-list">{returnableItems.map((item) => { const quantity = selected[item.id]; const maximum = availableByItem.get(item.id) ?? 0; return <div key={item.id} className="order-return-select-item"><label><input type="checkbox" checked={Boolean(quantity)} onChange={() => toggle(item.id)} /><span><strong>{item.productName}</strong>{item.optionSnapshot && <small>{item.optionSnapshot}</small>}</span></label><span>반품 가능 {maximum}개</span>{quantity && <div className="order-return-stepper"><button type="button" onClick={() => changeQuantity(item.id, quantity - 1)} disabled={quantity <= 1}>−</button><output>{quantity}</output><button type="button" onClick={() => changeQuantity(item.id, quantity + 1)} disabled={quantity >= maximum}>+</button></div>}</div>; })}</div>
      <div className="order-return-fields"><label><span>반품 사유</span><select value={reasonType} onChange={(event) => { changed(); setReasonType(event.target.value as ReturnReasonType); }} disabled={isSubmitting}>{Object.entries(REASON_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><label className="order-return-full"><span>상세 사유</span><textarea value={reason} onChange={(event) => { changed(); setReason(event.target.value); }} maxLength={500} rows={3} placeholder="상품 상태와 반품 사유를 자세히 입력해주세요." disabled={isSubmitting} /><small>{reason.length}/500</small></label></div>
      <h3>회수 주소</h3><div className="order-return-fields"><label><span>회수 수령인</span><input value={recipientName} onChange={(event) => { changed(); setRecipientName(event.target.value); }} maxLength={100} disabled={isSubmitting} /></label><label><span>연락처</span><input value={phone} onChange={(event) => { changed(); setPhone(formatKoreanPhoneNumber(event.target.value)); }} maxLength={13} disabled={isSubmitting} /></label><label className="order-return-full"><span>주소</span><div className="order-return-address-row"><input value={postalCode} readOnly placeholder="우편번호" /><button type="button" onClick={searchAddress} disabled={isSubmitting}>주소 검색</button></div><input value={address} readOnly placeholder="기본 주소" /><input value={addressDetail} onChange={(event) => { changed(); setAddressDetail(event.target.value); }} maxLength={255} placeholder="상세주소" disabled={isSubmitting} /></label></div>
      {formError && <p className="order-return-error" role="alert">{formError}</p>}
      <div className="order-return-actions"><button type="button" className="order-return-close-button" onClick={() => setIsOpen(false)} disabled={isSubmitting}>닫기</button><button type="button" className="order-return-submit-button" onClick={() => void submit()} disabled={isSubmitting}>{isSubmitting ? "반품 요청 중..." : "반품 요청"}</button></div>
    </div>}
  </div>;
}
