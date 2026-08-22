"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import {
  approveSellerReturnRequest,
  getSellerReturnRequest,
  inspectSellerReturn,
  receiveSellerReturn,
  rejectSellerReturnRequest,
  startSellerReturnCollection,
} from "@/lib/seller-return-api";
import { useAuthStore } from "@/stores/auth-store";
import {
  RETURN_INSPECTION_LABELS,
  RETURN_REASON_LABELS,
  RETURN_RESPONSIBILITY_LABELS,
  RETURN_SHIPMENT_STATUS_LABELS,
  RETURN_STATUS_LABELS,
  type ReturnInspectionResult,
  type ReturnRequest,
  type ReturnResponsibility,
} from "@/types/return";

type ActionMode = "approve" | "reject" | "collect" | "receive" | "inspect" | null;
const formatDate = (value: string | null) => value ? new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(value)) : "-";
const formatPrice = (value: number) => `${new Intl.NumberFormat("ko-KR").format(value)}원`;

export default function SellerReturnDetailPage() {
  const params = useParams<{ returnRequestId: string }>();
  const router = useRouter();
  const returnRequestId = Number(params.returnRequestId);
  const initialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const [request, setRequest] = useState<ReturnRequest | null>(null);
  const [loading, setLoading] = useState(true);
  const [processing, setProcessing] = useState(false);
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");
  const [mode, setMode] = useState<ActionMode>(null);
  const [responsibility, setResponsibility] = useState<ReturnResponsibility | "">("");
  const [rejectReason, setRejectReason] = useState("");
  const [shippingCompany, setShippingCompany] = useState("");
  const [trackingNumber, setTrackingNumber] = useState("");
  const [inspections, setInspections] = useState<Record<number, ReturnInspectionResult>>({});

  const loadRequest = useCallback(async (quiet = false) => {
    await Promise.resolve();
    if (!Number.isInteger(returnRequestId) || returnRequestId <= 0) { setError("올바른 반품 요청 정보가 아닙니다."); setLoading(false); return; }
    try { if (!quiet) setLoading(true); setError(""); setRequest(await getSellerReturnRequest(returnRequestId)); }
    catch { setError("반품 요청을 불러오지 못했습니다. 접근 권한이나 요청 상태를 확인해주세요."); }
    finally { if (!quiet) setLoading(false); }
  }, [returnRequestId]);

  useEffect(() => {
    if (!initialized) return;
    if (!isAuthenticated || !user) { router.replace("/login"); return; }
    if (user.role !== "SELLER" && user.role !== "ADMIN") { router.replace("/seller"); return; }
    const requestId = window.setTimeout(() => void loadRequest(), 0);
    return () => window.clearTimeout(requestId);
  }, [initialized, isAuthenticated, loadRequest, router, user]);

  const closeMode = () => { setMode(null); setActionError(""); };
  const completeAction = async (action: () => Promise<ReturnRequest>) => {
    if (processing) return;
    try {
      setProcessing(true); setActionError("");
      await action();
      setRequest(await getSellerReturnRequest(returnRequestId));
      setMode(null); setResponsibility(""); setRejectReason(""); setShippingCompany(""); setTrackingNumber(""); setInspections({});
    } catch (actionFailure) {
      const message = actionFailure instanceof Error ? actionFailure.message : "";
      setActionError(message.includes("로그인") ? message : message || "반품 상태가 변경되었거나 요청을 처리하지 못했습니다.");
      try { setRequest(await getSellerReturnRequest(returnRequestId)); } catch { /* 현재 상세를 유지한다. */ }
    } finally { setProcessing(false); }
  };

  const approve = () => {
    if (!request || request.status !== "REQUESTED") return;
    if (request.reasonType === "OTHER" && !responsibility) { setActionError("기타 반품 사유의 귀책 주체를 선택해주세요."); return; }
    void completeAction(() => approveSellerReturnRequest(returnRequestId, request.reasonType === "OTHER" ? responsibility || null : null));
  };
  const reject = () => {
    const reason = rejectReason.trim();
    if (!reason) { setActionError("반품 거절 사유를 입력해주세요."); return; }
    void completeAction(() => rejectSellerReturnRequest(returnRequestId, reason));
  };
  const collect = () => {
    const company = shippingCompany.trim(); const tracking = trackingNumber.trim();
    if (!company || !tracking) { setActionError("택배사와 송장번호를 모두 입력해주세요."); return; }
    void completeAction(() => startSellerReturnCollection(returnRequestId, company, tracking));
  };
  const inspect = () => {
    if (!request || request.items.some((item) => !inspections[item.orderItemId])) { setActionError("모든 반품 상품의 검수 결과를 선택해주세요."); return; }
    void completeAction(() => inspectSellerReturn(returnRequestId, { items: request.items.map((item) => ({ orderItemId: item.orderItemId, inspectionResult: inspections[item.orderItemId] })) }));
  };

  if (!initialized || !isAuthenticated || !user || loading) return <div className="seller-orders-auth-loading">반품 요청을 확인하고 있습니다.</div>;
  if (error || !request) return <main className="seller-orders-page"><div className="common-inner seller-orders-container"><div className="seller-orders-state seller-orders-state-error"><p>{error || "반품 요청을 확인할 수 없습니다."}</p><button type="button" onClick={() => void loadRequest()}>다시 시도</button><Link href="/seller/orders/returns">목록으로</Link></div></div></main>;

  const timeline = [
    ["요청", request.requestedAt], ["승인", request.approvedAt], ["회수 시작", request.collectingAt],
    ["입고", request.receivedAt], ["검수", request.inspectedAt], ["환불 시작", request.refundingAt], ["완료", request.completedAt],
  ].filter((entry): entry is [string, string] => Boolean(entry[1]));

  return <main className="seller-orders-page seller-returns-page"><div className="common-inner seller-orders-container seller-order-detail-container">
    <header className="seller-order-detail-header"><div><p>RETURN DETAIL</p><h1>반품 요청 상세</h1><span>주문 ID #{request.orderId} · 요청 #{request.returnRequestId}</span></div><Link href="/seller/orders/returns">목록으로</Link></header>
    <section className="seller-order-detail-summary seller-return-summary"><div><span>요청 상태</span><strong className={`seller-return-status seller-return-status-${request.status.toLowerCase()}`}>{RETURN_STATUS_LABELS[request.status]}</strong></div><div><span>요청일시</span><strong>{formatDate(request.requestedAt)}</strong></div><div><span>귀책</span><strong>{request.responsibility ? RETURN_RESPONSIBILITY_LABELS[request.responsibility] : "귀책 확인 전"}</strong></div></section>
    {request.status === "REFUNDING" && <p className="seller-return-state-message processing">환불 처리 중입니다. 추가 작업 없이 Backend의 최신 처리 결과를 확인해주세요.</p>}
    {request.status === "COMPLETED" && <p className="seller-return-state-message success">반품과 환불 처리가 완료되었습니다.</p>}
    {request.status === "REJECTED" && <p className="seller-return-state-message rejected">거절된 반품 요청입니다.{request.rejectedReason && ` 사유: ${request.rejectedReason}`}</p>}
    {request.status === "FAILED" && <p className="seller-return-state-message failed">반품 처리 중 문제가 발생했습니다. 관리자 확인이 필요한 상태입니다.</p>}

    <section className="seller-order-detail-section"><h2>반품 요청 정보</h2><dl className="seller-order-detail-info-list seller-return-info-list"><div><dt>주문</dt><dd><Link href={`/seller/orders/${request.sellerOrderId}`}>주문 ID #{request.orderId} 상세보기</Link></dd></div><div><dt>반품 사유</dt><dd>{RETURN_REASON_LABELS[request.reasonType]} · {request.reason}</dd></div><div><dt>귀책</dt><dd>{request.responsibility ? RETURN_RESPONSIBILITY_LABELS[request.responsibility] : "귀책 확인 전"}</dd></div><div><dt>회수지</dt><dd>{request.collectionRecipientName} · {request.collectionPhone}<br />({request.collectionPostalCode}) {request.collectionAddress} {request.collectionAddressDetail}</dd></div>{request.rejectedReason && <div><dt>거절 사유</dt><dd>{request.rejectedReason}</dd></div>}</dl>{timeline.length > 0 && <div className="seller-return-timeline">{timeline.map(([label, date]) => <div key={label}><span>{label}</span><strong>{formatDate(date)}</strong></div>)}</div>}</section>

    {request.images.length > 0 && <section className="seller-order-detail-section"><h2>첨부 이미지</h2><div className="seller-return-images">{request.images.map((image, index) => <a key={image.imageId} href={image.url} target="_blank" rel="noreferrer">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={image.url} alt={`반품 증빙 이미지 ${index + 1}`} />
    </a>)}</div></section>}

    <section className="seller-order-detail-section"><h2>반품 상품</h2><div className="seller-return-items">{request.items.map((item) => <article key={item.orderItemId} className="seller-return-item"><div><strong>{item.productName}</strong>{item.optionSnapshot && <span>{item.optionSnapshot}</span>}</div><dl><div><dt>요청 수량</dt><dd>{item.quantity}개</dd></div><div><dt>반품 완료 누적</dt><dd>{item.returnedQuantity}개</dd></div><div><dt>검수 결과</dt><dd>{item.inspectionResult ? RETURN_INSPECTION_LABELS[item.inspectionResult] : "검수 전"}</dd></div><div><dt>재입고 수량</dt><dd>{item.restockedQuantity}개</dd></div></dl></article>)}</div></section>

    <div className="seller-return-detail-columns"><section className="seller-order-detail-section"><h2>반품 회수 배송</h2>{request.collectionShipment ? <dl className="seller-order-detail-info-list seller-return-info-list"><div><dt>택배사</dt><dd>{request.collectionShipment.shippingCompany ?? "-"}</dd></div><div><dt>송장번호</dt><dd>{request.collectionShipment.trackingNumber ?? "-"}</dd></div><div><dt>회수 상태</dt><dd>{RETURN_SHIPMENT_STATUS_LABELS[request.collectionShipment.status]}</dd></div><div><dt>회수 시작</dt><dd>{formatDate(request.collectionShipment.shippedAt)}</dd></div><div><dt>회수 완료</dt><dd>{formatDate(request.collectionShipment.deliveredAt)}</dd></div></dl> : <p className="seller-order-action-notice">아직 반품 회수 배송이 등록되지 않았습니다.</p>}</section>
    <section className="seller-order-detail-section"><h2>환불 정보</h2>{request.refundAmount === null ? <p className="seller-order-action-notice">환불금액 계산 전입니다.</p> : <dl className="seller-return-refund"><div><dt>상품 환불금액</dt><dd>{formatPrice(request.productRefundAmount ?? 0)}</dd></div><div><dt>원 배송비 환불</dt><dd>+ {formatPrice(request.originalShippingRefundAmount ?? 0)}</dd></div><div><dt>반품 배송비</dt><dd>- {formatPrice(request.returnShippingCharge ?? 0)}</dd></div><div><dt>최종 환불금액</dt><dd>{formatPrice(request.refundAmount)}</dd></div></dl>}</section></div>

    <section className="seller-order-detail-section seller-order-action-section seller-return-action-section"><h2>반품 처리</h2>
      {request.status === "REQUESTED" && mode === null && <div className="seller-return-action-buttons"><button type="button" disabled={processing} onClick={() => setMode("approve")}>반품 승인</button><button type="button" className="danger-secondary" disabled={processing} onClick={() => setMode("reject")}>반품 거절</button></div>}
      {request.status === "REQUESTED" && mode === "approve" && <div className="seller-return-action-form"><strong>반품 요청을 승인합니다.</strong>{request.reasonType === "OTHER" && <label><span>귀책 주체</span><select value={responsibility} onChange={(event) => setResponsibility(event.target.value as ReturnResponsibility)} disabled={processing}><option value="">선택해주세요</option><option value="BUYER">구매자 귀책</option><option value="SELLER">판매자 귀책</option></select></label>}<p>승인 후 회수 배송정보를 등록할 수 있습니다.</p><div><button type="button" className="secondary" disabled={processing} onClick={closeMode}>돌아가기</button><button type="button" disabled={processing} onClick={approve}>{processing ? "승인 처리 중..." : "승인 확정"}</button></div></div>}
      {request.status === "REQUESTED" && mode === "reject" && <div className="seller-return-action-form"><label><span>거절 사유 <small>{rejectReason.length}/500</small></span><textarea value={rejectReason} maxLength={500} rows={4} disabled={processing} placeholder="구매자에게 전달할 거절 사유를 입력해주세요." onChange={(event) => setRejectReason(event.target.value)} /></label><div><button type="button" className="secondary" disabled={processing} onClick={closeMode}>돌아가기</button><button type="button" className="danger" disabled={processing || !rejectReason.trim()} onClick={reject}>{processing ? "거절 처리 중..." : "거절 확정"}</button></div></div>}
      {request.status === "APPROVED" && mode === null && <div className="seller-return-action-buttons"><button type="button" disabled={processing} onClick={() => setMode("collect")}>회수 시작</button></div>}
      {request.status === "APPROVED" && mode === "collect" && <div className="seller-return-action-form"><label><span>택배사</span><input value={shippingCompany} maxLength={100} disabled={processing} onChange={(event) => setShippingCompany(event.target.value)} /></label><label><span>송장번호</span><input value={trackingNumber} maxLength={100} disabled={processing} onChange={(event) => setTrackingNumber(event.target.value)} /></label><div><button type="button" className="secondary" disabled={processing} onClick={closeMode}>돌아가기</button><button type="button" disabled={processing || !shippingCompany.trim() || !trackingNumber.trim()} onClick={collect}>{processing ? "회수 등록 중..." : "회수 시작"}</button></div></div>}
      {request.status === "COLLECTING" && mode === null && <div className="seller-return-action-buttons"><button type="button" disabled={processing} onClick={() => setMode("receive")}>입고 처리</button></div>}
      {request.status === "COLLECTING" && mode === "receive" && <div className="seller-return-action-form"><strong>반품 상품이 실제로 도착했나요?</strong><p>입고 처리하면 회수 배송이 완료되고 상품 검수를 진행할 수 있습니다.</p><div><button type="button" className="secondary" disabled={processing} onClick={closeMode}>돌아가기</button><button type="button" disabled={processing} onClick={() => void completeAction(() => receiveSellerReturn(returnRequestId))}>{processing ? "입고 처리 중..." : "입고 확정"}</button></div></div>}
      {request.status === "RECEIVED" && mode === null && <div className="seller-return-action-buttons"><button type="button" disabled={processing} onClick={() => setMode("inspect")}>상품 검수</button></div>}
      {request.status === "RECEIVED" && mode === "inspect" && <div className="seller-return-inspection"><p>모든 상품의 검수 결과를 선택해야 합니다.</p>{request.items.map((item) => <fieldset key={item.orderItemId}><legend>{item.productName}{item.optionSnapshot && <small>{item.optionSnapshot}</small>}</legend><label><input type="radio" name={`inspection-${item.orderItemId}`} checked={inspections[item.orderItemId] === "RESTOCKABLE"} disabled={processing} onChange={() => setInspections((current) => ({ ...current, [item.orderItemId]: "RESTOCKABLE" }))} />재입고 가능</label><label><input type="radio" name={`inspection-${item.orderItemId}`} checked={inspections[item.orderItemId] === "NON_RESTOCKABLE"} disabled={processing} onChange={() => setInspections((current) => ({ ...current, [item.orderItemId]: "NON_RESTOCKABLE" }))} />재입고 불가</label></fieldset>)}<div><button type="button" className="secondary" disabled={processing} onClick={closeMode}>돌아가기</button><button type="button" disabled={processing || request.items.some((item) => !inspections[item.orderItemId])} onClick={inspect}>{processing ? "검수 처리 중..." : "검수 완료 및 환불 진행"}</button></div></div>}
      {!["REQUESTED", "APPROVED", "COLLECTING", "RECEIVED"].includes(request.status) && <p className="seller-order-action-notice">{request.status === "COMPLETED" ? "반품 처리가 완료되었습니다." : request.status === "FAILED" ? "관리자 확인이 필요한 상태입니다." : `${RETURN_STATUS_LABELS[request.status]} 상태이며 판매자 추가 작업은 없습니다.`}</p>}
      {actionError && <p className="seller-order-action-error">{actionError}</p>}
    </section>
  </div></main>;
}
