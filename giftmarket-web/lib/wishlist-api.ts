import { apiFetch } from "@/lib/api";
import type { ApiResponse } from "@/types/api";
import type { ProductSummary } from "@/types/product";

export async function getWishlist(): Promise<ProductSummary[]> {
  const result = await apiFetch<ApiResponse<ProductSummary[]>>("/api/wishlist");
  if (!result.success || !result.data) throw new Error(result.message || "찜 목록을 불러오지 못했습니다.");
  return result.data;
}

export async function addWishlist(productId: number): Promise<ProductSummary> {
  const result = await apiFetch<ApiResponse<ProductSummary>>(`/api/wishlist/${productId}`, { method: "POST" });
  if (!result.success || !result.data) throw new Error(result.message || "상품을 찜하지 못했습니다.");
  return result.data;
}

export async function removeWishlist(productId: number): Promise<void> {
  const result = await apiFetch<ApiResponse<null>>(`/api/wishlist/${productId}`, { method: "DELETE" });
  if (!result.success) throw new Error(result.message || "찜을 해제하지 못했습니다.");
}

export async function getWishlistCount(): Promise<number> {
  const result = await apiFetch<ApiResponse<number>>("/api/wishlist/count");
  if (!result.success || result.data === null || result.data === undefined) throw new Error(result.message || "찜 수를 불러오지 못했습니다.");
  return result.data;
}
