"use client";

import Script from "next/script";
import Image from "next/image";
import { type ChangeEvent, useEffect, useMemo, useRef, useState } from "react";
import ReturnImageViewerModal from "@/components/return/ReturnImageViewerModal";
import { createExchangeRequest, getExchangeShippingPayment, prepareExchangeShippingPayment } from "@/lib/exchange-api";
import { saveExchangePaymentSession } from "@/lib/exchange-payment-session";
import { getProduct } from "@/lib/product-api";
import { uploadImage } from "@/lib/storage-api";
import { loadTossWidgets, type TossWidgets } from "@/lib/toss-payment";
import type { BuyerSellerOrder } from "@/types/order";
import type { ProductDetail } from "@/types/product";
import type { ReturnRequest } from "@/types/return";
import {
  EXCHANGE_INSPECTION_LABELS, EXCHANGE_PAYMENT_STATUS_LABELS, EXCHANGE_REASON_LABELS,
  EXCHANGE_RESPONSIBILITY_LABELS, EXCHANGE_SHIPMENT_STATUS_LABELS, EXCHANGE_STATUS_LABELS,
  type ExchangeReasonType, type ExchangeRequest, type ExchangeRequestStatus, type ExchangeShippingPayment,
} from "@/types/exchange";
import { formatKoreanPhoneNumber } from "@/utils/phone";

interface Address { recipientName: string; phone: string; postalCode: string; address: string; addressDetail: string | null; }
interface Props { orderId: number; sellerOrder: BuyerSellerOrder; exchanges: ExchangeRequest[]; returns: ReturnRequest[]; isLoading: boolean; loadError: string; defaultAddress: Address; userId: number; onChanged: () => Promise<void>; }
interface PostcodeData { zonecode: string; address: string; roadAddress: string; jibunAddress: string; }
type PostcodeWindow = Window & { daum?: { Postcode?: new (options: { oncomplete: (data: PostcodeData) => void }) => { open: () => void } } };
type SelectedImage = { file: File; previewUrl: string };
type AddressKind = "collection" | "reshipping";
type Selection = { quantity: number; targetVariantId: number | null };

const ACTIVE_EXCHANGES = new Set<ExchangeRequestStatus>(["REQUESTED", "APPROVED", "PAYMENT_PENDING", "COLLECTING", "RECEIVED", "INSPECTED", "RESHIPPING"]);
const ACTIVE_RETURNS = new Set(["REQUESTED", "APPROVED", "COLLECTING", "RECEIVED", "INSPECTED", "REFUNDING"]);
const IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"];
const SELLER_REASON_TYPES = new Set<ExchangeReasonType>(["DEFECTIVE", "WRONG_ITEM", "DAMAGED", "DESCRIPTION_MISMATCH"]);
const count = (value: unknown) => typeof value === "number" && Number.isFinite(value) ? Math.max(0, value) : 0;
const money = (value: unknown) => typeof value === "number" && Number.isFinite(value) ? `${value.toLocaleString("ko-KR")}원` : "-";
const dateTime = (value: string | null | undefined) => value ? new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false }).format(new Date(value)) : "-";
const addressText = (a: { collectionPostalCode: string; collectionAddress: string; collectionAddressDetail: string | null }, prefix: "collection" = "collection") => prefix === "collection" ? `(${a.collectionPostalCode}) ${a.collectionAddress} ${a.collectionAddressDetail ?? ""}` : "";
const sameAddress = (a: Address, b: Address) => a.recipientName === b.recipientName && a.phone === b.phone && a.postalCode === b.postalCode && a.address === b.address && (a.addressDetail ?? "") === (b.addressDetail ?? "");
const timelineSteps: Array<{ status: ExchangeRequestStatus; label: string; time: keyof ExchangeRequest }> = [
  { status: "REQUESTED", label: "요청", time: "requestedAt" }, { status: "APPROVED", label: "승인", time: "approvedAt" },
  { status: "PAYMENT_PENDING", label: "배송비 결제", time: "paymentPendingAt" }, { status: "COLLECTING", label: "회수", time: "collectingAt" },
  { status: "RECEIVED", label: "입고", time: "receivedAt" }, { status: "INSPECTED", label: "검수", time: "inspectedAt" },
  { status: "RESHIPPING", label: "재배송", time: "reshippingAt" }, { status: "COMPLETED", label: "완료", time: "completedAt" },
];
const statusOrder = timelineSteps.map((step) => step.status);

