"use client";
import { Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { getExchangePaymentSession } from "@/lib/exchange-payment-session";
function Content() { const router = useRouter(); const params = useSearchParams(); const session = getExchangePaymentSession(); const canceled = params.get("code") === "PAY_PROCESS_CANCELED"; return <div className="payment-result-page"><section className="payment-result-card"><h1 className="payment-result-title">결제가 완료되지 않았습니다.</h1><p className="payment-result-description">{canceled ? "결제가 취소되었습니다." : "결제를 완료하지 못했습니다."} 결제기한 내 다시 시도해주세요.</p><button type="button" className="payment-result-button is-primary" onClick={() => router.replace(session ? `/my/orders/${session.orderId}` : "/my/orders")}>주문 상세로</button></section></div>; }
export default function Page() { return <Suspense fallback={<div className="payment-result-page"><p>결제 정보를 확인하는 중입니다.</p></div>}><Content /></Suspense>; }
