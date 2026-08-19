"use client";

import Image from "next/image";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";

import {
  deliverSellerOrder,
  getSellerOrder,
  prepareSellerOrder,
  shipSellerOrder,
} from "@/lib/seller-order-api";
import { useAuthStore } from "@/stores/auth-store";
import {
  SELLER_ORDER_STATUS_LABEL,
  type SellerOrderDetail,
} from "@/types/seller-order";
import { resolveImageUrl } from "@/utils/image-url";

function formatPrice(value: number) {
  return `${new Intl.NumberFormat("ko-KR").format(value)}원`;
}

function formatDate(value: string | null) {
  if (!value) return "-";
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function friendlyActionError(error: unknown) {
  const message = error instanceof Error ? error.message : "";
  if (message.includes("로그인")) return message;
  return "주문 상태가 변경되었거나 요청을 처리하지 못했습니다. 최신 상태를 확인해주세요.";
}

export default function SellerOrderDetailPage() {
  const params = useParams<{ sellerOrderId: string }>();
  const router = useRouter();
  const sellerOrderId = Number(params.sellerOrderId);
  const initialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const [order, setOrder] = useState<SellerOrderDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [processing, setProcessing] = useState(false);
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");
  const [shippingCompany, setShippingCompany] = useState("");
  const [trackingNumber, setTrackingNumber] = useState("");
  const [confirmDelivery, setConfirmDelivery] = useState(false);

  const totalProductAmount = useMemo(
    () => order?.items.reduce((sum, item) => sum + item.totalPrice, 0) ?? 0,
    [order],
  );

  const displayedCancellation = order?.cancellations.find(
    (cancellation) => cancellation.status === "REQUESTED",
  ) ?? order?.cancellations.find(
    (cancellation) => cancellation.status === "PROCESSING",
  ) ?? order?.cancellations[0] ?? null;
  const requestedCancellationCount = order?.cancellations.filter(
    (cancellation) => cancellation.status === "REQUESTED",
  ).length ?? 0;

  const loadOrder = useCallback(async (quiet = false) => {
    await Promise.resolve();

    if (!Number.isInteger(sellerOrderId) || sellerOrderId <= 0) {
      setError("올바른 주문 정보가 아닙니다.");
      setLoading(false);
      return;
    }
    try {
      if (!quiet) setLoading(true);
      setError("");
      const result = await getSellerOrder(sellerOrderId);
      setOrder(result);
      setShippingCompany(result.shippingCompany ?? "");
      setTrackingNumber(result.trackingNumber ?? "");
    } catch {
      setError("주문 정보를 불러오지 못했습니다. 접근 권한이나 주문 상태를 확인해주세요.");
    } finally {
      if (!quiet) setLoading(false);
    }
  }, [sellerOrderId]);

  useEffect(() => {
    if (!initialized) return;
    if (!isAuthenticated || !user) {
      router.replace("/login");
      return;
    }
    if (user.role !== "SELLER" && user.role !== "ADMIN") {
      router.replace("/seller");
      return;
    }
    const requestId = window.setTimeout(() => void loadOrder(), 0);

    return () => window.clearTimeout(requestId);
  }, [initialized, isAuthenticated, loadOrder, router, user]);

  const runAction = async (action: () => Promise<SellerOrderDetail>) => {
    if (processing) return;
    try {
      setProcessing(true);
      setActionError("");
      const result = await action();
      setOrder(result);
      setShippingCompany(result.shippingCompany ?? "");
      setTrackingNumber(result.trackingNumber ?? "");
      setConfirmDelivery(false);
    } catch (actionFailure) {
      setActionError(friendlyActionError(actionFailure));
      await loadOrder(true);
    } finally {
      setProcessing(false);
    }
  };

  const handleShip = () => {
    const company = shippingCompany.trim();
    const tracking = trackingNumber.trim();
    if (!company || !tracking) {
      setActionError("배송사와 운송장번호를 모두 입력해주세요.");
      return;
    }
    if (company.length > 100 || tracking.length > 100) {
      setActionError("배송사와 운송장번호는 각각 100자 이내로 입력해주세요.");
      return;
    }
    void runAction(() => shipSellerOrder(sellerOrderId, {
      shippingCompany: company,
      trackingNumber: tracking,
    }));
  };

  if (!initialized || !isAuthenticated || !user || loading) {
    return <div className="seller-orders-auth-loading">주문 정보를 확인하고 있습니다.</div>;
  }

  if (error || !order) {
    return (
      <main className="seller-orders-page"><div className="common-inner seller-orders-container">
        <div className="seller-orders-state seller-orders-state-error"><p>{error || "주문 정보를 확인할 수 없습니다."}</p><button type="button" onClick={() => void loadOrder()}>다시 시도</button><Link href="/seller/orders">목록으로</Link></div>
      </div></main>
    );
  }

  return (
    <main className="seller-orders-page">
      <div className="common-inner seller-orders-container seller-order-detail-container">
        <header className="seller-order-detail-header">
          <div><p>ORDER DETAIL</p><h1>주문 상세</h1><span>{order.merchantOrderId}</span></div>
          <Link href="/seller/orders">목록으로</Link>
        </header>

        <section className="seller-order-detail-summary">
          <div><span>주문 상태</span><strong className={`seller-order-status seller-order-status-${order.status.toLowerCase()}`}>{SELLER_ORDER_STATUS_LABEL[order.status]}</strong></div>
          <div><span>주문일시</span><strong>{formatDate(order.orderedAt)}</strong></div>
          <div><span>상품 합계</span><strong>{formatPrice(totalProductAmount)}</strong></div>
        </section>

        <section className="seller-order-detail-section">
          <h2>주문 상품</h2>
          <div className="seller-order-detail-items">
            {order.items.map((item) => {
              const imageUrl = resolveImageUrl(item.representativeImageKey);
              return (
                <article key={item.orderItemId} className="seller-order-detail-item">
                  <div className="seller-order-detail-item-image">{imageUrl ? <Image src={imageUrl} alt="" fill sizes="72px" /> : <span>이미지 없음</span>}</div>
                  <div className="seller-order-detail-item-info">{item.brandName && <small>{item.brandName}</small>}<strong>{item.productName}</strong>{item.optionSnapshot && <span>{item.optionSnapshot}</span>}</div>
                  <dl><div><dt>단가</dt><dd>{formatPrice(item.unitPrice)}</dd></div><div><dt>주문수량</dt><dd>{item.quantity}개</dd></div><div><dt>취소/처리</dt><dd>{item.remainingQuantity === 0 ? <span className="seller-order-item-cancelled">취소완료</span> : item.canceledQuantity > 0 ? <>취소 {item.canceledQuantity}개 · 처리 예정 {item.remainingQuantity}개</> : <>처리 예정 {item.remainingQuantity}개</>}</dd></div><div><dt>주문금액</dt><dd><strong>{formatPrice(item.totalPrice)}</strong></dd></div></dl>
                </article>
              );
            })}
          </div>
        </section>

        {displayedCancellation && (
          <section className="seller-order-detail-section seller-order-cancellation-summary-section">
            <div>
              <h2>취소 요청</h2>
              <p>
                {displayedCancellation.status === "REQUESTED" && `취소 요청 ${requestedCancellationCount}건 확인이 필요합니다.`}
                {displayedCancellation.status === "PROCESSING" && "취소 환불 처리 결과를 확인 중입니다."}
                {displayedCancellation.status === "COMPLETED" && "취소 완료 내역이 있습니다."}
                {displayedCancellation.status === "REJECTED" && "거절한 취소 요청 내역이 있습니다."}
                {displayedCancellation.status === "FAILED" && "환불 처리에 실패한 취소 요청이 있습니다."}
              </p>
            </div>
            <Link href={`/seller/orders/cancellations/${displayedCancellation.cancellationId}`}>취소 요청 보기</Link>
          </section>
        )}

        <div className="seller-order-detail-grid">
          <section className="seller-order-detail-section">
            <h2>배송지</h2>
            <dl className="seller-order-detail-info-list">
              <div><dt>수령인</dt><dd>{order.recipientName}</dd></div>
              <div><dt>연락처</dt><dd>{order.recipientPhone}</dd></div>
              <div><dt>주소</dt><dd>({order.postalCode}) {order.address}{order.addressDetail ? ` ${order.addressDetail}` : ""}</dd></div>
            </dl>
          </section>

          <section className="seller-order-detail-section">
            <h2>배송 정보</h2>
            <dl className="seller-order-detail-info-list">
              <div><dt>배송사</dt><dd>{order.shippingCompany ?? "-"}</dd></div>
              <div><dt>운송장번호</dt><dd>{order.trackingNumber ?? "-"}</dd></div>
              <div><dt>상품준비</dt><dd>{formatDate(order.preparedAt)}</dd></div>
              <div><dt>배송시작</dt><dd>{formatDate(order.shippedAt)}</dd></div>
              <div><dt>배송완료</dt><dd>{formatDate(order.deliveredAt)}</dd></div>
            </dl>
          </section>
        </div>

        <section className="seller-order-detail-section seller-order-action-section">
          <h2>주문 처리</h2>
          {order.status === "PAID" && <div className="seller-order-action-content"><div><strong>상품 준비를 시작해주세요.</strong><p>준비를 시작하면 판매자 주문 상태가 상품준비중으로 변경됩니다.</p></div><button type="button" disabled={processing} onClick={() => void runAction(() => prepareSellerOrder(sellerOrderId))}>{processing ? "처리 중..." : "상품 준비 시작"}</button></div>}
          {order.status === "PREPARING" && <div className="seller-order-ship-form"><label>배송사<input value={shippingCompany} maxLength={100} placeholder="예: CJ대한통운" disabled={processing} onChange={(event) => setShippingCompany(event.target.value)} /></label><label>운송장번호<input value={trackingNumber} maxLength={100} placeholder="운송장번호 입력" disabled={processing} onChange={(event) => setTrackingNumber(event.target.value)} /></label><button type="button" disabled={processing} onClick={handleShip}>{processing ? "처리 중..." : "배송 시작"}</button></div>}
          {order.status === "SHIPPED" && <div className="seller-order-action-content"><div><strong>배송 완료 여부를 확인해주세요.</strong><p>{order.shippingCompany} · {order.trackingNumber}</p></div>{confirmDelivery ? <div className="seller-order-delivery-confirm"><span>완료 후에는 이전 상태로 되돌릴 수 없습니다.</span><button type="button" className="secondary" disabled={processing} onClick={() => setConfirmDelivery(false)}>아니요</button><button type="button" disabled={processing} onClick={() => void runAction(() => deliverSellerOrder(sellerOrderId))}>{processing ? "처리 중..." : "배송 완료 확정"}</button></div> : <button type="button" disabled={processing} onClick={() => setConfirmDelivery(true)}>배송 완료 처리</button>}</div>}
          {order.status === "DELIVERED" && <p className="seller-order-action-notice success">배송이 완료된 주문입니다.</p>}
          {order.status === "CANCELLED" && <p className="seller-order-action-notice cancelled">취소된 주문입니다. 배송 상태를 변경할 수 없습니다.</p>}
          {order.status === "PENDING_PAYMENT" && <p className="seller-order-action-notice">결제 완료 전 주문은 처리할 수 없습니다.</p>}
          {actionError && <p className="seller-order-action-error">{actionError}</p>}
        </section>
      </div>
    </main>
  );
}
