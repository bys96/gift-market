import type { PaymentStatus } from "@/types/order";

export interface PaymentResponse {
  paymentId: number;
  orderId: number;
  status: PaymentStatus;
  amount: number;
  method: string | null;
  easyPayProvider: string | null;
  approvedAt: string | null;
  expiresAt: string;
  userMessage: string;
}

export interface PaymentConfirmRequest {
  providerPaymentKey: string;
  merchantPaymentId: string;
  amount: number;
}

export interface PaymentSession {
  paymentId: number;
  orderId: number;
  merchantPaymentId: string;
  amount: number;
  orderName: string;
  expiresAt: string;
  customerKey: string;
  returnPath: string;
}
