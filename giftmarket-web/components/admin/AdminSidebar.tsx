"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState } from "react";

import Modal from "@/components/common/modal/Modal";

interface AdminMenuItem {
  label: string;
  href: string;
  enabled: boolean;
}

const ADMIN_MENU: AdminMenuItem[] = [
  { label: "대시보드", href: "/admin", enabled: true },
  { label: "회원 관리", href: "/admin/users", enabled: true },
  { label: "판매자 관리", href: "/admin/sellers", enabled: true },
  { label: "판매자 신청", href: "/admin/seller-applications", enabled: true },
  { label: "상품 관리", href: "/admin/products", enabled: false },
  { label: "주문 관리", href: "/admin/orders", enabled: false },
  { label: "취소 관리", href: "/admin/cancellations", enabled: false },
  { label: "반품 관리", href: "/admin/returns", enabled: false },
  { label: "교환 관리", href: "/admin/exchanges", enabled: false },
];

function isActive(pathname: string, item: AdminMenuItem) {
  return item.href === "/admin"
    ? pathname === item.href
    : pathname === item.href || pathname.startsWith(`${item.href}/`);
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
      <div className="admin-center-sidebar-header">
        <Link href="/admin" className="admin-center-brand" onClick={onNavigate}>
          <span className="admin-center-brand-mark">A</span>
          <span><strong>Admin Center</strong><small>Gift Market</small></span>
        </Link>
        {closeButtonRef && (
          <button
            ref={closeButtonRef}
            type="button"
            className="admin-center-mobile-close"
            aria-label="관리자 메뉴 닫기"
            onClick={onNavigate}
          >
            ×
          </button>
        )}
      </div>

      <nav className="admin-center-menu" aria-label="관리자 메뉴">
        {ADMIN_MENU.map((item) => {
          const active = isActive(pathname, item);
          if (!item.enabled) {
            return (
              <div key={item.href} className="admin-center-menu-item admin-center-menu-disabled" aria-disabled="true">
                <span>{item.label}</span><small>준비 중</small>
              </div>
            );
          }
          return (
            <Link
              key={item.href}
              href={item.href}
              className={`admin-center-menu-item${active ? " admin-center-menu-active" : ""}`}
              aria-current={active ? "page" : undefined}
              onClick={onNavigate}
            >
              <span>{item.label}</span>
            </Link>
          );
        })}
      </nav>

      <div className="admin-center-sidebar-footer">
        <Link href="/" onClick={onNavigate}>← 쇼핑몰로 돌아가기</Link>
      </div>
    </>
  );
}

export default function AdminSidebar() {
  const pathname = usePathname();
  const [isOpen, setIsOpen] = useState(false);
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const activeLabel = ADMIN_MENU.find((item) => isActive(pathname, item))?.label ?? "관리자 메뉴";

  useEffect(() => {
    const mediaQuery = window.matchMedia("(min-width: 769px)");
    const closeOnDesktop = (event: MediaQueryListEvent) => {
      if (event.matches) setIsOpen(false);
    };
    mediaQuery.addEventListener("change", closeOnDesktop);
    return () => mediaQuery.removeEventListener("change", closeOnDesktop);
  }, []);

  return (
    <>
      <button type="button" className="admin-center-mobile-trigger" aria-haspopup="dialog" aria-expanded={isOpen} onClick={() => setIsOpen(true)}>
        <span aria-hidden="true">☰</span><span><small>Admin Center</small><strong>{activeLabel}</strong></span>
      </button>
      <aside className="admin-center-sidebar admin-center-sidebar-desktop">
        <SidebarContent pathname={pathname} />
      </aside>
      {isOpen && (
        <Modal
          onClose={() => setIsOpen(false)}
          overlayClassName="admin-center-mobile-backdrop"
          contentClassName="admin-center-mobile-drawer"
          ariaLabel="관리자 메뉴"
          initialFocusRef={closeButtonRef}
        >
          <SidebarContent pathname={pathname} onNavigate={() => setIsOpen(false)} closeButtonRef={closeButtonRef} />
        </Modal>
      )}
    </>
  );
}
