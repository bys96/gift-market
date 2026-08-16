import { apiFetch } from "@/lib/api";
import type { ApiResponse } from "@/types/api";
import type {
  PaymentConfirmRequest,
  PaymentResponse,
} from "@/types/payment";

export async function confirmPayment(
  paymentId: number,
  request: PaymentConfirmRequest,
): Promise<PaymentResponse> {
  const result = await apiFetch<ApiResponse<PaymentResponse>>(
    `/api/payments/${paymentId}/confirm`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
    },
  );

  if (!result.success || !result.data) {
    throw new Error(result.message || "결제 승인을 확인하지 못했습니다.");
  }

  return result.data;
}

export async function getPayment(paymentId: number): Promise<PaymentResponse> {
  const result = await apiFetch<ApiResponse<PaymentResponse>>(
    `/api/payments/${paymentId}`,
  );

  if (!result.success || !result.data) {
    throw new Error(result.message || "결제 상태를 확인하지 못했습니다.");
  }

  return result.data;
}
