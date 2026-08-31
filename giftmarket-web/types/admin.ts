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

export type AdminSellerOrderStatus = "PENDING_PAYMENT" | "PAID" | "PREPARING" | "SHIPPED" | "DELIVERED" | "CANCELLED";

export interface AdminSellerSummary {
  sellerId: number;
  storeName: string;
  status: AdminSellerStatus;
  createdAt: string;
  userId: number;
  userName: string;
  userEmail: string | null;
  onSaleProductCount: number;
}

export interface AdminSellerPage {
  content: AdminSellerSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface AdminSellerDetail {
  sellerId: number;
  storeName: string;
  introduction: string | null;
  status: AdminSellerStatus;
  approvedAt: string;
  createdAt: string;
  updatedAt: string;
  owner: {
    userId: number;
    name: string;
    email: string | null;
    role: AdminUserRole;
    provider: AdminAuthProvider;
    status: AdminUserStatus;
    createdAt: string;
  };
  sellerApplication: {
    applicationId: number;
    status: AdminSellerApplicationStatus;
    appliedAt: string;
    reviewedAt: string | null;
    reviewedBy: number | null;
  } | null;
  activity: {
    totalProducts: number;
    onSaleProducts: number;
    totalOrders: number;
  };
  recentOrders: Array<{
    sellerOrderId: number;
    orderId: number;
    orderNumber: string;
    status: AdminSellerOrderStatus;
    totalProductAmount: number;
    orderedAt: string;
  }>;
}

export interface AdminSellerSearchParams {
  page?: number;
  size?: number;
  keyword?: string;
  status?: AdminSellerStatus;
}

export type AdminProductStatus = "DRAFT" | "ON_SALE" | "SOLD_OUT" | "HIDDEN";
export type AdminProductDeletedFilter = "ALL" | "ACTIVE" | "DELETED";

export interface AdminProductSummary {
  productId: number;
  productName: string;
  representativeImageKey: string | null;
  status: AdminProductStatus;
  deleted: boolean;
  createdAt: string;
  sellerId: number;
  storeName: string;
  price: number;
  availableStock: number;
}

export interface AdminProductPage {
  content: AdminProductSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface AdminProductDetail extends Omit<AdminProductSummary, "productName"> {
  name: string;
  brandName: string | null;
  summary: string | null;
  description: string | null;
  deletedAt: string | null;
  updatedAt: string;
  freeShipping: boolean;
  shippingFee: number;
  shippingPreparationDays: number;
  returnShippingFee: number;
  exchangeShippingFee: number;
  seller: { sellerId: number; storeName: string; status: AdminSellerStatus; ownerUserId: number };
  category: { categoryId: number; categoryName: string; parentCategoryId: number | null; parentCategoryName: string | null };
  galleryImageKeys: string[];
  optionGroups: Array<{ optionGroupId: number; name: string; sortOrder: number; values: Array<{ optionValueId: number; value: string; sortOrder: number }> }>;
  variants: Array<{ variantId: number; skuCode: string; combinationKey: string; additionalPrice: number; price: number; stockQuantity: number; active: boolean; optionValues: Array<{ optionValueId: number; groupName: string; value: string }> }>;
  operationSummary: { reviewCount: number; averageRating: number; inquiryCount: number };
}

export interface AdminProductSearchParams {
  page?: number;
  size?: number;
  keyword?: string;
  status?: AdminProductStatus;
  sellerId?: number;
  categoryId?: number;
  deleted?: AdminProductDeletedFilter;
}
