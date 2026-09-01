import { apiFetch } from "@/lib/api";
import type {
  AdminDashboard,
  AdminUserDetail,
  AdminUserPage,
  AdminUserSearchParams,
  AdminUserStatusChangeRequest,
  AdminSellerDetail,
  AdminSellerPage,
  AdminSellerSearchParams,
  AdminProductDetail,
  AdminProductPage,
  AdminProductSearchParams,
  AdminProductStatusChangeRequest,
  AdminOrderDetail, AdminOrderPage, AdminOrderSearchParams,
  AdminCancellationDetail, AdminCancellationPage, AdminCancellationSearchParams,
  AdminReturnDetail, AdminReturnPage, AdminReturnSearchParams,
  AdminExchangeDetail, AdminExchangePage, AdminExchangeSearchParams,
} from "@/types/admin";
import type { ApiResponse } from "@/types/api";

export async function getAdminDashboard(): Promise<AdminDashboard> {
  const response = await apiFetch<ApiResponse<AdminDashboard>>(
    "/api/admin/dashboard",
  );

  if (!response.data) {
    throw new Error("관리자 대시보드 정보를 불러오지 못했습니다.");
  }

  return response.data;
}

export async function getAdminUsers(
  search: AdminUserSearchParams,
): Promise<AdminUserPage> {
  const params = new URLSearchParams({
    page: String(search.page ?? 0),
    size: String(search.size ?? 20),
  });
  if (search.keyword) params.set("keyword", search.keyword);
  if (search.role) params.set("role", search.role);
  if (search.provider) params.set("provider", search.provider);
  if (search.status) params.set("status", search.status);

  const response = await apiFetch<ApiResponse<AdminUserPage>>(
    `/api/admin/users?${params.toString()}`,
  );
  if (!response.data) throw new Error("회원 목록을 불러오지 못했습니다.");
  return response.data;
}

export async function getAdminUser(userId: number): Promise<AdminUserDetail> {
  const response = await apiFetch<ApiResponse<AdminUserDetail>>(
    `/api/admin/users/${userId}`,
  );
  if (!response.data) throw new Error("회원 정보를 불러오지 못했습니다.");
  return response.data;
}

export async function suspendAdminUser(
  userId: number,
  request: AdminUserStatusChangeRequest,
): Promise<AdminUserDetail> {
  const response = await apiFetch<ApiResponse<AdminUserDetail>>(
    `/api/admin/users/${userId}/suspend`,
    { method: "PATCH", body: JSON.stringify(request) },
  );
  if (!response.data) throw new Error("회원 이용 정지에 실패했습니다.");
  return response.data;
}

export async function reactivateAdminUser(
  userId: number,
  request: AdminUserStatusChangeRequest,
): Promise<AdminUserDetail> {
  const response = await apiFetch<ApiResponse<AdminUserDetail>>(
    `/api/admin/users/${userId}/reactivate`,
    { method: "PATCH", body: JSON.stringify(request) },
  );
  if (!response.data) throw new Error("회원 정지 해제에 실패했습니다.");
  return response.data;
}

export async function getAdminSellers(
  search: AdminSellerSearchParams,
): Promise<AdminSellerPage> {
  const params = new URLSearchParams({
    page: String(search.page ?? 0),
    size: String(search.size ?? 20),
  });
  if (search.keyword) params.set("keyword", search.keyword);
  if (search.status) params.set("status", search.status);
  const response = await apiFetch<ApiResponse<AdminSellerPage>>(
    `/api/admin/sellers?${params.toString()}`,
  );
  if (!response.data) throw new Error("판매자 목록을 불러오지 못했습니다.");
  return response.data;
}

export async function getAdminSeller(sellerId: number): Promise<AdminSellerDetail> {
  const response = await apiFetch<ApiResponse<AdminSellerDetail>>(
    `/api/admin/sellers/${sellerId}`,
  );
  if (!response.data) throw new Error("판매자 정보를 불러오지 못했습니다.");
  return response.data;
}

export async function getAdminProducts(search: AdminProductSearchParams): Promise<AdminProductPage> {
  const params = new URLSearchParams({ page: String(search.page ?? 0), size: String(search.size ?? 20) });
  if (search.keyword) params.set("keyword", search.keyword);
  if (search.status) params.set("status", search.status);
  if (search.sellerId) params.set("sellerId", String(search.sellerId));
  if (search.categoryId) params.set("categoryId", String(search.categoryId));
  if (search.deleted && search.deleted !== "ALL") params.set("deleted", search.deleted);
  const response = await apiFetch<ApiResponse<AdminProductPage>>(`/api/admin/products?${params.toString()}`);
  if (!response.data) throw new Error("상품 목록을 불러오지 못했습니다.");
  return response.data;
}

