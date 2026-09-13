import NotificationList from "@/components/notification/NotificationList";

export default function AdminNotificationsPage() {
  return (
    <NotificationList
      context="ADMIN"
      title="관리자 알림"
      description="새로운 운영 요청과 관리할 항목을 확인하세요."
    />
  );
}
