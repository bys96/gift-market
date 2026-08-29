"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { type ReactNode, useEffect, useRef, useState } from "react";

import Modal from "@/components/common/modal/Modal";

interface SellerMenuItem {
  label: string;
  href: string;
  icon: ReactNode;
  matchPaths?: string[];
  excludePaths?: string[];
}

interface SellerMenuGroup {
  label: string;
  items: SellerMenuItem[];
}

const DashboardIcon = () => (
  <svg viewBox="0 0 24 24" aria-hidden="true">
    <path d="M4 4h6v7H4V4Zm0 9h6v7H4v-7Zm10-9h6v4h-6V4Zm0 6h6v10h-6V10Z" />
  </svg>
);

const ProductIcon = () => (
  <svg viewBox="0 0 24 24" aria-hidden="true">
    <path d="m12 2 8.5 4.5v11L12 22l-8.5-4.5v-11L12 2Zm0 1.8L6 7l6 3.2L18 7l-6-3.2ZM5 8.3v8.3l6.2 3.3v-8.3L5 8.3Zm14 0-6.2 3.3v8.3l6.2-3.3V8.3Z" />
  </svg>
);

const OrderIcon = () => (
  <svg viewBox="0 0 24 24" aria-hidden="true">
    <path d="M5 3h14v18H5V3Zm1.6 1.6v14.8h10.8V4.6H6.6Zm2 3h6.8v1.5H8.6V7.6Zm0 3.7h6.8v1.5H8.6v-1.5Zm0 3.7h4.5v1.5H8.6V15Z" />
  </svg>
);

const CancellationIcon = () => (
  <svg viewBox="0 0 24 24" aria-hidden="true">
    <path d="M7.2 4h9.6l3.2 3.2v9.6L16.8 20H7.2L4 16.8V7.2L7.2 4Zm.7 1.6L5.6 7.9v8.2l2.3 2.3h8.2l2.3-2.3V7.9l-2.3-2.3H7.9Zm1.2 4.7h5.8v1.5H9.1v-1.5Zm0 3h5.8v1.5H9.1v-1.5Z" />
  </svg>
);

const InquiryIcon = () => (
  <svg viewBox="0 0 24 24" aria-hidden="true">
    <path d="M4 4h16v13H9l-5 4V4Zm1.6 1.6v12.1L8.4 15.4h10V5.6H5.6ZM11.2 8h1.6v1.6h-1.6V8Zm0 3h1.6v3h-1.6v-3Z" />
  </svg>
);

const SettlementIcon = () => (
  <svg viewBox="0 0 24 24" aria-hidden="true">
    <path d="M4 5h16v14H4V5Zm1.6 1.6v10.8h12.8V6.6H5.6Zm2.2 2.2h2.7l1.5 2.3 1.5-2.3h2.7l-2.4 3.4h1.8v1.3h-2.8v2.1h-1.6v-2.1H8.4v-1.3h1.8L7.8 8.8Z" />
  </svg>
);

const SettingsIcon = () => (
  <svg viewBox="0 0 24 24" aria-hidden="true">
    <path d="m14.8 3 .5 2a8 8 0 0 1 1.4.8l2-.6 1.4 2.4-1.5 1.4c.1.5.2 1 .2 1.5s-.1 1-.2 1.5l1.5 1.4-1.4 2.4-2-.6a8 8 0 0 1-1.4.8l-.5 2h-2.8l-.5-2a8 8 0 0 1-1.4-.8l-2 .6-1.4-2.4 1.5-1.4a7 7 0 0 1 0-3L6.7 7.6l1.4-2.4 2 .6a8 8 0 0 1 1.4-.8l.5-2h2.8Zm-1.4 5A3.5 3.5 0 1 0 13.4 15a3.5 3.5 0 0 0 0-7Zm0 1.6a1.9 1.9 0 1 1 0 3.8 1.9 1.9 0 0 1 0-3.8Z" />
  </svg>
);

const StoreIcon = () => (
  <svg viewBox="0 0 24 24" aria-hidden="true">
    <path d="M4 9.5 5.5 4h13L20 9.5V20H4V9.5Zm2.7-3.9-.8 3h12.2l-.8-3H6.7Zm-1.1 4.6v8.2h4.8v-5.2h3.2v5.2h4.8v-8.2H5.6Z" />
  </svg>
);

const SELLER_MENU_GROUPS: SellerMenuGroup[] = [
  {
    label: "운영 현황",
    items: [
      {
        label: "대시보드",
        href: "/seller/dashboard",
        icon: <DashboardIcon />,
      },
    ],
  },
  {
    label: "판매 관리",
    items: [
      {
        label: "상품 관리",
        href: "/seller/products",
        icon: <ProductIcon />,
        matchPaths: ["/seller/products"],
      },
      {
        label: "주문 관리",
        href: "/seller/orders",
        icon: <OrderIcon />,
        matchPaths: ["/seller/orders"],
        excludePaths: ["/seller/orders/cancellations", "/seller/orders/returns", "/seller/orders/exchanges"],
      },
      {
        label: "취소 요청",
        href: "/seller/orders/cancellations",
        icon: <CancellationIcon />,
        matchPaths: ["/seller/orders/cancellations"],
      },
      {
        label: "반품 관리",
        href: "/seller/orders/returns",
        icon: <CancellationIcon />,
        matchPaths: ["/seller/orders/returns"],
      },
      {
        label: "교환 관리",
        href: "/seller/orders/exchanges",
        icon: <CancellationIcon />,
        matchPaths: ["/seller/orders/exchanges"],
      },
      {
        label: "문의 관리",
        href: "/seller/inquiries",
        icon: <InquiryIcon />,
        matchPaths: ["/seller/inquiries"],
      },
    ],
  },
  {
    label: "스토어 관리",
    items: [
      {
        label: "정산 관리",
        href: "/seller/settlements",
        icon: <SettlementIcon />,
        matchPaths: ["/seller/settlements"],
      },
      {
        label: "스토어 설정",
        href: "/seller/settings",
        icon: <SettingsIcon />,
        matchPaths: ["/seller/settings"],
      },
    ],
  },
];

