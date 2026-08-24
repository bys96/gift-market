"use client";
import { Suspense, useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { confirmExchangeShippingPayment } from "@/lib/exchange-api";
import { clearExchangePaymentSession, getExchangePaymentSession } from "@/lib/exchange-payment-session";

function Content() {
  const router = useRouter(); const params = useSearchParams(); const started = useRef(false);
  const [message, setMessage] = useState("교환 배송비 결제 결과를 확인하고 있습니다."); const path = useRef("/my/orders");
  useEffect(() => { if (started.current) return; started.current = true; const paymentKey = params.get("paymentKey"); const orderId = params.get("orderId"); const amount = Number(params.get("amount")); const session = getExchangePaymentSession();
    if (!paymentKey || !orderId || !Number.isSafeInteger(amount) || amount <= 0 || !session || session.providerOrderId !== orderId || session.amount !== amount) { window.setTimeout(() => setMessage("결제 정보가 일치하지 않습니다. 주문 상세에서 상태를 확인해주세요."), 0); return; }
    path.current = `/my/orders/${session.orderId}`; void confirmExchangeShippingPayment(session.exchangeRequestId, { providerPaymentKey: paymentKey, merchantPaymentId: orderId, amount }).then((payment) => { if (payment.status === "SUCCEEDED") { clearExchangePaymentSession(); router.replace(`/my/orders/${session.orderId}`); } else setMessage(payment.userMessage); }).catch((e) => setMessage(e instanceof Error ? e.message : "결제 결과를 확인하지 못했습니다."));
  }, [params, router]);
  return <div className="payment-result-page"><section className="payment-result-card" role="status"><h1 className="payment-result-title">교환 배송비 결제 확인</h1><p className="payment-result-description">{message}</p><button type="button" className="payment-result-button is-primary" onClick={() => router.replace(path.current)}>주문 상세로</button></section></div>;
}
export default function Page() { return <Suspense fallback={<div className="payment-result-page"><p>결제 정보를 불러오는 중입니다.</p></div>}><Content /></Suspense>; }
