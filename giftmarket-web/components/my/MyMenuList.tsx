"use client";

import Link from "next/link";

interface MyMenuItem {
  label: string;
  description: string;
  href: string;
}

const MY_MENU_ITEMS: MyMenuItem[] = [
  {
    label: "주문 내역",
    description: "주문 및 배송 상태를 확인합니다.",
    href: "/my/orders",
  },
  {
    label: "찜한 상품",
    description: "찜해둔 상품을 확인합니다.",
    href: "/my/wishlist",
  },
  {
    label: "배송지 관리",
    description: "자주 사용하는 배송지를 관리합니다.",
    href: "/my/addresses",
  },
  {
    label: "회원 정보",
    description: "내 정보를 확인하고 수정합니다.",
    href: "/my/profile",
  },
];

interface MyMenuListProps {
  onLogout: () => void;
  isLoggingOut: boolean;
}

export default function MyMenuList({
  onLogout,
  isLoggingOut,
}: MyMenuListProps) {
  return (
    <section className="my-menu-section">
      <h2 className="my-section-title">나의 쇼핑</h2>

      <div className="my-menu-list">
        {MY_MENU_ITEMS.map((item) => (
          <Link key={item.href} className="my-menu-item" href={item.href}>
            <div className="my-menu-content">
              <strong className="my-menu-label">{item.label}</strong>

              <span className="my-menu-description">{item.description}</span>
            </div>

            <span className="my-menu-arrow" aria-hidden="true">
              ›
            </span>
          </Link>
        ))}

        <button
          className="my-menu-item my-menu-logout"
          type="button"
          onClick={onLogout}
          disabled={isLoggingOut}
        >
          <div className="my-menu-content">
            <strong className="my-menu-label">로그아웃</strong>

            <span className="my-menu-description">
              {isLoggingOut
                ? "로그아웃 처리 중입니다."
                : "현재 계정에서 로그아웃합니다."}
            </span>
          </div>

          <span className="my-menu-arrow" aria-hidden="true">
            ›
          </span>
        </button>
      </div>
    </section>
  );
}
