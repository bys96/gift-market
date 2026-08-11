import { apiFetch } from "@/lib/api";
import type { ApiResponse } from "@/types/api";
import type {
  Category,
  ProductCreateRequest,
  ProductDetail,
  ProductOptionResponse,
  ProductOptionUpdateRequest,
  ProductPage,
  ProductRegistrationRequest,
  ProductRegistrationResponse,
  ProductStatus,
  ProductStatusUpdateRequest,
  ProductStockUpdateRequest,
  ProductUpdateRequest,
  ProductVariantListResponse,
  ProductVariantUpdateRequest,
  SellerProduct,
  SellerProductPage,
} from "@/types/product";

const JSON_HEADERS = {
  "Content-Type": "application/json",
};

interface GetProductsParams {
  page?: number;
  size?: number;

  // 기존 화면 호환용
  categoryId?: number;

  // 신규 다중 카테고리 필터
  categoryIds?: number[];

  keyword?: string;
  excludeSoldOut?: boolean;
}

export async function getCategories(): Promise<Category[]> {
  const response = await apiFetch<ApiResponse<Category[]>>("/api/categories");

  return response.data ?? [];
}

export async function getProducts({
  page = 0,
  size = 20,
  categoryId,
  categoryIds,
  keyword,
  excludeSoldOut = false,
}: GetProductsParams = {}): Promise<ProductPage> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
    excludeSoldOut: String(excludeSoldOut),
  });

  const normalizedCategoryIds = [
    ...(categoryIds ?? []),
    ...(categoryId !== undefined ? [categoryId] : []),
  ].filter(
    (id, index, ids) =>
      Number.isInteger(id) && id > 0 && ids.indexOf(id) === index,
  );

  normalizedCategoryIds.forEach((id) => {
    params.append("categoryIds", String(id));
  });

  const normalizedKeyword = keyword?.trim();

  if (normalizedKeyword) {
    params.set("keyword", normalizedKeyword);
  }

  const response = await apiFetch<ApiResponse<ProductPage>>(
    `/api/products?${params.toString()}`,
  );

  if (!response.data) {
    throw new Error("상품 목록을 확인할 수 없습니다.");
  }

  return response.data;
}

export async function getProduct(productId: number): Promise<ProductDetail> {
  const response = await apiFetch<ApiResponse<ProductDetail>>(
    `/api/products/${productId}`,
  );

  if (!response.data) {
    throw new Error("상품 정보를 확인할 수 없습니다.");
  }

  return response.data;
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

export async function registerProduct(
  request: ProductRegistrationRequest,
): Promise<ProductRegistrationResponse> {
  const response = await apiFetch<ApiResponse<ProductRegistrationResponse>>(
    "/api/seller/products/registration",
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

export async function getProductOptions(
  productId: number,
): Promise<ProductOptionResponse> {
  const response = await apiFetch<ApiResponse<ProductOptionResponse>>(
    `/api/seller/products/${productId}/options`,
  );

  if (!response.data) {
    throw new Error("상품 옵션 정보를 확인할 수 없습니다.");
  }

  return response.data;
}

export async function updateProductOptions(
  productId: number,
  request: ProductOptionUpdateRequest,
): Promise<ProductOptionResponse> {
  const response = await apiFetch<ApiResponse<ProductOptionResponse>>(
    `/api/seller/products/${productId}/options`,
    {
      method: "PUT",
      headers: JSON_HEADERS,
      body: JSON.stringify(request),
    },
  );

  if (!response.data) {
    throw new Error("상품 옵션 저장 결과를 확인할 수 없습니다.");
  }

  return response.data;
}

export async function getProductVariants(
  productId: number,
): Promise<ProductVariantListResponse> {
  const response = await apiFetch<ApiResponse<ProductVariantListResponse>>(
    `/api/seller/products/${productId}/variants`,
  );

  if (!response.data) {
    throw new Error("상품 Variant 정보를 확인할 수 없습니다.");
  }

  return response.data;
}

export async function updateProductVariants(
  productId: number,
  request: ProductVariantUpdateRequest,
): Promise<ProductVariantListResponse> {
  const response = await apiFetch<ApiResponse<ProductVariantListResponse>>(
    `/api/seller/products/${productId}/variants`,
    {
      method: "PUT",
      headers: JSON_HEADERS,
      body: JSON.stringify(request),
    },
  );

  if (!response.data) {
    throw new Error("상품 Variant 저장 결과를 확인할 수 없습니다.");
  }

  return response.data;
}
