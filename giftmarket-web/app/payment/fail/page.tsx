"use client";

import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";

import { getPaymentSession } from "@/lib/payment-session";
import type { PaymentSession } from "@/types/payment";

function getUserMessage(code: string | null) {
  if (code === "PAY_PROCESS_CANCELED") {
    return "결제가 취소되었습니다. 결제수단을 다시 선택해 시도할 수 있습니다.";
  }
  if (code === "PAY_PROCESS_ABORTED") {
    return "결제를 완료하지 못했습니다. 결제 정보를 다시 확인해주세요.";
  }
  return "결제를 완료하지 못했습니다. 잠시 후 다시 시도해주세요.";
}

function PaymentFailContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const merchantPaymentId = searchParams.get("orderId");
  const [paymentSession, setPaymentSession] =
    useState<PaymentSession | null>(null);
  const message = getUserMessage(searchParams.get("code"));

  useEffect(() => {
    if (!merchantPaymentId) return;

    const timeoutId = window.setTimeout(() => {
      setPaymentSession(getPaymentSession(merchantPaymentId));
    }, 0);

    return () => window.clearTimeout(timeoutId);
  }, [merchantPaymentId]);

  return (
    <div className="payment-result-page">
      <section className="payment-result-card">
        <div className="payment-result-icon">!</div>
        <h1 className="payment-result-title">결제가 완료되지 않았습니다</h1>
        <p className="payment-result-description">{message}</p>

        <div className="payment-result-actions">
          {paymentSession && (
            <button
              type="button"
              className="payment-result-button is-primary"
              onClick={() => router.replace(paymentSession.returnPath)}
            >
              다시 결제하기
            </button>
          )}
          <button
            type="button"
            className="payment-result-button"
            onClick={() => router.replace("/my/orders")}
          >
            주문 내역 확인
          </button>
        </div>
      </section>
    </div>
  );
}

export default function PaymentFailPage() {
  return (
    <Suspense
      fallback={
        <div className="payment-result-page">
          <p>결제 정보를 불러오는 중입니다.</p>
        </div>
      }
    >
      <PaymentFailContent />
    </Suspense>
  );
}
