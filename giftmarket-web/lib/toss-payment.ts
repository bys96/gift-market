import { loadScript } from "@tosspayments/sdk-loader/dist/loadScript";

const TOSS_SDK_URL = "https://js.tosspayments.com/v2/standard";

export interface TossWidgets {
  setAmount(amount: { currency: "KRW"; value: number }): Promise<void>;
  renderPaymentMethods(options: {
    selector: string;
    variantKey?: string;
  }): Promise<{ destroy(): void }>;
  renderAgreement(options: {
    selector: string;
    variantKey?: string;
  }): Promise<{ destroy(): void }>;
  requestPayment(options: {
    orderId: string;
    orderName: string;
    successUrl: string;
    failUrl: string;
  }): Promise<void>;
}

interface TossPaymentsInstance {
  widgets(options: { customerKey: string }): TossWidgets;
}

type TossPaymentsConstructor = (clientKey: string) => TossPaymentsInstance;

export async function loadTossWidgets(
  clientKey: string,
  customerKey: string,
) {
  const TossPayments = await loadScript<TossPaymentsConstructor>(
    TOSS_SDK_URL,
    "TossPayments",
    { priority: "high" },
  );

  if (!TossPayments) {
    throw new Error("결제창을 불러오지 못했습니다.");
  }

  return TossPayments(clientKey).widgets({ customerKey });
}
