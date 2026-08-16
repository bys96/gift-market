"use client";

import { useEffect } from "react";

import { loadTossWidgets } from "@/lib/toss-payment";
import type { PaymentSession } from "@/types/payment";

interface TossPaymentWidgetProps {
  amount: number;
  customerKey: string;
  onReady: (
    requestPayment: ((paymentSession: PaymentSession) => Promise<void>) | null,
  ) => void;
  onLoadingChange: (loading: boolean) => void;
  onError: (message: string) => void;
}

export default function TossPaymentWidget({
  amount,
  customerKey,
  onReady,
  onLoadingChange,
  onError,
}: TossPaymentWidgetProps) {
  useEffect(() => {
    const clientKey = process.env.NEXT_PUBLIC_TOSS_CLIENT_KEY;
    let disposed = false;
    let paymentMethods: { destroy(): void } | null = null;
    let agreement: { destroy(): void } | null = null;

    if (!clientKey) {
      onError("결제 테스트 Client Key가 설정되지 않았습니다.");
      onLoadingChange(false);
      return;
    }

    const initialize = async () => {
      try {
        onLoadingChange(true);
        const widgets = await loadTossWidgets(
          clientKey,
          customerKey,
        );
        if (disposed) return;
        await widgets.setAmount({
          currency: "KRW",
          value: amount,
        });
        if (disposed) return;

        paymentMethods = await widgets.renderPaymentMethods({
          selector: "#toss-payment-methods",
          variantKey: "DEFAULT",
        });
        if (disposed) {
          paymentMethods.destroy();
          return;
        }
        agreement = await widgets.renderAgreement({
          selector: "#toss-payment-agreement",
          variantKey: "AGREEMENT",
        });

        if (disposed) {
          paymentMethods.destroy();
          agreement.destroy();
          return;
        }

        onReady(async (paymentSession) => {
          await widgets.setAmount({
            currency: "KRW",
            value: paymentSession.amount,
          });
          await widgets.requestPayment({
            orderId: paymentSession.merchantPaymentId,
            orderName: paymentSession.orderName,
            successUrl: `${window.location.origin}/payment/success`,
            failUrl: `${window.location.origin}/payment/fail`,
          });
        });
      } catch {
        if (!disposed) {
          onError("결제수단을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
        }
      } finally {
        if (!disposed) onLoadingChange(false);
      }
    };

    void initialize();

    return () => {
      disposed = true;
      paymentMethods?.destroy();
      agreement?.destroy();
      onReady(null);
    };
  }, [amount, customerKey, onReady, onLoadingChange, onError]);

  return (
    <section className="order-section payment-widget-section">
      <div className="order-section-header">
        <h2 className="order-section-title">결제수단</h2>
      </div>

      <div id="toss-payment-methods" className="payment-widget-methods" />
      <div id="toss-payment-agreement" className="payment-widget-agreement" />
    </section>
  );
}