function PaymentBox({ request, userId, onChanged }: { request: ExchangeRequest; userId: number; onChanged: () => Promise<void> }) {
  const [payment, setPayment] = useState<ExchangeShippingPayment | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [widgets, setWidgets] = useState<TossWidgets | null>(null);
  const [renderedAt] = useState(() => Date.now());
  const paymentMethodsRef = useRef<{ destroy(): void } | null>(null);
  const agreementRef = useRef<{ destroy(): void } | null>(null);
  const suffix = request.exchangeRequestId;
  useEffect(() => { let live = true; void getExchangeShippingPayment(request.exchangeRequestId).then((value) => { if (live) setPayment(value); }).catch(() => { /* 최초 prepare 전에는 payment aggregate가 아직 없을 수 있다. */ }); return () => { live = false; paymentMethodsRef.current?.destroy(); agreementRef.current?.destroy(); }; }, [request.exchangeRequestId]);
  const prepare = async () => {
    if (busy) return;
    try {
      setBusy(true); setError("");
      const value = await prepareExchangeShippingPayment(request.exchangeRequestId);
      setPayment(value);
      if (value.status === "SUCCEEDED" || value.amount === 0) { await onChanged(); return; }
      if (value.status === "REQUESTED") return;
      const clientKey = process.env.NEXT_PUBLIC_TOSS_CLIENT_KEY;
      if (!clientKey) throw new Error("결제 설정을 확인해주세요.");
      const instance = await loadTossWidgets(clientKey, `exchange-customer-${userId}`);
      await instance.setAmount({ currency: "KRW", value: value.amount });
      paymentMethodsRef.current?.destroy(); agreementRef.current?.destroy();
      paymentMethodsRef.current = await instance.renderPaymentMethods({ selector: `#exchange-payment-methods-${suffix}`, variantKey: "DEFAULT" });
      agreementRef.current = await instance.renderAgreement({ selector: `#exchange-payment-agreement-${suffix}`, variantKey: "AGREEMENT" });
      setWidgets(instance);
    } catch (e) { setError(e instanceof Error ? e.message : "결제를 준비하지 못했습니다."); }
    finally { setBusy(false); }
  };
  const pay = async () => {
    if (!widgets || !payment || busy) return;
    try {
      setBusy(true);
      saveExchangePaymentSession({ exchangeRequestId: request.exchangeRequestId, orderId: request.orderId, providerOrderId: payment.providerOrderId, amount: payment.amount });
      await widgets.requestPayment({ orderId: payment.providerOrderId, orderName: "교환 배송비", successUrl: `${location.origin}/my/exchanges/payment/success`, failUrl: `${location.origin}/my/exchanges/payment/fail` });
    } catch (e) { setError(e instanceof Error ? e.message : "결제를 시작하지 못했습니다."); setBusy(false); }
  };
  const blocked = payment?.status === "REQUESTED" || payment?.status === "COMPENSATION_REQUIRED" || payment?.status === "EXPIRED" || (request.paymentDueAt ? renderedAt >= new Date(request.paymentDueAt).getTime() : false);
  return <section className="exchange-payment-box" aria-label="교환 배송비 결제">
    <div className="exchange-payment-title"><span>지금 필요한 작업</span><h4>교환 배송비 결제가 필요합니다</h4><p>결제가 완료되면 교환 상품 회수가 진행됩니다.</p></div>
    <dl><div><dt>결제금액</dt><dd>{payment ? money(payment.amount) : "확인 중"}</dd></div><div><dt>결제기한</dt><dd>{dateTime(request.paymentDueAt)}</dd></div><div><dt>결제상태</dt><dd>{payment ? EXCHANGE_PAYMENT_STATUS_LABELS[payment.status] : "확인 중"}</dd></div></dl>
    {payment?.userMessage && <p>{payment.status === "COMPENSATION_REQUIRED" ? "결제 상태 확인이 필요합니다. 잠시 후 다시 확인해주세요." : payment.userMessage}</p>}
    <div id={`exchange-payment-methods-${suffix}`} /><div id={`exchange-payment-agreement-${suffix}`} />
    {!widgets ? <button type="button" onClick={() => void prepare()} disabled={busy || blocked}>{busy ? "준비 중..." : "교환 배송비 결제"}</button> : <button type="button" onClick={() => void pay()} disabled={busy}>{busy ? "처리 중..." : `${money(payment?.amount)} 결제`}</button>}
    {payment?.status === "REQUESTED" && <p>결제 결과를 확인 중입니다. 중복 결제를 방지하기 위해 잠시 기다려주세요.</p>}
    {error && <p className="order-exchange-error" role="alert">{error}</p>}
  </section>;
}

