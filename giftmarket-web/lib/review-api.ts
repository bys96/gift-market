import { apiFetch } from "@/lib/api";
import type { ApiResponse } from "@/types/api";
import type { Review, ReviewEdit, ReviewEligibility, ReviewPage, ReviewRequest } from "@/types/review";

export async function getProductReviews(productId: number, page = 0): Promise<ReviewPage> {
  return (await apiFetch<ApiResponse<ReviewPage>>(`/api/products/${productId}/reviews?page=${page}&size=10`)).data;
}
export async function getReview(reviewId: number): Promise<ReviewEdit> { return (await apiFetch<ApiResponse<ReviewEdit>>(`/api/reviews/${reviewId}`)).data; }
export async function getReviewIds(ids: number[]): Promise<Record<string, number>> {
  if (!ids.length) return {};
  return (await apiFetch<ApiResponse<Record<string, number>>>(`/api/reviews/order-items?${ids.map(id => `ids=${id}`).join("&")}`)).data;
}
export async function getReviewEligibility(orderItemId: number): Promise<ReviewEligibility> { return (await apiFetch<ApiResponse<ReviewEligibility>>(`/api/reviews/order-items/${orderItemId}/eligibility`)).data; }
export async function createReview(request: ReviewRequest): Promise<Review> { return (await apiFetch<ApiResponse<Review>>("/api/reviews", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(request) })).data; }
export async function updateReview(reviewId: number, request: Omit<ReviewRequest, "orderItemId">): Promise<Review> { return (await apiFetch<ApiResponse<Review>>(`/api/reviews/${reviewId}`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify(request) })).data; }
export async function deleteReview(reviewId: number): Promise<void> { await apiFetch(`/api/reviews/${reviewId}`, { method: "DELETE" }); }
