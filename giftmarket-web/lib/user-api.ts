import { apiFetch } from "@/lib/api";
import type { ApiResponse } from "@/types/api";

export async function withdrawMyAccount(): Promise<void> {
  const result = await apiFetch<ApiResponse<null>>("/api/users/me", {
    method: "DELETE",
  });

  if (!result.success) {
    throw new Error(result.message ?? "회원탈퇴에 실패했습니다.");
  }
}