export default function OrderExchangePanel({ orderId, sellerOrder, exchanges, returns, isLoading, loadError, defaultAddress, userId, onChanged }: Props) {
  const [open, setOpen] = useState(false);
  const [selection, setSelection] = useState<Record<number, Selection>>({});
  const [products, setProducts] = useState<Record<number, ProductDetail>>({});
  const [reasonType, setReasonType] = useState<ExchangeReasonType>("CHANGE_OF_MIND");
  const [reason, setReason] = useState("");
  const [collection, setCollection] = useState({ ...defaultAddress, addressDetail: defaultAddress.addressDetail ?? "" });
  const [reshipping, setReshipping] = useState({ ...defaultAddress, addressDetail: defaultAddress.addressDetail ?? "" });
  const [useCollectionAddress, setUseCollectionAddress] = useState(true);
  const [images, setImages] = useState<SelectedImage[]>([]);
  const [busy, setBusy] = useState(false); const [error, setError] = useState(""); const [message, setMessage] = useState("");
  const requestKey = useRef<string | null>(null); const uploadedKeys = useRef<string[] | null>(null); const inputRef = useRef<HTMLInputElement>(null);
  const [viewer, setViewer] = useState<{ images: ExchangeRequest["images"]; initialIndex: number } | null>(null);
  const effectiveReshipping = useCollectionAddress ? collection : reshipping;
  const imagesRef = useRef<SelectedImage[]>([]);
  useEffect(() => { imagesRef.current = images; }, [images]);
  useEffect(() => () => imagesRef.current.forEach((image) => URL.revokeObjectURL(image.previewUrl)), []);
  const available = useMemo(() => new Map(sellerOrder.items.map((item) => {
    const activeReturn = returns.filter((r) => ACTIVE_RETURNS.has(r.status)).flatMap((r) => r.items).filter((i) => i.orderItemId === item.id).reduce((s, i) => s + count(i.quantity), 0);
    const returned = returns.flatMap((r) => r.items).filter((i) => i.orderItemId === item.id).reduce((m, i) => Math.max(m, count(i.returnedQuantity)), 0);
    const activeExchange = exchanges.filter((r) => ACTIVE_EXCHANGES.has(r.status)).flatMap((r) => r.items).filter((i) => i.orderItemId === item.id).reduce((s, i) => s + count(i.quantity), 0);
    const exchanged = exchanges.filter((r) => r.status === "COMPLETED").flatMap((r) => r.items).filter((i) => i.orderItemId === item.id).reduce((s, i) => s + count(i.quantity), 0);
    return [item.id, Math.max(0, count(item.quantity) - count(item.canceledQuantity) - count(item.confirmedQuantity) - returned - exchanged - activeReturn - activeExchange)];
  })), [sellerOrder.items, returns, exchanges]);
  const eligible = sellerOrder.items.filter((item) => (available.get(item.id) ?? 0) > 0);
  const invalidate = () => { requestKey.current = null; uploadedKeys.current = null; setError(""); };
  const loadItem = async (itemId: number, productId: number, originalVariantId: number | null) => {
    invalidate();
    if (selection[itemId]) { setSelection((v) => { const n = { ...v }; delete n[itemId]; return n; }); return; }
    try { const product = products[productId] ?? await getProduct(productId); setProducts((v) => ({ ...v, [productId]: product })); const item = sellerOrder.items.find((value) => value.id === itemId); const allowSame = SELLER_REASON_TYPES.has(reasonType) || reasonType === "OTHER"; const firstTarget = product.hasOptions ? product.variants.find((variant) => variant.available && variant.stockQuantity >= 1 && variant.price === item?.unitPrice && (allowSame || variant.id !== originalVariantId))?.id ?? null : null; setSelection((v) => ({ ...v, [itemId]: { quantity: 1, targetVariantId: firstTarget } })); }
    catch (e) { setError(e instanceof Error ? e.message : "상품 정보를 불러오지 못했습니다."); }
  };
  const variantLabel = (product: ProductDetail, ids: number[]) => product.optionGroups.flatMap((group) => group.values.filter((value) => ids.includes(value.id)).map((value) => `${group.name}: ${value.value}`)).join(" / ");
  const searchAddress = (kind: AddressKind) => { const Postcode = (window as PostcodeWindow).daum?.Postcode; if (!Postcode) { setError("주소 검색 서비스를 불러오지 못했습니다."); return; } new Postcode({ oncomplete: (data) => { invalidate(); const setter = kind === "collection" ? setCollection : setReshipping; setter((v) => ({ ...v, postalCode: data.zonecode, address: data.roadAddress || data.jibunAddress || data.address, addressDetail: "" })); } }).open(); };
  const selectImages = (event: ChangeEvent<HTMLInputElement>) => { const files = Array.from(event.target.files ?? []); event.target.value = ""; if (images.length + files.length > 5) { alert("사진은 최대 5장까지 첨부할 수 있습니다."); return; } if (files.some((f) => !IMAGE_TYPES.includes(f.type) || f.size > 5 * 1024 * 1024)) { setError("JPEG, PNG, WEBP 이미지만 파일당 5MB까지 첨부할 수 있습니다."); return; } invalidate(); setImages((v) => [...v, ...files.map((file) => ({ file, previewUrl: URL.createObjectURL(file) }))]); };
  const submit = async () => {
    const items = Object.entries(selection).map(([id, value]) => ({ orderItemId: Number(id), quantity: value.quantity, targetVariantId: value.targetVariantId }));
    if (!items.length || !reason.trim()) { setError("교환 상품과 상세 사유를 입력해주세요."); return; }
    if ([collection, effectiveReshipping].some((a) => !a.recipientName.trim() || !a.phone.trim() || !a.postalCode.trim() || !a.address.trim())) { setError("회수지와 재배송지의 필수 정보를 입력해주세요."); return; }
    if (![collection.phone, effectiveReshipping.phone].every((p) => /^0\d{1,2}-\d{3,4}-\d{4}$/.test(p))) { setError("올바른 연락처를 입력해주세요."); return; }
    try { setBusy(true); setError("");
      const selectedOrderItems = sellerOrder.items.filter((item) => selection[item.id]);
      const latestEntries = await Promise.all(selectedOrderItems.map(async (item) => [item.productId, await getProduct(item.productId)] as const));
      const latestProducts = Object.fromEntries(latestEntries) as Record<number, ProductDetail>;
      setProducts((current) => ({ ...current, ...latestProducts }));
      for (const item of selectedOrderItems) {
        const selected = selection[item.id]; const product = latestProducts[item.productId];
        if (selected.quantity > (available.get(item.id) ?? 0)) throw new Error("교환 가능 수량을 다시 확인해주세요.");
        if (product.hasOptions) {
          const variant = product.variants.find((value) => value.id === selected.targetVariantId);
          if (!variant || !variant.available || variant.stockQuantity < selected.quantity) throw new Error(`${item.productName}의 교환 옵션 재고가 부족합니다.`);
          if (variant.price !== item.unitPrice) throw new Error(`${item.productName}은 가격이 같은 옵션만 교환할 수 있습니다.`);
        } else {
          if (selected.targetVariantId !== null) throw new Error("옵션이 없는 상품의 교환 옵션이 올바르지 않습니다.");
          if (product.status !== "ON_SALE" || product.stockQuantity < selected.quantity) throw new Error(`${item.productName}의 재고가 부족합니다.`);
          if (product.price !== item.unitPrice) throw new Error(`${item.productName}은 현재 가격이 달라 자동 교환할 수 없습니다.`);
        }
      }
      requestKey.current ??= crypto.randomUUID(); if (!uploadedKeys.current) { const keys: string[] = []; for (const image of images) keys.push(await uploadImage(image.file, "EXCHANGE_EVIDENCE")); uploadedKeys.current = keys; }
      await createExchangeRequest(orderId, sellerOrder.sellerOrderId, { clientRequestKey: requestKey.current, reasonType, reason: reason.trim(), collectionRecipientName: collection.recipientName.trim(), collectionPhone: collection.phone, collectionPostalCode: collection.postalCode, collectionAddress: collection.address, collectionAddressDetail: collection.addressDetail.trim() || null, reshippingRecipientName: effectiveReshipping.recipientName.trim(), reshippingPhone: effectiveReshipping.phone, reshippingPostalCode: effectiveReshipping.postalCode, reshippingAddress: effectiveReshipping.address, reshippingAddressDetail: effectiveReshipping.addressDetail.trim() || null, items, imageObjectKeys: uploadedKeys.current });
      images.forEach((i) => URL.revokeObjectURL(i.previewUrl)); setImages([]); setSelection({}); setReason(""); requestKey.current = null; uploadedKeys.current = null; setOpen(false); setMessage("교환 요청이 접수되었습니다."); await onChanged();
    } catch (e) { setError(e instanceof Error ? e.message : "교환 요청을 처리하지 못했습니다."); try { await onChanged(); } catch {} } finally { setBusy(false); }
  };
  const changeReasonType = (next: ExchangeReasonType) => { invalidate(); setReasonType(next); const allowSame = SELLER_REASON_TYPES.has(next) || next === "OTHER"; setSelection((current) => Object.fromEntries(Object.entries(current).map(([id, selected]) => { const item = sellerOrder.items.find((value) => value.id === Number(id)); const product = item ? products[item.productId] : undefined; if (!item || !product?.hasOptions || allowSame || selected.targetVariantId !== item.variantId) return [id, selected]; const target = product.variants.find((variant) => variant.available && variant.stockQuantity >= selected.quantity && variant.price === item.unitPrice && variant.id !== item.variantId); return [id, { ...selected, targetVariantId: target?.id ?? null }]; })) as Record<number, Selection>); };
  return <div className="order-exchange-area"><Script src="https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js" strategy="afterInteractive" />
    <div className="order-exchange-heading"><strong>교환</strong><span>같은 상품의 동일 가격 옵션으로 교환할 수 있습니다.</span></div>
    {isLoading && <p className="order-exchange-notice">교환 이력을 불러오는 중입니다.</p>}{loadError && <p className="order-exchange-error" role="alert">{loadError}</p>}
    {exchanges.map((request) => <article className="order-exchange-history" key={request.exchangeRequestId}><header><div><span className={`order-exchange-status is-${request.status.toLowerCase()}`}>{EXCHANGE_STATUS_LABELS[request.status]}</span><strong>{EXCHANGE_REASON_LABELS[request.reasonType]}</strong></div><time>요청 {dateTime(request.requestedAt)}</time></header><div className="order-exchange-reason"><p>{request.reason}</p><span>{request.responsibility ? EXCHANGE_RESPONSIBILITY_LABELS[request.responsibility] : "귀책 확인 중"}</span></div>
      {request.images.length > 0 && <div className="order-exchange-images">{request.images.map((image, index) => <button type="button" key={image.imageId} onClick={() => setViewer({ images: request.images, initialIndex: index })}><Image src={image.url} alt={`교환 증빙 이미지 ${index + 1}`} width={76} height={76} unoptimized /></button>)}</div>}
      <ul className="order-exchange-items">{request.items.map((item) => <li key={item.orderItemId}><strong>{item.originalProductName}</strong><div className="order-exchange-route"><span><small>원 상품</small>{item.originalOptionSnapshot ?? "기본 상품"}</span><b aria-hidden="true">→</b><span><small>교환 상품</small>{item.targetOptionSnapshot ?? "기본 상품"}</span></div><p>{item.quantity}개{item.inspectionResult ? ` · ${EXCHANGE_INSPECTION_LABELS[item.inspectionResult]}` : ""}</p></li>)}</ul>
      {request.status === "REJECTED" && request.rejectedReason && <p className="order-exchange-error">거절 사유: {request.rejectedReason}</p>}{request.status === "CANCELED" && <p>교환 요청이 취소되었습니다.</p>}{request.status === "FAILED" && <p className="order-exchange-error">교환 처리 중 문제가 발생했습니다. 고객센터에 문의해주세요.</p>}{request.status === "COMPLETED" && <p className="order-exchange-complete">교환이 완료되었습니다.</p>}
      <div className={`order-exchange-address-summary ${sameAddress({ recipientName: request.collectionRecipientName, phone: request.collectionPhone, postalCode: request.collectionPostalCode, address: request.collectionAddress, addressDetail: request.collectionAddressDetail }, { recipientName: request.reshippingRecipientName, phone: request.reshippingPhone, postalCode: request.reshippingPostalCode, address: request.reshippingAddress, addressDetail: request.reshippingAddressDetail }) ? "is-same" : ""}`}><section><h5>회수지</h5><p>{request.collectionRecipientName} · {request.collectionPhone}<br />{addressText(request)}</p></section><section><h5>재배송지</h5>{sameAddress({ recipientName: request.collectionRecipientName, phone: request.collectionPhone, postalCode: request.collectionPostalCode, address: request.collectionAddress, addressDetail: request.collectionAddressDetail }, { recipientName: request.reshippingRecipientName, phone: request.reshippingPhone, postalCode: request.reshippingPostalCode, address: request.reshippingAddress, addressDetail: request.reshippingAddressDetail }) ? <p>회수지와 동일</p> : <p>{request.reshippingRecipientName} · {request.reshippingPhone}<br />({request.reshippingPostalCode}) {request.reshippingAddress} {request.reshippingAddressDetail ?? ""}</p>}</section></div>
      {(request.collectionShipment || request.outboundShipment || ["COLLECTING", "RECEIVED", "INSPECTED", "RESHIPPING", "COMPLETED"].includes(request.status)) && <div className="order-exchange-shipments"><Shipment title="교환 상품 회수" shipment={request.collectionShipment} empty="판매자가 회수 배송을 준비하고 있습니다." /><Shipment title="교환 상품 배송" shipment={request.outboundShipment} empty="검수 후 교환 상품 배송이 시작됩니다." /></div>}
      <ExchangeTimeline request={request} />
      {request.status === "PAYMENT_PENDING" && request.responsibility === "BUYER" && <PaymentBox request={request} userId={userId} onChanged={onChanged} />}
    </article>)}
    {message && <p className="order-exchange-success" role="status">{message}</p>}
    {sellerOrder.status === "DELIVERED" && eligible.length > 0 && !open && <button type="button" className="order-exchange-open" onClick={() => setOpen(true)}>교환 신청</button>}
    {sellerOrder.status === "DELIVERED" && eligible.length === 0 && !isLoading && <p className="order-exchange-notice">현재 추가로 교환 신청할 수 있는 수량이 없습니다.</p>}
    {open && <div className="order-exchange-form"><header className="order-exchange-form-header"><span>교환 신청</span><h3>교환할 상품을 확인해주세요</h3><p>교환 가능한 동일 가격 옵션만 표시됩니다.</p></header><section className="order-exchange-form-section"><h4><b>1</b> 교환할 상품 · 옵션 · 수량</h4>{eligible.map((item) => { const selected = selection[item.id]; const product = products[item.productId]; const max = available.get(item.id) ?? 0; const allowSame = SELLER_REASON_TYPES.has(reasonType) || reasonType === "OTHER"; const variants = product?.variants.filter((variant) => variant.available && count(variant.stockQuantity) >= (selected?.quantity ?? 1) && count(variant.price) === count(item.unitPrice) && (allowSame || variant.id !== item.variantId)) ?? []; return <div className={`order-exchange-select ${selected ? "is-selected" : ""}`} key={item.id}><label className="order-exchange-product-check"><input type="checkbox" checked={Boolean(selected)} onChange={() => void loadItem(item.id, item.productId, item.variantId)} /><span><strong>{item.productName}</strong><small>{item.optionSnapshot ?? "기본 상품"}</small></span><em>교환 가능 {max}개</em></label>{selected && <div className="order-exchange-target"><div className="order-exchange-route"><span><small>원 상품</small>{item.optionSnapshot ?? "기본 상품"}</span><b aria-hidden="true">→</b><label><small>교환 상품</small>{product?.hasOptions ? <select aria-label={`${item.productName} 교환 옵션`} value={selected.targetVariantId ?? ""} onChange={(e) => { invalidate(); setSelection((v) => ({ ...v, [item.id]: { ...selected, targetVariantId: Number(e.target.value) } })); }}><option value="" disabled>교환 옵션을 선택해주세요</option>{variants.map((variant) => <option key={variant.id} value={variant.id}>{variant.id === item.variantId ? `동일 옵션 새 상품 · ${variantLabel(product, variant.optionValueIds)}` : variantLabel(product, variant.optionValueIds)}</option>)}</select> : <strong>동일 상품 새 상품</strong>}</label></div>{product?.hasOptions && variants.length === 0 && <p className="order-exchange-option-empty">현재 수량으로 교환 가능한 옵션이 없습니다.</p>}<label className="order-exchange-quantity"><span>수량</span><input type="number" min={1} max={max} value={selected.quantity} onChange={(e) => { invalidate(); const quantity = Math.min(max, Math.max(1, Number(e.target.value) || 1)); const nextVariants = product?.variants.filter((variant) => variant.available && variant.stockQuantity >= quantity && variant.price === item.unitPrice && (allowSame || variant.id !== item.variantId)) ?? []; setSelection((v) => ({ ...v, [item.id]: { quantity, targetVariantId: nextVariants.some((variant) => variant.id === selected.targetVariantId) ? selected.targetVariantId : nextVariants[0]?.id ?? null } })); }} /><strong>개</strong></label></div>}</div>; })}</section>
      <section className="order-exchange-form-section"><h4><b>2</b> 교환 사유</h4><div className="order-exchange-fields"><label><span>교환 사유</span><select value={reasonType} onChange={(e) => changeReasonType(e.target.value as ExchangeReasonType)}>{Object.entries(EXCHANGE_REASON_LABELS).map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></label><label className="wide"><span>상세 사유</span><textarea rows={3} maxLength={500} placeholder="상품 상태와 교환이 필요한 이유를 적어주세요." value={reason} onChange={(e) => { invalidate(); setReason(e.target.value); }} /><small>{reason.length}/500</small></label></div></section>
      <section className="order-exchange-form-section"><h4><b>3</b> 사진 <small>선택 · {images.length}/5</small></h4><div className="order-exchange-evidence"><input ref={inputRef} hidden type="file" multiple accept={IMAGE_TYPES.join(",")} onChange={selectImages} /><button type="button" className="order-exchange-image-add" disabled={busy || images.length >= 5} onClick={() => inputRef.current?.click()}>+ 사진 추가</button><div>{images.map((image, index) => <figure key={`${image.file.name}-${index}`}><Image src={image.previewUrl} alt={`${image.file.name} 미리보기`} width={88} height={88} unoptimized /><button type="button" aria-label={`${image.file.name} 삭제`} onClick={() => { invalidate(); URL.revokeObjectURL(image.previewUrl); setImages((v) => v.filter((_, i) => i !== index)); }}>×</button></figure>)}</div><p>JPEG, PNG, WEBP · 파일당 최대 5MB</p></div></section>
      <section className="order-exchange-form-section"><h4><b>4</b> 회수지 · 재배송지</h4><div className="order-exchange-addresses"><AddressEditor title="회수지" description="교환할 상품을 가져갈 주소" value={collection} onChange={(value) => { invalidate(); setCollection({ ...value, addressDetail: value.addressDetail ?? "" }); }} onSearch={() => searchAddress("collection")} /><div className="order-exchange-reshipping"><label className="order-exchange-same-address"><input type="checkbox" checked={useCollectionAddress} onChange={(e) => { invalidate(); setUseCollectionAddress(e.target.checked); }} />회수지와 동일</label>{!useCollectionAddress && <AddressEditor title="재배송지" description="새 교환 상품을 받을 주소" value={reshipping} onChange={(value) => { invalidate(); setReshipping({ ...value, addressDetail: value.addressDetail ?? "" }); }} onSearch={() => searchAddress("reshipping")} />}{useCollectionAddress && <div className="order-exchange-address-compact"><strong>재배송지</strong><span>새 교환 상품을 받을 주소</span><p>회수지와 동일한 주소로 배송합니다.</p></div>}</div></div></section>
      <div className={`order-exchange-fee-notice is-${reasonType === "OTHER" ? "other" : SELLER_REASON_TYPES.has(reasonType) ? "seller" : "buyer"}`}>{reasonType === "OTHER" ? "판매자 확인 후 교환 배송비가 발생할 수 있습니다." : SELLER_REASON_TYPES.has(reasonType) ? "판매자 귀책 사유는 교환 배송비 없이 진행됩니다." : "판매자 승인 후 교환 배송비 결제가 필요합니다."}</div>{error && <p className="order-exchange-error" role="alert">{error}</p>}<div className="order-exchange-actions"><button type="button" onClick={() => setOpen(false)} disabled={busy}>닫기</button><button type="button" onClick={() => void submit()} disabled={busy}>{busy ? "접수 중..." : "교환 신청"}</button></div></div>}
    {viewer && <ReturnImageViewerModal images={viewer.images} initialIndex={viewer.initialIndex} label="교환 증빙 이미지" onClose={() => setViewer(null)} />}
  </div>;
}

function Shipment({ title, shipment, empty }: { title: string; shipment: ExchangeRequest["collectionShipment"]; empty: string }) {
  return <section className={shipment ? "has-shipment" : "is-pending"}><h5>{title}</h5>{shipment ? <dl><div><dt>배송 상태</dt><dd>{EXCHANGE_SHIPMENT_STATUS_LABELS[shipment.status]}</dd></div><div><dt>택배사</dt><dd>{shipment.shippingCompany ?? "확인 중"}</dd></div><div><dt>송장번호</dt><dd>{shipment.trackingNumber ?? "미등록"}</dd></div>{shipment.shippedAt && <div><dt>발송일</dt><dd>{dateTime(shipment.shippedAt)}</dd></div>}{shipment.deliveredAt && <div><dt>완료일</dt><dd>{dateTime(shipment.deliveredAt)}</dd></div>}</dl> : <p>{empty}</p>}</section>;
}

function AddressEditor({ title, description, value, onChange, onSearch }: { title: string; description: string; value: Address; onChange: (value: Address) => void; onSearch: () => void }) {
  return <fieldset className="order-exchange-address"><legend>{title}<small>{description}</small></legend><label><span>받는 분</span><input value={value.recipientName} maxLength={100} onChange={(e) => onChange({ ...value, recipientName: e.target.value })} /></label><label><span>연락처</span><input value={value.phone} maxLength={13} onChange={(e) => onChange({ ...value, phone: formatKoreanPhoneNumber(e.target.value) })} /></label><label className="wide"><span>주소</span><div><input value={value.postalCode} readOnly /><button type="button" onClick={onSearch}>주소 검색</button></div><input value={value.address} readOnly /><input value={value.addressDetail ?? ""} maxLength={255} placeholder="상세주소" onChange={(e) => onChange({ ...value, addressDetail: e.target.value })} /></label></fieldset>;
}

function ExchangeTimeline({ request }: { request: ExchangeRequest }) {
  const currentIndex = request.status === "REJECTED" || request.status === "CANCELED" || request.status === "FAILED" ? 0 : statusOrder.indexOf(request.status);
  const steps = request.responsibility === "SELLER" ? timelineSteps.filter((step) => step.status !== "PAYMENT_PENDING") : timelineSteps;
  return <section className="order-exchange-timeline" aria-label="교환 진행 상태"><h5>진행 상태</h5><ol>{steps.map((step) => { const originalIndex = statusOrder.indexOf(step.status); const value = request[step.time]; const active = originalIndex <= currentIndex; return <li key={step.status} className={active ? "is-active" : ""}><span aria-hidden="true" /><div><strong>{step.label}</strong>{typeof value === "string" && <time>{dateTime(value)}</time>}{step.status === "PAYMENT_PENDING" && request.responsibility === null && <small>필요 시 진행</small>}</div></li>; })}</ol>{request.responsibility === "SELLER" && <p>판매자 부담으로 교환 배송비 없이 진행됩니다.</p>}</section>;
}
