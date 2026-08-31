export type AdminOrderStatus =
  | "ORDERED"
  | "PENDING_PAYMENT"
  | "PAID"
  | "PAYMENT_FAILED"
  | "PAYMENT_EXPIRED"
  | "CANCELLED";

export type AdminSellerApplicationStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface AdminDashboard {
  actionCenter: {
    pendingSellerApplications: number;
    pendingCancellations: number;
    pendingReturns: number;
    pendingExchanges: number;
  };
  summary: {
    totalUsers: number;
    activeSellers: number;
    sellingProducts: number;
    totalOrders: number;
  };
  recentOrders: AdminRecentOrder[];
  recentSellerApplications: AdminRecentSellerApplication[];
}

export interface AdminRecentOrder {
  id: number;
  orderNumber: string;
  status: AdminOrderStatus;
  totalAmount: number;
  orderedAt: string | null;
  createdAt: string;
}

export interface AdminRecentSellerApplication {
  id: number;
  storeName: string;
  applicantName: string;
  status: AdminSellerApplicationStatus;
  createdAt: string;
}

export type AdminUserRole = "USER" | "SELLER" | "ADMIN";
export type AdminAuthProvider = "GOOGLE" | "KAKAO";
export type AdminUserStatus = "ACTIVE" | "SUSPENDED" | "WITHDRAWN";
export type AdminSellerStatus = "ACTIVE" | "SUSPENDED" | "WITHDRAWN";

export interface AdminUserSummary {
  id: number;
  email: string | null;
  name: string;
  role: AdminUserRole;
  provider: AdminAuthProvider;
  status: AdminUserStatus;
  createdAt: string;
  activeSeller: boolean;
}

export interface AdminUserPage {
  content: AdminUserSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface AdminUserDetail extends Omit<AdminUserSummary, "activeSeller"> {
  profileImageUrl: string | null;
  updatedAt: string;
  seller: {
    sellerId: number;
    storeName: string;
    status: AdminSellerStatus;
    createdAt: string;
  } | null;
  latestSellerApplication: {
    applicationId: number;
    storeName: string;
    status: AdminSellerApplicationStatus;
    createdAt: string;
    reviewedAt: string | null;
  } | null;
  activity: {
    orders: number;
    reviews: number;
    inquiries: number;
  };
}

export interface AdminUserSearchParams {
  page?: number;
  size?: number;
  keyword?: string;
  role?: AdminUserRole;
  provider?: AdminAuthProvider;
  status?: AdminUserStatus;
}
