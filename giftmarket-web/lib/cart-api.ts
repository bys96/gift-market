import { apiFetch } from "@/lib/api";
import type {
  Cart,
  CartItemBulkDeleteRequest,
  CartItemCreateRequest,
  CartItemQuantityUpdateRequest,
} from "@/types/cart";
import type { ApiResponse } from "@/types/api";

export async function getCart(): Promise<Cart> {
  const result = await apiFetch<ApiResponse<Cart>>("/api/cart", {
    method: "GET",
  });

  if (!result.success || !result.data) {
    throw new Error(result.message || "장바구니를 불러오지 못했습니다.");
  }

  return result.data;
}

export async function addCartItem(
  request: CartItemCreateRequest,
): Promise<Cart> {
  const result = await apiFetch<ApiResponse<Cart>>("/api/cart/items", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });

  if (!result.success || !result.data) {
    throw new Error(result.message || "장바구니에 상품을 담지 못했습니다.");
  }

  return result.data;
}

export async function updateCartItemQuantity(
  cartItemId: number,
  request: CartItemQuantityUpdateRequest,
): Promise<Cart> {
  const result = await apiFetch<ApiResponse<Cart>>(
    `/api/cart/items/${cartItemId}`,
    {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  if (!result.success || !result.data) {
    throw new Error(result.message || "장바구니 수량을 변경하지 못했습니다.");
  }

  return result.data;
}

export async function deleteCartItem(cartItemId: number): Promise<Cart> {
  const result = await apiFetch<ApiResponse<Cart>>(
    `/api/cart/items/${cartItemId}`,
    {
      method: "DELETE",
    },
  );

  if (!result.success || !result.data) {
    throw new Error(result.message || "장바구니 상품을 삭제하지 못했습니다.");
  }

  return result.data;
}

export async function deleteCartItems(
  request: CartItemBulkDeleteRequest,
): Promise<Cart> {
  const result = await apiFetch<ApiResponse<Cart>>("/api/cart/items", {
    method: "DELETE",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });

  if (!result.success || !result.data) {
    throw new Error(
      result.message || "선택한 장바구니 상품을 삭제하지 못했습니다.",
    );
  }

  return result.data;
}

export async function clearCart(): Promise<void> {
  const result = await apiFetch<ApiResponse<null>>("/api/cart", {
    method: "DELETE",
  });

  if (!result.success) {
    throw new Error(result.message || "장바구니를 비우지 못했습니다.");
  }
}
