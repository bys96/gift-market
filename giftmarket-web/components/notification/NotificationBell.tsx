"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";

import {
  getNotifications,
  getNotificationUnreadCount,
  markAllNotificationsAsRead,
  markNotificationAsRead,
} from "@/lib/notification-api";
import type { Notification, NotificationContext } from "@/types/notification";

interface NotificationBellProps {
  context: NotificationContext;
  allNotificationsHref: string;
}

function formatNotificationDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";

  const elapsed = Date.now() - date.getTime();
  const minute = 60 * 1000;
  const hour = 60 * minute;
  const day = 24 * hour;

  if (elapsed >= 0 && elapsed < minute) return "방금 전";
  if (elapsed >= 0 && elapsed < hour) return `${Math.floor(elapsed / minute)}분 전`;
  if (elapsed >= 0 && elapsed < day) return `${Math.floor(elapsed / hour)}시간 전`;

  return new Intl.DateTimeFormat("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

export default function NotificationBell({
  context,
  allNotificationsHref,
}: NotificationBellProps) {
  const router = useRouter();
  const pathname = usePathname();
  const containerRef = useRef<HTMLDivElement>(null);
  const [isOpen, setIsOpen] = useState(false);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");
  const [readingId, setReadingId] = useState<number | null>(null);
  const [isMarkingAll, setIsMarkingAll] = useState(false);

  const loadUnreadCount = useCallback(async () => {
    try {
      setUnreadCount(await getNotificationUnreadCount(context));
    } catch {
      // 벨 하나의 실패가 헤더 전체를 깨뜨리지 않도록 badge만 비운다.
      setUnreadCount(0);
    }
  }, [context]);

  const loadDropdown = useCallback(async () => {
    try {
      setIsLoading(true);
      setError("");
      const [page, count] = await Promise.all([
        getNotifications(context, 0, 10),
        getNotificationUnreadCount(context),
      ]);
      setNotifications(page.content);
      setUnreadCount(count);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "알림을 불러오지 못했습니다.");
    } finally {
      setIsLoading(false);
    }
  }, [context]);

  useEffect(() => {
    // 최초 mount 시 서버의 unread count를 동기화한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadUnreadCount();
  }, [loadUnreadCount]);

  useEffect(() => {
    const closeOnOutsideClick = (event: PointerEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) setIsOpen(false);
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setIsOpen(false);
    };
    document.addEventListener("pointerdown", closeOnOutsideClick);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("pointerdown", closeOnOutsideClick);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, []);

  useEffect(() => {
    // route 이동 후 열려 있던 dropdown을 닫는다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setIsOpen(false);
  }, [pathname]);

  const toggleDropdown = () => {
    const nextOpen = !isOpen;
    setIsOpen(nextOpen);
    if (nextOpen) void loadDropdown();
  };

  const handleNotificationClick = async (notification: Notification) => {
    if (readingId !== null) return;

    try {
      setReadingId(notification.id);
      setError("");
      if (!notification.read) {
        await markNotificationAsRead(context, notification.id);
        setNotifications((current) => current.map((item) =>
          item.id === notification.id ? { ...item, read: true } : item,
        ));
        setUnreadCount((current) => Math.max(0, current - 1));
      }
      setIsOpen(false);
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
      setNotifications((current) => current.map((item) => ({ ...item, read: true })));
      setUnreadCount(0);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "모두 읽음 처리하지 못했습니다.");
    } finally {
      setIsMarkingAll(false);
    }
  };

  return (
    <div className="notification-bell" ref={containerRef}>
      <button
        type="button"
        className="notification-bell-trigger"
        aria-label={`알림${unreadCount > 0 ? `, 읽지 않음 ${unreadCount}개` : ""}`}
        aria-expanded={isOpen}
        aria-haspopup="dialog"
        onClick={toggleDropdown}
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M12 2.8a6 6 0 0 0-6 6v3.1c0 1.7-.5 3.3-1.5 4.6l-.6.8h16.2l-.6-.8a7.7 7.7 0 0 1-1.5-4.6V8.8a6 6 0 0 0-6-6Zm0 1.7a4.3 4.3 0 0 1 4.3 4.3v3.1c0 1.3.3 2.6.8 3.7H6.9c.5-1.1.8-2.4.8-3.7V8.8A4.3 4.3 0 0 1 12 4.5Zm-2.3 14.2a2.4 2.4 0 0 0 4.6 0H9.7Z" />
        </svg>
        {unreadCount > 0 && (
          <span className="notification-bell-badge">
            {unreadCount > 99 ? "99+" : unreadCount}
          </span>
        )}
      </button>

      {isOpen && (
        <section className="notification-dropdown" role="dialog" aria-label="최근 알림">
          <header className="notification-dropdown-header">
            <strong>알림</strong>
            <button
              type="button"
              onClick={handleMarkAll}
              disabled={unreadCount === 0 || isMarkingAll}
            >
              {isMarkingAll ? "처리 중" : "모두 읽음"}
            </button>
          </header>

          <div className="notification-dropdown-body">
            {isLoading ? (
              <p className="notification-state">알림을 불러오는 중입니다.</p>
            ) : error ? (
              <div className="notification-state notification-state-error" role="alert">
                <p>{error}</p>
                <button type="button" onClick={() => void loadDropdown()}>다시 시도</button>
              </div>
            ) : notifications.length === 0 ? (
              <p className="notification-state">새로운 알림이 없습니다.</p>
            ) : (
              <ul className="notification-dropdown-list">
                {notifications.map((notification) => (
                  <li key={notification.id}>
                    <button
                      type="button"
                      className={notification.read ? "" : "is-unread"}
                      disabled={readingId !== null}
                      onClick={() => void handleNotificationClick(notification)}
                    >
                      <span className="notification-item-dot" aria-hidden="true" />
                      <span className="notification-item-content">
                        <strong>{notification.title}</strong>
                        <span>{notification.message}</span>
                        <time>{formatNotificationDate(notification.createdAt)}</time>
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <Link href={allNotificationsHref} className="notification-dropdown-more">
            전체 알림 보기
          </Link>
        </section>
      )}
    </div>
  );
}
