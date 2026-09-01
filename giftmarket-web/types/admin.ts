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
export type AdminSellerStatus = "ACTIVE" | "SALES_SUSPENDED" | "SUSPENDED" | "WITHDRAWN";

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

export interface AdminUserStatusChangeRequest {
  reason: string;
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

export interface AdminSellerSalesStatusChangeRequest {
  reason: string;
}

export type AdminProductStatus = "DRAFT" | "ON_SALE" | "SOLD_OUT" | "HIDDEN";
export type AdminProductDeletedFilter = "ALL" | "ACTIVE" | "DELETED";

export interface AdminProductSummary {
  productId: number;
  productName: string;
  representativeImageKey: string | null;
  status: AdminProductStatus;
  adminHidden: boolean;
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
  adminHiddenReason: string | null;
  adminHiddenAt: string | null;
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

export interface AdminProductStatusChangeRequest {
  reason: string;
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

export type AdminPaymentStatus = "READY" | "CONFIRMING" | "PAID" | "PARTIALLY_CANCELED" | "FAILED" | "EXPIRED" | "CANCELING" | "CANCELED";
export type AdminShipmentType = "ORIGINAL_OUTBOUND" | "RETURN_COLLECTION" | "EXCHANGE_COLLECTION" | "EXCHANGE_OUTBOUND";
export type AdminShipmentStatus = "READY" | "SHIPPED" | "DELIVERED" | "CANCELED";
export interface AdminOrderSummary {
  orderId: number; orderNumber: string; orderStatus: AdminOrderStatus; orderedAt: string | null;
  totalProductAmount: number; totalShippingFee: number; totalAmount: number;
  userId: number; userName: string; userEmail: string | null; paymentId: number | null; paymentStatus: AdminPaymentStatus | null;
  representativeProductName: string | null; productTypeCount: number; totalItemCount: number;
  sellerOrderCount: number; sellerOrderStatuses: AdminSellerOrderStatus[];
}
export interface AdminOrderPage { content: AdminOrderSummary[]; page: number; size: number; totalElements: number; totalPages: number; first: boolean; last: boolean; }
export interface AdminOrderSearchParams { page?: number; size?: number; keyword?: string; orderStatus?: AdminOrderStatus; paymentStatus?: AdminPaymentStatus; sellerOrderStatus?: AdminSellerOrderStatus; }
export interface AdminOrderDetail {
  orderId: number; orderNumber: string; orderStatus: AdminOrderStatus; orderedAt: string | null;
  totalProductAmount: number; totalShippingFee: number; totalAmount: number;
  buyer: { userId: number; name: string; email: string | null; role: AdminUserRole; status: AdminUserStatus };
  recipient: { name: string; phone: string; postalCode: string; address: string; detailAddress: string | null };
  payment: null | { paymentId: number; provider: string; status: AdminPaymentStatus; amount: number; currency: string; method: string | null; easyPayProvider: string | null; providerStatus: string | null; requestedAt: string; approvedAt: string | null; cancelledAt: string | null };
  sellerOrders: Array<{ sellerOrderId: number; sellerId: number; storeName: string; sellerStatus: AdminSellerStatus; status: AdminSellerOrderStatus; shippingCompany: string | null; trackingNumber: string | null; preparedAt: string | null; shippedAt: string | null; deliveredAt: string | null;
    items: Array<{ orderItemId: number; productId: number; productName: string; optionSnapshot: string | null; unitPrice: number; quantity: number; totalPrice: number; shippingFee: number; canceledQuantity: number; returnedQuantity: number; exchangedQuantity: number; confirmedQuantity: number }>;
    shipments: Array<{ shipmentId: number; type: AdminShipmentType; status: AdminShipmentStatus; shippingCompany: string; trackingNumber: string; shippedAt: string | null; deliveredAt: string | null }>;
  }>;
  claims: { cancellationCount: number; returnCount: number; exchangeCount: number };
  refund: { succeededCount: number; succeededAmount: number };
}

export type AdminCancellationStatus = "REQUESTED" | "PROCESSING" | "COMPLETED" | "REJECTED" | "FAILED";
export type AdminPaymentCancellationStatus = "REQUESTED" | "SUCCEEDED" | "FAILED";
export interface AdminCancellationSummary { cancellationId:number;status:AdminCancellationStatus;requiresSellerApproval:boolean;requestedAt:string;completedAt:string|null;rejectedAt:string|null;failedAt:string|null;orderId:number;orderNumber:string;userId:number;userName:string;userEmail:string|null;sellerOrderId:number;sellerId:number;storeName:string;representativeProductName:string|null;productTypeCount:number;requestedQuantity:number;refundAmount:number|null;refundStatus:AdminPaymentCancellationStatus|null; }
export interface AdminCancellationPage {content:AdminCancellationSummary[];page:number;size:number;totalElements:number;totalPages:number;first:boolean;last:boolean;}
export interface AdminCancellationSearchParams {page?:number;size?:number;keyword?:string;status?:AdminCancellationStatus;requiresSellerApproval?:boolean;}
export interface AdminCancellationDetail {cancellationId:number;status:AdminCancellationStatus;requiresSellerApproval:boolean;reason:string;rejectedReason:string|null;requestedAt:string;processingAt:string|null;completedAt:string|null;rejectedAt:string|null;failedAt:string|null;order:{orderId:number;orderNumber:string;status:AdminOrderStatus;orderedAt:string|null};buyer:{userId:number;name:string;email:string|null};seller:{sellerOrderId:number;sellerOrderStatus:AdminSellerOrderStatus;sellerId:number;storeName:string};items:Array<{cancellationItemId:number;orderItemId:number;productId:number;productName:string;optionSnapshot:string|null;unitPrice:number;originalQuantity:number;cancelQuantity:number;canceledQuantity:number;shippingFee:number}>;payment:{paymentId:number;status:AdminPaymentStatus;originalAmount:number;succeededRefundAmount:number}|null;paymentCancellation:{paymentCancellationId:number;type:"FULL"|"PARTIAL";status:AdminPaymentCancellationStatus;amount:number;requestedAt:string;canceledAt:string|null;failedAt:string|null;failureCode:string|null}|null;}

export type AdminReturnStatus="REQUESTED"|"APPROVED"|"COLLECTING"|"RECEIVED"|"INSPECTED"|"REFUNDING"|"COMPLETED"|"REJECTED"|"CANCELED"|"FAILED";export type AdminReturnResponsibility="BUYER"|"SELLER";export type AdminReturnInspectionResult="RESTOCKABLE"|"NON_RESTOCKABLE";
export interface AdminReturnSummary{returnId:number;status:AdminReturnStatus;responsibility:AdminReturnResponsibility|null;requestedAt:string;orderId:number;orderNumber:string;userId:number;userName:string;userEmail:string|null;sellerOrderId:number;sellerId:number;storeName:string;representativeProductName:string|null;productTypeCount:number;requestedQuantity:number;refundAmount:number|null;refundStatus:AdminPaymentCancellationStatus|null;collectionStatus:AdminShipmentStatus|null}
export interface AdminReturnPage{content:AdminReturnSummary[];page:number;size:number;totalElements:number;totalPages:number;first:boolean;last:boolean}export interface AdminReturnSearchParams{page?:number;size?:number;keyword?:string;status?:AdminReturnStatus;responsibility?:AdminReturnResponsibility}
export interface AdminReturnDetail{returnId:number;status:AdminReturnStatus;reasonType:string;reason:string;responsibility:AdminReturnResponsibility|null;rejectedReason:string|null;requestedAt:string;approvedAt:string|null;collectingAt:string|null;receivedAt:string|null;inspectedAt:string|null;refundingAt:string|null;completedAt:string|null;rejectedAt:string|null;canceledAt:string|null;failedAt:string|null;order:{orderId:number;orderNumber:string;status:AdminOrderStatus;orderedAt:string|null};buyer:{userId:number;name:string;email:string|null};seller:{sellerOrderId:number;status:AdminSellerOrderStatus;sellerId:number;storeName:string};items:Array<{returnRequestItemId:number;orderItemId:number;productId:number;productName:string;optionSnapshot:string|null;unitPrice:number;originalQuantity:number;returnQuantity:number;returnedQuantity:number;inspectionResult:AdminReturnInspectionResult|null;restockedQuantity:number}>;evidence:Array<{imageId:number;url:string;sortOrder:number}>;collection:{shipmentId:number;type:AdminShipmentType;status:AdminShipmentStatus;shippingCompany:string;trackingNumber:string;shippedAt:string|null;deliveredAt:string|null}|null;refundCalculation:{productRefundAmount:number|null;originalShippingRefundAmount:number|null;returnShippingCharge:number|null;refundAmount:number|null};payment:{paymentId:number;status:AdminPaymentStatus;originalAmount:number;succeededRefundAmount:number}|null;paymentCancellation:{paymentCancellationId:number;type:"FULL"|"PARTIAL";status:AdminPaymentCancellationStatus;amount:number;requestedAt:string;canceledAt:string|null;failedAt:string|null;failureCode:string|null}|null}

export type AdminExchangeStatus="REQUESTED"|"APPROVED"|"PAYMENT_PENDING"|"COLLECTING"|"RECEIVED"|"INSPECTED"|"RESHIPPING"|"COMPLETED"|"REJECTED"|"CANCELED"|"FAILED";export type AdminExchangeResponsibility="BUYER"|"SELLER";export type AdminExchangeShippingPaymentStatus="READY"|"REQUESTED"|"SUCCEEDED"|"FAILED"|"EXPIRED"|"COMPENSATION_REQUIRED";
export interface AdminExchangeSummary{exchangeId:number;status:AdminExchangeStatus;responsibility:AdminExchangeResponsibility|null;requestedAt:string;orderId:number;orderNumber:string;userId:number;userName:string;userEmail:string|null;sellerOrderId:number;sellerId:number;storeName:string;representativeProductName:string|null;productTypeCount:number;requestedQuantity:number;collectionStatus:AdminShipmentStatus|null;outboundStatus:AdminShipmentStatus|null;shippingPaymentAmount:number|null;shippingPaymentStatus:AdminExchangeShippingPaymentStatus|null}export interface AdminExchangePage{content:AdminExchangeSummary[];page:number;size:number;totalElements:number;totalPages:number;first:boolean;last:boolean}export interface AdminExchangeSearchParams{page?:number;size?:number;keyword?:string;status?:AdminExchangeStatus;responsibility?:AdminExchangeResponsibility}
export interface AdminExchangeShipment{shipmentId:number;type:AdminShipmentType;status:AdminShipmentStatus;shippingCompany:string;trackingNumber:string;shippedAt:string|null;deliveredAt:string|null}export interface AdminExchangeDetail{exchangeId:number;status:AdminExchangeStatus;reasonType:string;reason:string;responsibility:AdminExchangeResponsibility|null;rejectedReason:string|null;requestedAt:string;approvedAt:string|null;paymentPendingAt:string|null;paymentDueAt:string|null;collectingAt:string|null;receivedAt:string|null;inspectedAt:string|null;reshippingAt:string|null;completedAt:string|null;rejectedAt:string|null;canceledAt:string|null;failedAt:string|null;order:{orderId:number;orderNumber:string;status:AdminOrderStatus;orderedAt:string|null};buyer:{userId:number;name:string;email:string|null};seller:{sellerOrderId:number;status:AdminSellerOrderStatus;sellerId:number;storeName:string};items:Array<{exchangeItemId:number;orderItemId:number;productId:number;productName:string;originalQuantity:number;exchangeQuantity:number;exchangedQuantity:number;originalOptionSnapshot:string|null;originalVariantId:number|null;originalSku:string|null;originalUnitPrice:number;targetOptionSnapshot:string|null;targetVariantId:number|null;targetSku:string|null;targetUnitPrice:number;sameVariant:boolean;inspectionResult:AdminReturnInspectionResult|null;restockedQuantity:number;reservedQuantity:number;releasedQuantity:number;consumedQuantity:number}>;collectionShipment:AdminExchangeShipment|null;outboundShipment:AdminExchangeShipment|null;shippingPayment:{id:number;amount:number;status:AdminExchangeShippingPaymentStatus;requestedAt:string|null;succeededAt:string|null;failedAt:string|null;expiredAt:string|null;failureCode:string|null}|null}
