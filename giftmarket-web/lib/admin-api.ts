import { apiFetch } from "@/lib/api";
import type {
  AdminDashboard,
  AdminUserDetail,
  AdminUserPage,
  AdminUserSearchParams,
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
