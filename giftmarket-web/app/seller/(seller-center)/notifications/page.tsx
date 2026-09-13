import NotificationList from "@/components/notification/NotificationList";

export default function SellerNotificationsPage() {
  return (
    <NotificationList
      context="SELLER"
      title="판매자 알림"
      description="새 주문과 처리할 문의·클레임 소식을 확인하세요."
    />
  );
}
