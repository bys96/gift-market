import { apiFetch } from "@/lib/api";
import type { ApiResponse } from "@/types/api";
import type {
  ProductDraft,
  ProductDraftCreateRequest,
  ProductDraftUpdateRequest,
} from "@/types/product-draft";

const JSON_HEADERS = {
  "Content-Type": "application/json",
};

export async function createProductDraft(
  request: ProductDraftCreateRequest,
): Promise<ProductDraft> {
  const response = await apiFetch<ApiResponse<ProductDraft>>(
    "/api/seller/product-drafts",
    {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify(request),
    },
  );

  if (!response.data) {
    throw new Error("상품 임시저장 결과를 확인할 수 없습니다.");
  }

  return response.data;
}

export async function getProductDraft(draftId: number): Promise<ProductDraft> {
  const response = await apiFetch<ApiResponse<ProductDraft>>(
    `/api/seller/product-drafts/${draftId}`,
  );

  if (!response.data) {
    throw new Error("임시저장 상품을 확인할 수 없습니다.");
  }

  return response.data;
}

export async function getProductDraftByProductId(
  productId: number,
): Promise<ProductDraft> {
  const response = await apiFetch<ApiResponse<ProductDraft>>(
    `/api/seller/product-drafts?productId=${productId}`,
  );

  if (!response.data) {
    throw new Error("상품의 임시저장 내용을 확인할 수 없습니다.");
  }

  return response.data;
}

export async function getProductDrafts(): Promise<ProductDraft[]> {
  const response = await apiFetch<ApiResponse<ProductDraft[]>>(
    "/api/seller/product-drafts",
  );

  return response.data ?? [];
}

export async function getNewProductDrafts(): Promise<ProductDraft[]> {
  const response = await apiFetch<ApiResponse<ProductDraft[]>>(
    "/api/seller/product-drafts?newOnly=true",
  );

  return response.data ?? [];
}

export async function updateProductDraft(
  draftId: number,
  request: ProductDraftUpdateRequest,
): Promise<ProductDraft> {
  const response = await apiFetch<ApiResponse<ProductDraft>>(
    `/api/seller/product-drafts/${draftId}`,
    {
      method: "PUT",
      headers: JSON_HEADERS,
      body: JSON.stringify(request),
    },
  );

  if (!response.data) {
    throw new Error("상품 임시저장 결과를 확인할 수 없습니다.");
  }

  return response.data;
}

export async function deleteProductDraft(draftId: number): Promise<void> {
  await apiFetch<ApiResponse<null>>(`/api/seller/product-drafts/${draftId}`, {
    method: "DELETE",
  });
}
