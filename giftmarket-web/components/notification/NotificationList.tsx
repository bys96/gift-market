"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import Pagination from "@/components/common/Pagination";
import {
  getNotifications,
  getNotificationUnreadCount,
  markAllNotificationsAsRead,
  markNotificationAsRead,
} from "@/lib/notification-api";
import type {
  Notification,
  NotificationContext,
  NotificationPage,
} from "@/types/notification";

interface NotificationListProps {
  context: NotificationContext;
  title: string;
  description: string;
}

function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "-";
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

export default function NotificationList({
  context,
  title,
  description,
}: NotificationListProps) {
  const router = useRouter();
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<NotificationPage | null>(null);
  const [unreadCount, setUnreadCount] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const [readingId, setReadingId] = useState<number | null>(null);
  const [isMarkingAll, setIsMarkingAll] = useState(false);

  const load = useCallback(async () => {
    try {
      setIsLoading(true);
      setError("");
      const [notifications, count] = await Promise.all([
        getNotifications(context, page, 20),
        getNotificationUnreadCount(context),
      ]);
      setResult(notifications);
      setUnreadCount(count);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "알림을 불러오지 못했습니다.");
    } finally {
      setIsLoading(false);
    }
  }, [context, page]);

  useEffect(() => {
    // 최초 진입 및 page 변경 시 서버 목록을 동기화한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [load]);

  const updateItem = (notificationId: number) => {
    setResult((current) => current ? {
      ...current,
      content: current.content.map((item) =>
        item.id === notificationId ? { ...item, read: true } : item,
      ),
    } : current);
  };

  const handleNotificationClick = async (notification: Notification) => {
    if (readingId !== null) return;
    try {
      setReadingId(notification.id);
      setError("");
      if (!notification.read) {
        await markNotificationAsRead(context, notification.id);
        updateItem(notification.id);
        setUnreadCount((current) => Math.max(0, current - 1));
      }
      if (notification.targetUrl) router.push(notification.targetUrl);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "알림을 읽음 처리하지 못했습니다.");
    } finally {
      setReadingId(null);
    }
  };

  const handleMarkAll = async () => {
    if (isMarkingAll || unreadCount === 0) return;
    try {
      setIsMarkingAll(true);
      setError("");
      await markAllNotificationsAsRead(context);
      setResult((current) => current ? {
        ...current,
        content: current.content.map((item) => ({ ...item, read: true })),
      } : current);
      setUnreadCount(0);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "모두 읽음 처리하지 못했습니다.");
    } finally {
      setIsMarkingAll(false);
    }
  };

  return (
    <main className={`notification-page notification-page-${context.toLowerCase()}`}>
      <header className="notification-page-header">
        <div>
          <p>NOTIFICATIONS</p>
          <h1>{title}</h1>
          <span>{description}</span>
        </div>
        <button
          type="button"
          onClick={handleMarkAll}
          disabled={unreadCount === 0 || isMarkingAll}
        >
          {isMarkingAll ? "처리 중" : `모두 읽음${unreadCount > 0 ? ` (${unreadCount})` : ""}`}
        </button>
      </header>

      {error && (
        <div className="notification-page-error" role="alert">
          <span>{error}</span>
          <button type="button" onClick={() => void load()}>다시 시도</button>
        </div>
      )}

      {isLoading ? (
        <div className="notification-page-state">알림을 불러오는 중입니다.</div>
      ) : !result || result.content.length === 0 ? (
        <div className="notification-page-state">아직 받은 알림이 없습니다.</div>
      ) : (
        <section className="notification-page-list" aria-label={title}>
          {result.content.map((notification) => (
            <button
              key={notification.id}
              type="button"
              className={notification.read ? "" : "is-unread"}
              disabled={readingId !== null}
              onClick={() => void handleNotificationClick(notification)}
            >
              <span className="notification-item-dot" aria-hidden="true" />
              <span className="notification-item-content">
                <strong>{notification.title}</strong>
                <span>{notification.message}</span>
                <time>{formatDateTime(notification.createdAt)}</time>
              </span>
              {notification.targetUrl && <span className="notification-page-arrow" aria-hidden="true">›</span>}
            </button>
          ))}
        </section>
      )}

      <Pagination
        currentPage={result?.page ?? page}
        totalPages={result?.totalPages ?? 0}
        ariaLabel="알림 목록 페이지"
        mode="numbers"
        disabled={isLoading}
        onPageChange={setPage}
        className="pagination notification-pagination"
      />
    </main>
  );
}
