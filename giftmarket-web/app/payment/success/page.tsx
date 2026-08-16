"use client";

import { Suspense, useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";

import { confirmPayment, getPayment } from "@/lib/payment-api";
import {
  clearCompletedPaymentSession,
  getPaymentSession,
} from "@/lib/payment-session";
import type { PaymentResponse } from "@/types/payment";

const POLLING_INTERVAL_MS = 1_500;
const MAX_POLLING_COUNT = 10;

function PaymentSuccessContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const startedRef = useRef(false);
  const [message, setMessage] = useState("결제 승인을 확인하고 있습니다.");
  const [orderId, setOrderId] = useState<number | null>(null);
  const [hasError, setHasError] = useState(false);

  useEffect(() => {
    if (startedRef.current) return;
    startedRef.current = true;

    const providerPaymentKey = searchParams.get("paymentKey");
    const merchantPaymentId = searchParams.get("orderId");
    const amountValue = searchParams.get("amount");
    const amount = amountValue ? Number(amountValue) : NaN;

    if (
      !providerPaymentKey ||
      !merchantPaymentId ||
      !Number.isSafeInteger(amount) ||
      amount <= 0
    ) {
      window.setTimeout(() => {
        setHasError(true);
        setMessage("결제 정보를 확인할 수 없습니다. 주문 내역을 확인해주세요.");
      }, 0);
      return;
    }

    const paymentSession = getPaymentSession(merchantPaymentId);

    if (
      !paymentSession ||
      paymentSession.merchantPaymentId !== merchantPaymentId ||
      paymentSession.amount !== amount
    ) {
      window.setTimeout(() => {
        setHasError(true);
        setMessage("저장된 결제 정보와 일치하지 않습니다. 주문 내역을 확인해주세요.");
      }, 0);
      return;
    }

    window.setTimeout(() => setOrderId(paymentSession.orderId), 0);
    let disposed = false;

    const finishPaid = () => {
      clearCompletedPaymentSession(merchantPaymentId);
      router.replace(`/my/orders/${paymentSession.orderId}`);
    };

    const handleStatus = (payment: PaymentResponse) => {
      if (payment.status === "PAID") {
        finishPaid();
        return true;
      }
      if (["FAILED", "EXPIRED", "CANCELED"].includes(payment.status)) {
        setHasError(true);
        setMessage(payment.userMessage);
        return true;
      }
      return false;
    };

    const poll = async () => {
      for (let count = 0; count < MAX_POLLING_COUNT; count += 1) {
        await new Promise((resolve) =>
          window.setTimeout(resolve, POLLING_INTERVAL_MS),
        );
        if (disposed) return;

        try {
          const payment = await getPayment(paymentSession.paymentId);
          if (handleStatus(payment)) return;
        } catch {
          // 일시적인 조회 실패는 다음 polling에서 다시 확인합니다.
        }
      }

      if (!disposed) {
        setMessage(
          "결제 결과를 확인하고 있습니다. 잠시 후 주문 내역에서 확인해주세요.",
        );
      }
    };

    const confirm = async () => {
      try {
        const payment = await confirmPayment(paymentSession.paymentId, {
          providerPaymentKey,
          merchantPaymentId,
          amount,
        });

        if (!handleStatus(payment) && !disposed) {
          setMessage("결제 결과를 확인 중입니다.");
          await poll();
        }
      } catch (error) {
        if (!disposed) {
          setHasError(true);
          setMessage(
            error instanceof Error
              ? error.message
              : "결제 승인을 완료하지 못했습니다. 주문 내역을 확인해주세요.",
          );
        }
      }
    };

    void confirm();
    return () => {
      disposed = true;
    };
  }, [router, searchParams]);

  return (
    <div className="payment-result-page">
      <section className="payment-result-card" role="status">
        <div className="payment-result-icon">{hasError ? "!" : "…"}</div>
        <h1 className="payment-result-title">
          {hasError ? "결제를 확인해주세요" : "결제 확인 중"}
        </h1>
        <p className="payment-result-description">{message}</p>

        <div className="payment-result-actions">
          <button
            type="button"
            className="payment-result-button is-primary"
            onClick={() =>
              router.replace(orderId ? `/my/orders/${orderId}` : "/my/orders")
            }
          >
            주문 내역 확인
          </button>
        </div>
      </section>
    </div>
  );
}

export default function PaymentSuccessPage() {
  return (
    <Suspense
      fallback={
        <div className="payment-result-page">
          <p>결제 정보를 불러오는 중입니다.</p>
        </div>
      }
    >
      <PaymentSuccessContent />
    </Suspense>
  );
}
