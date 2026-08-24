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
const count = (value: unknown) => typeof value === "number" && Number.isFinite(value) ? Math.max(0, value) : 0;
const money = (value: unknown) => typeof value === "number" && Number.isFinite(value) ? `${value.toLocaleString("ko-KR")}원` : "-";
const dateTime = (value: string | null | undefined) => value ? new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false }).format(new Date(value)) : "-";
const addressText = (a: { collectionPostalCode: string; collectionAddress: string; collectionAddressDetail: string | null }, prefix: "collection" = "collection") => prefix === "collection" ? `(${a.collectionPostalCode}) ${a.collectionAddress} ${a.collectionAddressDetail ?? ""}` : "";

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
  return <section className="exchange-payment-box">
    <h4>교환 배송비 결제</h4>
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
  const [images, setImages] = useState<SelectedImage[]>([]);
  const [busy, setBusy] = useState(false); const [error, setError] = useState(""); const [message, setMessage] = useState("");
  const requestKey = useRef<string | null>(null); const uploadedKeys = useRef<string[] | null>(null); const inputRef = useRef<HTMLInputElement>(null);
  const [viewer, setViewer] = useState<{ images: ExchangeRequest["images"]; initialIndex: number } | null>(null);
  const imagesRef = useRef<SelectedImage[]>([]);
  useEffect(() => { imagesRef.current = images; }, [images]);
  useEffect(() => () => imagesRef.current.forEach((image) => URL.revokeObjectURL(image.previewUrl)), []);
  const available = useMemo(() => new Map(sellerOrder.items.map((item) => {
    const activeReturn = returns.filter((r) => ACTIVE_RETURNS.has(r.status)).flatMap((r) => r.items).filter((i) => i.orderItemId === item.id).reduce((s, i) => s + count(i.quantity), 0);
    const returned = returns.flatMap((r) => r.items).filter((i) => i.orderItemId === item.id).reduce((m, i) => Math.max(m, count(i.returnedQuantity)), 0);
    const activeExchange = exchanges.filter((r) => ACTIVE_EXCHANGES.has(r.status)).flatMap((r) => r.items).filter((i) => i.orderItemId === item.id).reduce((s, i) => s + count(i.quantity), 0);
    const exchanged = exchanges.filter((r) => r.status === "COMPLETED").flatMap((r) => r.items).filter((i) => i.orderItemId === item.id).reduce((s, i) => s + count(i.quantity), 0);
    return [item.id, Math.max(0, count(item.quantity) - count(item.canceledQuantity) - returned - exchanged - activeReturn - activeExchange)];
  })), [sellerOrder.items, returns, exchanges]);
  const eligible = sellerOrder.items.filter((item) => (available.get(item.id) ?? 0) > 0);
  const invalidate = () => { requestKey.current = null; uploadedKeys.current = null; setError(""); };
  const loadItem = async (itemId: number, productId: number, originalVariantId: number | null) => {
    invalidate();
    if (selection[itemId]) { setSelection((v) => { const n = { ...v }; delete n[itemId]; return n; }); return; }
    try { const product = products[productId] ?? await getProduct(productId); setProducts((v) => ({ ...v, [productId]: product })); setSelection((v) => ({ ...v, [itemId]: { quantity: 1, targetVariantId: product.hasOptions ? originalVariantId : null } })); }
    catch (e) { setError(e instanceof Error ? e.message : "상품 정보를 불러오지 못했습니다."); }
  };
  const variantLabel = (product: ProductDetail, ids: number[]) => product.optionGroups.flatMap((group) => group.values.filter((value) => ids.includes(value.id)).map((value) => `${group.name}: ${value.value}`)).join(" / ");
  const searchAddress = (kind: AddressKind) => { const Postcode = (window as PostcodeWindow).daum?.Postcode; if (!Postcode) { setError("주소 검색 서비스를 불러오지 못했습니다."); return; } new Postcode({ oncomplete: (data) => { invalidate(); const setter = kind === "collection" ? setCollection : setReshipping; setter((v) => ({ ...v, postalCode: data.zonecode, address: data.roadAddress || data.jibunAddress || data.address, addressDetail: "" })); } }).open(); };
  const selectImages = (event: ChangeEvent<HTMLInputElement>) => { const files = Array.from(event.target.files ?? []); event.target.value = ""; if (images.length + files.length > 5) { alert("사진은 최대 5장까지 첨부할 수 있습니다."); return; } if (files.some((f) => !IMAGE_TYPES.includes(f.type) || f.size > 5 * 1024 * 1024)) { setError("JPEG, PNG, WEBP 이미지만 파일당 5MB까지 첨부할 수 있습니다."); return; } invalidate(); setImages((v) => [...v, ...files.map((file) => ({ file, previewUrl: URL.createObjectURL(file) }))]); };
  const submit = async () => {
    const items = Object.entries(selection).map(([id, value]) => ({ orderItemId: Number(id), quantity: value.quantity, targetVariantId: value.targetVariantId }));
    if (!items.length || !reason.trim()) { setError("교환 상품과 상세 사유를 입력해주세요."); return; }
    if ([collection, reshipping].some((a) => !a.recipientName.trim() || !a.phone.trim() || !a.postalCode.trim() || !a.address.trim())) { setError("회수지와 재배송지의 필수 정보를 입력해주세요."); return; }
    if (![collection.phone, reshipping.phone].every((p) => /^0\d{1,2}-\d{3,4}-\d{4}$/.test(p))) { setError("올바른 연락처를 입력해주세요."); return; }
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
      await createExchangeRequest(orderId, sellerOrder.sellerOrderId, { clientRequestKey: requestKey.current, reasonType, reason: reason.trim(), collectionRecipientName: collection.recipientName.trim(), collectionPhone: collection.phone, collectionPostalCode: collection.postalCode, collectionAddress: collection.address, collectionAddressDetail: collection.addressDetail.trim() || null, reshippingRecipientName: reshipping.recipientName.trim(), reshippingPhone: reshipping.phone, reshippingPostalCode: reshipping.postalCode, reshippingAddress: reshipping.address, reshippingAddressDetail: reshipping.addressDetail.trim() || null, items, imageObjectKeys: uploadedKeys.current });
      images.forEach((i) => URL.revokeObjectURL(i.previewUrl)); setImages([]); setSelection({}); setReason(""); requestKey.current = null; uploadedKeys.current = null; setOpen(false); setMessage("교환 요청이 접수되었습니다."); await onChanged();
    } catch (e) { setError(e instanceof Error ? e.message : "교환 요청을 처리하지 못했습니다."); try { await onChanged(); } catch {} } finally { setBusy(false); }
  };
  const renderAddress = (title: string, value: typeof collection, setter: typeof setCollection, kind: AddressKind) => <fieldset className="order-exchange-address"><legend>{title}</legend><label><span>받는 분</span><input value={value.recipientName} maxLength={100} onChange={(e) => { invalidate(); setter((v) => ({ ...v, recipientName: e.target.value })); }} /></label><label><span>연락처</span><input value={value.phone} maxLength={13} onChange={(e) => { invalidate(); setter((v) => ({ ...v, phone: formatKoreanPhoneNumber(e.target.value) })); }} /></label><label className="wide"><span>주소</span><div><input value={value.postalCode} readOnly /><button type="button" onClick={() => searchAddress(kind)}>주소 검색</button></div><input value={value.address} readOnly /><input value={value.addressDetail} maxLength={255} placeholder="상세주소" onChange={(e) => { invalidate(); setter((v) => ({ ...v, addressDetail: e.target.value })); }} /></label></fieldset>;
  return <div className="order-exchange-area"><Script src="https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js" strategy="afterInteractive" />
    <div className="order-exchange-heading"><strong>교환</strong><span>같은 상품의 동일 가격 옵션으로 교환할 수 있습니다.</span></div>
    {isLoading && <p className="order-exchange-notice">교환 이력을 불러오는 중입니다.</p>}{loadError && <p className="order-exchange-error" role="alert">{loadError}</p>}
    {exchanges.map((request) => <article className="order-exchange-history" key={request.exchangeRequestId}><header><span className={`order-exchange-status is-${request.status.toLowerCase()}`}>{EXCHANGE_STATUS_LABELS[request.status]}</span><time>{dateTime(request.requestedAt)}</time></header><h4>{EXCHANGE_REASON_LABELS[request.reasonType]}</h4><p>{request.reason}</p>{request.responsibility && <p>{EXCHANGE_RESPONSIBILITY_LABELS[request.responsibility]}</p>}
      {request.images.length > 0 && <div className="order-exchange-images">{request.images.map((image, index) => <button type="button" key={image.imageId} onClick={() => setViewer({ images: request.images, initialIndex: index })}><Image src={image.url} alt={`교환 증빙 이미지 ${index + 1}`} width={76} height={76} unoptimized /></button>)}</div>}
      <ul className="order-exchange-items">{request.items.map((item) => <li key={item.orderItemId}><strong>{item.originalProductName}</strong><span>기존 {item.originalOptionSnapshot ?? "기본 상품"} → 교환 {item.targetOptionSnapshot ?? "기본 상품"}</span><span>{item.quantity}개 · {money(item.targetUnitPrice)}</span>{item.inspectionResult && <em>{EXCHANGE_INSPECTION_LABELS[item.inspectionResult]}</em>}</li>)}</ul>
      {request.status === "REJECTED" && request.rejectedReason && <p className="order-exchange-error">거절 사유: {request.rejectedReason}</p>}{request.status === "CANCELED" && <p>교환 요청이 취소되었습니다.</p>}{request.status === "FAILED" && <p className="order-exchange-error">교환 처리 중 문제가 발생했습니다. 고객센터에 문의해주세요.</p>}{request.status === "COMPLETED" && <p className="order-exchange-complete">교환이 완료되었습니다.</p>}
      <div className="order-exchange-detail-grid"><section><h5>회수지</h5><p>{request.collectionRecipientName} · {request.collectionPhone}<br />{addressText(request)}</p></section><section><h5>재배송지</h5><p>{request.reshippingRecipientName} · {request.reshippingPhone}<br />({request.reshippingPostalCode}) {request.reshippingAddress} {request.reshippingAddressDetail ?? ""}</p></section></div>
      <div className="order-exchange-detail-grid"><Shipment title="교환 상품 회수" shipment={request.collectionShipment} empty="회수 송장이 아직 등록되지 않았습니다." /><Shipment title="교환 상품 배송" shipment={request.outboundShipment} empty="교환품 배송이 아직 시작되지 않았습니다." /></div>
      {request.status === "PAYMENT_PENDING" && request.responsibility === "BUYER" && <PaymentBox request={request} userId={userId} onChanged={onChanged} />}
    </article>)}
    {message && <p className="order-exchange-success" role="status">{message}</p>}
    {sellerOrder.status === "DELIVERED" && eligible.length > 0 && !open && <button type="button" className="order-exchange-open" onClick={() => setOpen(true)}>교환 신청</button>}
    {sellerOrder.status === "DELIVERED" && eligible.length === 0 && !isLoading && <p className="order-exchange-notice">현재 추가로 교환 신청할 수 있는 수량이 없습니다.</p>}
    {open && <div className="order-exchange-form"><h3>교환 상품 선택</h3>{eligible.map((item) => { const selected = selection[item.id]; const product = products[item.productId]; const max = available.get(item.id) ?? 0; return <div className="order-exchange-select" key={item.id}><label><input type="checkbox" checked={Boolean(selected)} onChange={() => void loadItem(item.id, item.productId, item.variantId)} /><span><strong>{item.productName}</strong><small>현재 옵션: {item.optionSnapshot ?? "기본 상품"}</small></span></label><span>교환 가능 {max}개</span>{selected && <><label>수량 <input type="number" min={1} max={max} value={selected.quantity} onChange={(e) => { invalidate(); setSelection((v) => ({ ...v, [item.id]: { ...selected, quantity: Math.min(max, Math.max(1, Number(e.target.value) || 1)) } })); }} /></label>{product?.hasOptions && <label>교환 옵션<select value={selected.targetVariantId ?? ""} onChange={(e) => { invalidate(); setSelection((v) => ({ ...v, [item.id]: { ...selected, targetVariantId: Number(e.target.value) } })); }}>{product.variants.map((variant) => { const priceOk = count(variant.price) === count(item.unitPrice); const stockOk = variant.available && count(variant.stockQuantity) >= selected.quantity; return <option key={variant.id} value={variant.id} disabled={!priceOk || !stockOk}>{variantLabel(product, variant.optionValueIds)} · {money(variant.price)} {!priceOk ? "(가격이 달라 교환 불가)" : !stockOk ? "(품절/재고 부족)" : ""}</option>; })}</select></label>}{product && !product.hasOptions && <p>{product.price === item.unitPrice && product.stockQuantity >= selected.quantity ? "동일 상품으로 교환" : product.price !== item.unitPrice ? "가격이 달라 교환할 수 없습니다." : "재고가 부족합니다."}</p>}</>}</div>; })}
      <div className="order-exchange-fields"><label><span>교환 사유</span><select value={reasonType} onChange={(e) => { invalidate(); setReasonType(e.target.value as ExchangeReasonType); }}>{Object.entries(EXCHANGE_REASON_LABELS).map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></label><label className="wide"><span>상세 사유</span><textarea rows={3} maxLength={500} value={reason} onChange={(e) => { invalidate(); setReason(e.target.value); }} /><small>{reason.length}/500</small></label></div>
      <section className="order-exchange-evidence"><strong>증빙 사진 (선택) {images.length}/5</strong><input ref={inputRef} hidden type="file" multiple accept={IMAGE_TYPES.join(",")} onChange={selectImages} /><button type="button" disabled={busy || images.length >= 5} onClick={() => inputRef.current?.click()}>사진 선택</button><div>{images.map((image, index) => <figure key={`${image.file.name}-${index}`}><Image src={image.previewUrl} alt={`${image.file.name} 미리보기`} width={76} height={76} unoptimized /><button type="button" aria-label={`${image.file.name} 삭제`} onClick={() => { invalidate(); URL.revokeObjectURL(image.previewUrl); setImages((v) => v.filter((_, i) => i !== index)); }}>×</button></figure>)}</div></section>
      <div className="order-exchange-addresses">{renderAddress("회수지", collection, setCollection, "collection")}{renderAddress("재배송지", reshipping, setReshipping, "reshipping")}</div>{error && <p className="order-exchange-error" role="alert">{error}</p>}<div className="order-exchange-actions"><button type="button" onClick={() => setOpen(false)} disabled={busy}>닫기</button><button type="button" onClick={() => void submit()} disabled={busy}>{busy ? "접수 중..." : "교환 신청"}</button></div></div>}
    {viewer && <ReturnImageViewerModal images={viewer.images} initialIndex={viewer.initialIndex} label="교환 증빙 이미지" onClose={() => setViewer(null)} />}
  </div>;
}

function Shipment({ title, shipment, empty }: { title: string; shipment: ExchangeRequest["collectionShipment"]; empty: string }) {
  return <section><h5>{title}</h5>{shipment ? <p>{shipment.shippingCompany ?? "택배사 확인 중"} · {shipment.trackingNumber ?? "송장번호 미등록"}<br />{EXCHANGE_SHIPMENT_STATUS_LABELS[shipment.status]}<br />발송 {dateTime(shipment.shippedAt)} · 완료 {dateTime(shipment.deliveredAt)}</p> : <p>{empty}</p>}</section>;
}
