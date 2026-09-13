import { apiFetch } from "@/lib/api";
import type { ApiResponse } from "@/types/api";
import type {
  NotificationContext,
  NotificationPage,
  NotificationUnreadCount,
} from "@/types/notification";

const API_PATHS: Record<NotificationContext, string> = {
  BUYER: "/api/notifications",
  SELLER: "/api/seller/notifications",
  ADMIN: "/api/admin/notifications",
};

function data<T>(response: ApiResponse<T>, message: string): T {
  if (!response.success || response.data === null || response.data === undefined) {
    throw new Error(response.message || message);
  }
  return response.data;
}

export async function getNotifications(
  context: NotificationContext,
  page = 0,
  size = 20,
): Promise<NotificationPage> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  return data(
    await apiFetch<ApiResponse<NotificationPage>>(`${API_PATHS[context]}?${query}`),
    "알림 목록을 불러오지 못했습니다.",
  );
}

export async function getNotificationUnreadCount(
  context: NotificationContext,
): Promise<number> {
  const response = data(
    await apiFetch<ApiResponse<NotificationUnreadCount>>(
      `${API_PATHS[context]}/unread-count`,
    ),
    "읽지 않은 알림 수를 불러오지 못했습니다.",
  );
  return Math.max(0, response.unreadCount);
}

export async function markNotificationAsRead(
  context: NotificationContext,
  notificationId: number,
): Promise<void> {
  await apiFetch<ApiResponse<null>>(
    `${API_PATHS[context]}/${notificationId}/read`,
    { method: "PATCH" },
  );
}

export async function markAllNotificationsAsRead(
  context: NotificationContext,
): Promise<void> {
  await apiFetch<ApiResponse<null>>(`${API_PATHS[context]}/read-all`, {
    method: "PATCH",
  });
}
