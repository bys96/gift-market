"use client";

import { Suspense, useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";

import {
  confirmPayment,
  getPayment,
  getPaymentByMerchantPaymentId,
} from "@/lib/payment-api";
import { clearCompletedPaymentSession } from "@/lib/payment-session";
import { useAuthStore } from "@/stores/auth-store";
import type { PaymentResponse } from "@/types/payment";

const POLLING_INTERVAL_MS = 1_500;
const MAX_POLLING_COUNT = 10;

function PaymentSuccessContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const initialized = useAuthStore((state) => state.initialized);
  const startedRef = useRef(false);
  const [message, setMessage] = useState("결제 승인을 확인하고 있습니다.");
  const [orderId, setOrderId] = useState<number | null>(null);
  const [hasError, setHasError] = useState(false);
  const [isPollingTimedOut, setIsPollingTimedOut] = useState(false);

  useEffect(() => {
    if (!initialized) return;
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

    let disposed = false;

    const finishPaid = (payment: PaymentResponse) => {
      clearCompletedPaymentSession(merchantPaymentId);
      router.replace(`/my/orders/${payment.orderId}`);
    };

    const handleStatus = (payment: PaymentResponse) => {
      setOrderId(payment.orderId);
      if (["PAID", "PARTIALLY_CANCELED"].includes(payment.status)) {
        finishPaid(payment);
        return true;
      }
      if (["FAILED", "EXPIRED", "CANCELED"].includes(payment.status)) {
        setHasError(true);
        setMessage(payment.userMessage);
        return true;
      }
      return false;
    };

    const poll = async (
      initialPaymentId: number | null,
      retryConfirmWhenReady: boolean,
    ) => {
      let paymentId = initialPaymentId;
      let canRetryConfirm = retryConfirmWhenReady;

      for (let count = 0; count < MAX_POLLING_COUNT; count += 1) {
        await new Promise((resolve) =>
          window.setTimeout(resolve, POLLING_INTERVAL_MS),
        );
        if (disposed) return;

        try {
          const payment = paymentId === null
            ? await getPaymentByMerchantPaymentId(merchantPaymentId)
            : await getPayment(paymentId);
          paymentId = payment.paymentId;
          if (handleStatus(payment)) return;

          if (payment.amount !== amount) {
            setHasError(true);
            setMessage("결제 금액 정보가 일치하지 않습니다. 주문 내역을 확인해주세요.");
            return;
          }

          if (payment.status === "READY" && canRetryConfirm) {
            canRetryConfirm = false;
            try {
              const retried = await confirmPayment(payment.paymentId, {
                providerPaymentKey,
                merchantPaymentId,
                amount,
              });
              if (disposed) return;
              if (handleStatus(retried)) return;
            } catch {
              // 동일 confirm idempotency key로 시작된 결과를 다음 조회에서 확인합니다.
            }
          }
        } catch {
          // 일시적인 조회 실패는 다음 polling에서 다시 확인합니다.
        }
      }

      if (!disposed) {
        setIsPollingTimedOut(true);
        setMessage(
          "결제 결과 확인이 지연되고 있습니다. 잠시 후 주문 내역에서 결제 상태를 확인해주세요.",
        );
      }
    };

    const confirm = async (payment: PaymentResponse) => {
      if (handleStatus(payment)) return;

      if (payment.amount !== amount) {
        setHasError(true);
        setMessage("결제 금액 정보가 일치하지 않습니다. 주문 내역을 확인해주세요.");
        return;
      }

      if (payment.status !== "READY") {
        setMessage("결제 결과를 확인 중입니다.");
        await poll(payment.paymentId, false);
        return;
      }

      try {
        const confirmedPayment = await confirmPayment(payment.paymentId, {
          providerPaymentKey,
          merchantPaymentId,
          amount,
        });

        if (disposed) return;
        if (!handleStatus(confirmedPayment)) {
          setMessage("결제 결과를 확인 중입니다.");
          await poll(payment.paymentId, false);
        }
      } catch {
        if (!disposed) {
          setHasError(false);
          setMessage("결제 요청 결과를 확인하고 있습니다.");
          await poll(payment.paymentId, true);
        }
      }
    };

    const resolveAndConfirm = async () => {
      try {
        const payment = await getPaymentByMerchantPaymentId(merchantPaymentId);
        if (!disposed) await confirm(payment);
      } catch {
        if (!disposed) {
          setHasError(false);
          setMessage("결제 정보를 서버에서 확인하고 있습니다.");
          await poll(null, true);
        }
      }
    };

    void resolveAndConfirm();
    return () => {
      disposed = true;
      startedRef.current = false;
    };
  }, [initialized, router, searchParams]);

  return (
    <div className="payment-result-page">
      <section className="payment-result-card" role="status">
        <div className="payment-result-icon">
          {hasError ? "!" : isPollingTimedOut ? "?" : "…"}
        </div>
        <h1 className="payment-result-title">
          {hasError
            ? "결제를 확인해주세요"
            : isPollingTimedOut
              ? "결제 결과 확인 지연"
              : "결제 확인 중"}
        </h1>
        <p className="payment-result-description">{message}</p>

        {(hasError || isPollingTimedOut) && (
          <div className="payment-result-actions">
            <button
              type="button"
              className="payment-result-button is-primary"
              onClick={() =>
                router.replace(orderId ? `/my/orders/${orderId}` : "/my/orders")
              }
            >
              주문 내역으로 이동
            </button>
          </div>
        )}
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
