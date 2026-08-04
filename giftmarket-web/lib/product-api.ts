import { apiFetch } from "@/lib/api";
import type { ApiResponse } from "@/types/api";
import type {
  Category,
  ProductCreateRequest,
  ProductStatus,
  ProductStatusUpdateRequest,
  ProductStockUpdateRequest,
  ProductUpdateRequest,
  SellerProduct,
  SellerProductPage,
} from "@/types/product";

const JSON_HEADERS = {
  "Content-Type": "application/json",
};

export async function getCategories(): Promise<Category[]> {
  const response = await apiFetch<ApiResponse<Category[]>>("/api/categories");

  return response.data ?? [];
}

export async function createProduct(
  request: ProductCreateRequest,
): Promise<SellerProduct> {
  const response = await apiFetch<ApiResponse<SellerProduct>>(
    "/api/seller/products",
    {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify(request),
    },
  );

  if (!response.data) {
    throw new Error("상품 등록 결과를 확인할 수 없습니다.");
  }

  return response.data;
}

export async function getSellerProducts(
  page = 0,
  size = 20,
  status?: ProductStatus,
): Promise<SellerProductPage> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });

  if (status) {
    params.set("status", status);
  }

  const response = await apiFetch<ApiResponse<SellerProductPage>>(
    `/api/seller/products?${params.toString()}`,
  );

  if (!response.data) {
    throw new Error("상품 목록을 확인할 수 없습니다.");
  }

  return response.data;
}

export async function getSellerProduct(
  productId: number,
): Promise<SellerProduct> {
  const response = await apiFetch<ApiResponse<SellerProduct>>(
    `/api/seller/products/${productId}`,
  );

  if (!response.data) {
    throw new Error("상품 정보를 확인할 수 없습니다.");
  }

  return response.data;
}

export async function updateProduct(
  productId: number,
  request: ProductUpdateRequest,
): Promise<SellerProduct> {
  const response = await apiFetch<ApiResponse<SellerProduct>>(
    `/api/seller/products/${productId}`,
    {
      method: "PUT",
      headers: JSON_HEADERS,
      body: JSON.stringify(request),
    },
  );

  if (!response.data) {
    throw new Error("상품 수정 결과를 확인할 수 없습니다.");
  }

  return response.data;
}

export async function updateProductStatus(
  productId: number,
  request: ProductStatusUpdateRequest,
): Promise<SellerProduct> {
  const response = await apiFetch<ApiResponse<SellerProduct>>(
    `/api/seller/products/${productId}/status`,
    {
      method: "PATCH",
      headers: JSON_HEADERS,
      body: JSON.stringify(request),
    },
  );

  if (!response.data) {
    throw new Error("상품 상태 변경 결과를 확인할 수 없습니다.");
  }

  return response.data;
}

export async function updateProductStock(
  productId: number,
  request: ProductStockUpdateRequest,
): Promise<SellerProduct> {
  const response = await apiFetch<ApiResponse<SellerProduct>>(
    `/api/seller/products/${productId}/stock`,
    {
      method: "PATCH",
      headers: JSON_HEADERS,
      body: JSON.stringify(request),
    },
  );

  if (!response.data) {
    throw new Error("재고 변경 결과를 확인할 수 없습니다.");
  }

  return response.data;
}