export async function getAdminProduct(productId: number): Promise<AdminProductDetail> {
  const response = await apiFetch<ApiResponse<AdminProductDetail>>(`/api/admin/products/${productId}`);
  if (!response.data) throw new Error("상품 정보를 불러오지 못했습니다.");
  return response.data;
}

export async function hideAdminProduct(
  productId: number,
  request: AdminProductStatusChangeRequest,
): Promise<void> {
  await apiFetch<ApiResponse<null>>(`/api/admin/products/${productId}/hide`, {
    method: "PATCH",
    body: JSON.stringify(request),
  });
}

export async function unhideAdminProduct(
  productId: number,
  request: AdminProductStatusChangeRequest,
): Promise<void> {
  await apiFetch<ApiResponse<null>>(`/api/admin/products/${productId}/unhide`, {
    method: "PATCH",
    body: JSON.stringify(request),
  });
}

export async function getAdminOrders(search: AdminOrderSearchParams): Promise<AdminOrderPage> {
  const params = new URLSearchParams({ page: String(search.page ?? 0), size: String(search.size ?? 20) });
  if (search.keyword) params.set("keyword", search.keyword);
  if (search.orderStatus) params.set("orderStatus", search.orderStatus);
  if (search.paymentStatus) params.set("paymentStatus", search.paymentStatus);
  if (search.sellerOrderStatus) params.set("sellerOrderStatus", search.sellerOrderStatus);
  const response = await apiFetch<ApiResponse<AdminOrderPage>>(`/api/admin/orders?${params.toString()}`);
  if (!response.data) throw new Error("주문 목록을 불러오지 못했습니다.");
  return response.data;
}

export async function getAdminOrder(orderId: number): Promise<AdminOrderDetail> {
  const response = await apiFetch<ApiResponse<AdminOrderDetail>>(`/api/admin/orders/${orderId}`);
  if (!response.data) throw new Error("주문 정보를 불러오지 못했습니다.");
  return response.data;
}
export async function getAdminCancellations(search:AdminCancellationSearchParams):Promise<AdminCancellationPage>{const p=new URLSearchParams({page:String(search.page??0),size:String(search.size??20)});if(search.keyword)p.set("keyword",search.keyword);if(search.status)p.set("status",search.status);if(search.requiresSellerApproval!==undefined)p.set("requiresSellerApproval",String(search.requiresSellerApproval));const r=await apiFetch<ApiResponse<AdminCancellationPage>>(`/api/admin/cancellations?${p}`);if(!r.data)throw new Error("취소 요청 목록을 불러오지 못했습니다.");return r.data;}
export async function getAdminCancellation(id:number):Promise<AdminCancellationDetail>{const r=await apiFetch<ApiResponse<AdminCancellationDetail>>(`/api/admin/cancellations/${id}`);if(!r.data)throw new Error("취소 요청 정보를 불러오지 못했습니다.");return r.data;}
export async function getAdminReturns(s:AdminReturnSearchParams):Promise<AdminReturnPage>{const p=new URLSearchParams({page:String(s.page??0),size:String(s.size??20)});if(s.keyword)p.set("keyword",s.keyword);if(s.status)p.set("status",s.status);if(s.responsibility)p.set("responsibility",s.responsibility);const r=await apiFetch<ApiResponse<AdminReturnPage>>(`/api/admin/returns?${p}`);if(!r.data)throw new Error("반품 요청 목록을 불러오지 못했습니다.");return r.data}export async function getAdminReturn(id:number):Promise<AdminReturnDetail>{const r=await apiFetch<ApiResponse<AdminReturnDetail>>(`/api/admin/returns/${id}`);if(!r.data)throw new Error("반품 요청 정보를 불러오지 못했습니다.");return r.data}
export async function getAdminExchanges(s:AdminExchangeSearchParams):Promise<AdminExchangePage>{const p=new URLSearchParams({page:String(s.page??0),size:String(s.size??20)});if(s.keyword)p.set("keyword",s.keyword);if(s.status)p.set("status",s.status);if(s.responsibility)p.set("responsibility",s.responsibility);const r=await apiFetch<ApiResponse<AdminExchangePage>>(`/api/admin/exchanges?${p}`);if(!r.data)throw new Error("교환 요청 목록을 불러오지 못했습니다.");return r.data}export async function getAdminExchange(id:number):Promise<AdminExchangeDetail>{const r=await apiFetch<ApiResponse<AdminExchangeDetail>>(`/api/admin/exchanges/${id}`);if(!r.data)throw new Error("교환 요청 정보를 불러오지 못했습니다.");return r.data}
