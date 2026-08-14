import { apiFetch } from "@/lib/api";
import type { ApiResponse } from "@/types/api";
import type { Address, AddressRequest } from "@/types/address";

export async function getMyAddresses(): Promise<Address[]> {
  const result = await apiFetch<ApiResponse<Address[]>>("/api/addresses", {
    method: "GET",
  });

  if (!result.success || !result.data) {
    throw new Error(result.message || "배송지 목록을 불러오지 못했습니다.");
  }

  return result.data;
}

export async function createAddress(request: AddressRequest): Promise<Address> {
  const result = await apiFetch<ApiResponse<Address>>("/api/addresses", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });

  if (!result.success || !result.data) {
    throw new Error(result.message || "배송지를 등록하지 못했습니다.");
  }

  return result.data;
}

export async function updateAddress(
  addressId: number,
  request: AddressRequest,
): Promise<Address> {
  const result = await apiFetch<ApiResponse<Address>>(
    `/api/addresses/${addressId}`,
    {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  if (!result.success || !result.data) {
    throw new Error(result.message || "배송지를 수정하지 못했습니다.");
  }

  return result.data;
}

export async function setDefaultAddress(addressId: number): Promise<Address> {
  const result = await apiFetch<ApiResponse<Address>>(
    `/api/addresses/${addressId}/default`,
    {
      method: "PATCH",
    },
  );

  if (!result.success || !result.data) {
    throw new Error(result.message || "기본 배송지를 변경하지 못했습니다.");
  }

  return result.data;
}

export async function deleteAddress(addressId: number): Promise<void> {
  const result = await apiFetch<ApiResponse<null>>(
    `/api/addresses/${addressId}`,
    {
      method: "DELETE",
    },
  );

  if (!result.success) {
    throw new Error(result.message || "배송지를 삭제하지 못했습니다.");
  }
}
