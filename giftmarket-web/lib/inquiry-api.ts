import { apiFetch } from "@/lib/api";
import type { ApiResponse } from "@/types/api";
import type { ProductInquiry, ProductInquiryPage, ProductInquiryRequest, ProductInquiryStatus } from "@/types/inquiry";

function data<T>(response: ApiResponse<T>, message: string): T {
  if (!response.success || !response.data) throw new Error(response.message || message);
  return response.data;
}

export async function getProductInquiries(productId: number, page = 0) {
  return data(await apiFetch<ApiResponse<ProductInquiryPage>>(`/api/products/${productId}/inquiries?page=${page}&size=10`), "상품 문의를 불러오지 못했습니다.");
}
export async function createProductInquiry(productId: number, request: ProductInquiryRequest) {
  return data(await apiFetch<ApiResponse<ProductInquiry>>(`/api/products/${productId}/inquiries`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(request) }), "상품 문의를 등록하지 못했습니다.");
}
export async function updateProductInquiry(productId: number, inquiryId: number, request: ProductInquiryRequest) {
  return data(await apiFetch<ApiResponse<ProductInquiry>>(`/api/products/${productId}/inquiries/${inquiryId}`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify(request) }), "상품 문의를 수정하지 못했습니다.");
}
export async function deleteProductInquiry(productId: number, inquiryId: number) {
  await apiFetch<ApiResponse<null>>(`/api/products/${productId}/inquiries/${inquiryId}`, { method: "DELETE" });
}
export async function getSellerProductInquiries(status?: ProductInquiryStatus, page = 0) {
  const query = new URLSearchParams({ page: String(page), size: "20" }); if (status) query.set("status", status);
  return data(await apiFetch<ApiResponse<ProductInquiryPage>>(`/api/seller/product-inquiries?${query}`), "문의 목록을 불러오지 못했습니다.");
}
export async function getSellerProductInquiry(id: number) {
  return data(await apiFetch<ApiResponse<ProductInquiry>>(`/api/seller/product-inquiries/${id}`), "문의를 불러오지 못했습니다.");
}
export async function answerProductInquiry(id: number, content: string) {
  return data(await apiFetch<ApiResponse<ProductInquiry>>(`/api/seller/product-inquiries/${id}/answer`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ content }) }), "답변을 저장하지 못했습니다.");
}
