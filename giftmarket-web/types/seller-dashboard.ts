import type { SellerOrderStatus } from "@/types/seller-order";

export interface SellerDashboardReturnActions {
  total: number;
  approvalRequired: number;
  collectionRequired: number;
  receivingRequired: number;
  inspectionRequired: number;
}

export interface SellerDashboardExchangeActions {
  total: number;
  approvalRequired: number;
  collectionOrReceivingRequired: number;
  inspectionRequired: number;
  outboundRequired: number;
}

export interface SellerDashboardRecentOrder {
  sellerOrderId: number;
  orderId: number;
  orderNumber: string;
  orderedAt: string | null;
  representativeProductName: string;
  additionalProductCount: number;
  totalQuantity: number;
  totalProductAmount: number;
  status: SellerOrderStatus;
}

export interface SellerDashboard {
  storeName: string;
  actionRequired: {
    orders: number;
    cancellations: number;
    returns: SellerDashboardReturnActions;
    exchanges: SellerDashboardExchangeActions;
  };
  products: {
    onSale: number;
    soldOut: number;
  };
  recentOrders: SellerDashboardRecentOrder[];
}