function isActiveMenu(pathname: string, menuItem: SellerMenuItem): boolean {
  if (
    menuItem.excludePaths?.some(
      (path) => pathname === path || pathname.startsWith(`${path}/`),
    )
  ) {
    return false;
  }
  if (pathname === menuItem.href) {
    return true;
  }

  return (
    menuItem.matchPaths?.some(
      (path) => pathname === path || pathname.startsWith(`${path}/`),
    ) ?? false
  );
}

interface SidebarContentProps {
  pathname: string;
  onNavigate?: () => void;
  closeButtonRef?: React.RefObject<HTMLButtonElement | null>;
}

function SidebarContent({
  pathname,
  onNavigate,
  closeButtonRef,
}: SidebarContentProps) {
  return (
    <>
      <div className="seller-center-sidebar-header">
        <Link
          href="/seller/dashboard"
          className="seller-center-sidebar-logo"
          onClick={onNavigate}
        >
          <span className="seller-center-sidebar-logo-mark">G</span>

          <div className="seller-center-sidebar-logo-content">
            <strong>판매자센터</strong>
            <span>Gift Market</span>
          </div>
        </Link>

        {closeButtonRef && (
          <button
            ref={closeButtonRef}
            type="button"
            className="seller-sidebar-mobile-close"
            aria-label="판매자센터 메뉴 닫기"
            onClick={onNavigate}
          >
            ×
          </button>
        )}
      </div>

      <nav className="seller-center-sidebar-menu" aria-label="판매자센터 메뉴">
        {SELLER_MENU_GROUPS.map((group) => (
          <section key={group.label} className="seller-center-sidebar-group">
            <p className="seller-center-sidebar-group-label">{group.label}</p>

            <div className="seller-center-sidebar-group-list">
              {group.items.map((menuItem) => {
                const active = isActiveMenu(pathname, menuItem);

                return (
                  <Link
                    key={menuItem.href}
                    href={menuItem.href}
                    className={`seller-center-sidebar-menu-item ${
                      active ? "seller-center-sidebar-menu-item-active" : ""
                    }`}
                    aria-current={active ? "page" : undefined}
                    onClick={onNavigate}
                  >
                    <span className="seller-center-sidebar-menu-icon">
                      {menuItem.icon}
                    </span>

                    <span className="seller-center-sidebar-menu-label">
                      {menuItem.label}
                    </span>

                    {active && (
                      <span
                        className="seller-center-sidebar-active-dot"
                        aria-hidden="true"
                      />
                    )}
                  </Link>
                );
              })}
            </div>
          </section>
        ))}
      </nav>

      <div className="seller-center-sidebar-footer">
        <Link
          href="/"
          className="seller-center-store-link"
          onClick={onNavigate}
        >
          <span className="seller-center-store-link-icon">
            <StoreIcon />
          </span>

          <span className="seller-center-store-link-label">
            쇼핑몰로 돌아가기
          </span>

          <span className="seller-center-store-link-arrow" aria-hidden="true">
            ›
          </span>
        </Link>
      </div>
    </>
  );
}

export default function SellerSidebar() {
  const pathname = usePathname();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const activeMenuLabel =
    SELLER_MENU_GROUPS.flatMap((group) => group.items).find((menuItem) =>
      isActiveMenu(pathname, menuItem),
    )?.label ?? "메뉴 선택";

  useEffect(() => {
    const desktopMediaQuery = window.matchMedia("(min-width: 769px)");
    const closeMobileMenuOnDesktop = (event: MediaQueryListEvent) => {
      if (event.matches) setIsMobileMenuOpen(false);
    };

    desktopMediaQuery.addEventListener("change", closeMobileMenuOnDesktop);
    return () =>
      desktopMediaQuery.removeEventListener("change", closeMobileMenuOnDesktop);
  }, []);

  return (
    <>
      <button
        type="button"
        className="seller-sidebar-mobile-trigger"
        aria-haspopup="dialog"
        aria-expanded={isMobileMenuOpen}
        onClick={() => setIsMobileMenuOpen(true)}
      >
        <span className="seller-sidebar-mobile-trigger-icon" aria-hidden="true">
          ☰
        </span>
        <span className="seller-sidebar-mobile-trigger-text">
          <span>판매자센터 메뉴</span>
          <strong>{activeMenuLabel}</strong>
        </span>
        <span className="seller-sidebar-mobile-trigger-arrow" aria-hidden="true">
          ›
        </span>
      </button>

      <aside className="seller-center-sidebar seller-center-sidebar-desktop">
        <SidebarContent pathname={pathname} />
      </aside>

      {isMobileMenuOpen && (
        <Modal
          onClose={() => setIsMobileMenuOpen(false)}
          overlayClassName="seller-sidebar-mobile-backdrop"
          contentClassName="seller-sidebar-mobile-drawer"
          ariaLabel="판매자센터 메뉴"
          initialFocusRef={closeButtonRef}
        >
          <SidebarContent
            pathname={pathname}
            onNavigate={() => setIsMobileMenuOpen(false)}
            closeButtonRef={closeButtonRef}
          />
        </Modal>
      )}
    </>
  );
}
