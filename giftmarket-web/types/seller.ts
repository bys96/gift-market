export type SellerApplicationStatus = "PENDING" | "APPROVED" | "REJECTED";

export type SellerStatus = "ACTIVE" | "SUSPENDED" | "WITHDRAWN";

export interface Seller {
  id: number;
  storeName: string;
  introduction: string | null;
  status: SellerStatus;
}

export interface SellerApplication {
  id: number;
  userId: number;
  userName: string;
  userEmail: string;
  storeName: string;
  introduction: string;
  status: SellerApplicationStatus;
  rejectionReason: string | null;
  createdAt: string;
  reviewedAt: string | null;
}

export interface SellerApplicationCreateRequest {
  storeName: string;
  introduction: string;
}

export interface SellerApplicationRejectRequest {
  rejectionReason: string;
}

export const sellerApplicationStatusLabel: Record<
  SellerApplicationStatus,
  string
> = {
  PENDING: "심사 대기",
  APPROVED: "승인",
  REJECTED: "거절",
};

export const sellerStatusLabel: Record<SellerStatus, string> = {
  ACTIVE: "운영 중",
  SUSPENDED: "운영 정지",
  WITHDRAWN: "탈퇴",
};
