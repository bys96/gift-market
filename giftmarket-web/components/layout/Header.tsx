"use client";

import Image from "next/image";
import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";

import { useAuthStore } from "@/stores/auth-store";
import { roleLabel } from "@/types/user";
import { resolveImageUrl } from "@/utils/image-url";

export default function Header() {
  const router = useRouter();
  const pathname = usePathname();

  const [keyword, setKeyword] = useState("");
  const [isMobileSearchOpen, setIsMobileSearchOpen] = useState(false);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const profileImageSrc = resolveImageUrl(user?.profileImageUrl);

  useEffect(() => {
    // route 이동 시 열려 있던 모바일 UI를 닫는다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setIsMobileSearchOpen(false);
    setIsMobileMenuOpen(false);
  }, [pathname]);

  useEffect(() => {
    const desktopMediaQuery = window.matchMedia("(min-width: 901px)");

    const closeMobileUi = (event: MediaQueryListEvent) => {
      if (!event.matches) {
        return;
      }

      setIsMobileMenuOpen(false);
      setIsMobileSearchOpen(false);
    };

    if (desktopMediaQuery.matches) {
      // 최초 desktop media 상태를 모바일 UI에 동기화한다.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setIsMobileMenuOpen(false);
      setIsMobileSearchOpen(false);
    }

    desktopMediaQuery.addEventListener("change", closeMobileUi);

    return () => {
      desktopMediaQuery.removeEventListener("change", closeMobileUi);
    };
  }, []);

  useEffect(() => {
    if (!isMobileMenuOpen) {
      return;
    }

    const previousOverflow = document.body.style.overflow;

    document.body.style.overflow = "hidden";

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setIsMobileMenuOpen(false);
      }
    };

    window.addEventListener("keydown", handleKeyDown);

    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [isMobileMenuOpen]);

  const handleSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const trimmedKeyword = keyword.trim();

    if (!trimmedKeyword) {
      return;
    }

    setIsMobileSearchOpen(false);

    router.push(`/products?keyword=${encodeURIComponent(trimmedKeyword)}`);
  };

  const handleMobileSearchToggle = () => {
    setIsMobileSearchOpen((current) => !current);
    setIsMobileMenuOpen(false);
  };

  const handleMobileMenuToggle = () => {
    setIsMobileMenuOpen((current) => !current);
    setIsMobileSearchOpen(false);
  };

  return (
    <>
      <header className="layout-header">
        <div className="layout-header-inner">
          <Link
            href="/"
            className="layout-header-logo"
            aria-label="Open Market 홈"
          >
            Open Market
          </Link>

          <form
            className="layout-header-search layout-header-desktop-search"
            onSubmit={handleSearch}
          >
            <label htmlFor="layout-header-search-input" className="sr-only">
              상품 검색
            </label>

            <input
              id="layout-header-search-input"
              type="search"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="선물할 상품을 검색해 보세요"
              autoComplete="off"
            />

            <button type="submit" aria-label="검색">
              검색
            </button>
          </form>

          <nav className="layout-header-actions" aria-label="사용자 메뉴">
            {user?.role === "ADMIN" && (
              <Link
                href="/admin/seller-applications"
                className="layout-header-action"
              >
                관리자
              </Link>
            )}

            <Link href="/seller" className="layout-header-action">
              판매자
            </Link>

            <Link href="/my/wishlist" className="layout-header-action">
              찜
            </Link>

            <Link href="/cart" className="layout-header-action">
              장바구니
            </Link>

            {isAuthenticated && user ? (
              <Link href="/my" className="layout-header-profile">
                {profileImageSrc ? (
                  <Image
                    className="layout-header-profile-image"
                    src={profileImageSrc}
                    alt={`${user.name ?? "회원"} 프로필`}
                    width={32}
                    height={32}
                  />
                ) : (
                  <span className="layout-header-profile-fallback">
                    {user.name?.charAt(0) ?? "회"}
                  </span>
                )}

                <span>{user.name}</span>
              </Link>
            ) : (
              <Link href="/login" className="layout-header-login">
                로그인
              </Link>
            )}
          </nav>

          <div className="layout-header-mobile-actions">
            <button
              type="button"
              className="layout-header-icon-button"
              aria-label={isMobileSearchOpen ? "검색창 닫기" : "검색창 열기"}
              aria-expanded={isMobileSearchOpen}
              aria-controls="layout-header-mobile-search"
              onClick={handleMobileSearchToggle}
            >
              {isMobileSearchOpen ? (
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M6.4 5.3 12 10.9l5.6-5.6 1.1 1.1-5.6 5.6 5.6 5.6-1.1 1.1-5.6-5.6-5.6 5.6-1.1-1.1 5.6-5.6-5.6-5.6 1.1-1.1Z" />
                </svg>
              ) : (
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M10.8 4a6.8 6.8 0 1 0 4.2 12.1l4.5 4.5 1.1-1.1-4.5-4.5A6.8 6.8 0 0 0 10.8 4Zm0 1.6a5.2 5.2 0 1 1 0 10.4 5.2 5.2 0 0 1 0-10.4Z" />
                </svg>
              )}
            </button>

            <button
              type="button"
              className="layout-header-icon-button"
              aria-label={isMobileMenuOpen ? "메뉴 닫기" : "메뉴 열기"}
              aria-expanded={isMobileMenuOpen}
              aria-controls="layout-mobile-drawer"
              onClick={handleMobileMenuToggle}
            >
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M4 6.5h16V8H4V6.5Zm0 4.75h16v1.5H4v-1.5ZM4 16h16v1.5H4V16Z" />
              </svg>
            </button>
          </div>
        </div>

        <div
          id="layout-header-mobile-search"
          className={`layout-header-mobile-search ${
            isMobileSearchOpen ? "layout-header-mobile-search-open" : ""
          }`}
        >
          <form onSubmit={handleSearch}>
            <label
              htmlFor="layout-header-mobile-search-input"
              className="sr-only"
            >
              상품 검색
            </label>

            <input
              id="layout-header-mobile-search-input"
              type="search"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="선물할 상품을 검색해 보세요"
              autoComplete="off"
            />

            <button type="submit">검색</button>
          </form>
        </div>
      </header>

      <div
        className={`layout-mobile-menu-backdrop ${
          isMobileMenuOpen ? "layout-mobile-menu-backdrop-open" : ""
        }`}
        aria-hidden={!isMobileMenuOpen}
        onMouseDown={(event) => {
          if (event.target === event.currentTarget) {
            setIsMobileMenuOpen(false);
          }
        }}
      >
        <aside
          id="layout-mobile-drawer"
          className={`layout-mobile-drawer ${
            isMobileMenuOpen ? "layout-mobile-drawer-open" : ""
          }`}
          aria-label="모바일 메뉴"
          aria-hidden={!isMobileMenuOpen}
        >
          <div className="layout-mobile-drawer-header">
            <strong>메뉴</strong>

            <button
              type="button"
              aria-label="메뉴 닫기"
              onClick={() => setIsMobileMenuOpen(false)}
            >
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M6.4 5.3 12 10.9l5.6-5.6 1.1 1.1-5.6 5.6 5.6 5.6-1.1 1.1-5.6-5.6-5.6 5.6-1.1-1.1 5.6-5.6-5.6-5.6 1.1-1.1Z" />
              </svg>
            </button>
          </div>

          {isAuthenticated && user ? (
            <Link href="/my" className="layout-mobile-profile">
              {profileImageSrc ? (
                <Image
                  className="layout-mobile-profile-image"
                  src={profileImageSrc}
                  alt={`${user.name ?? "회원"} 프로필`}
                  width={52}
                  height={52}
                />
              ) : (
                <span className="layout-mobile-profile-fallback">
                  {user.name?.charAt(0) ?? "회"}
                </span>
              )}

              <div className="layout-mobile-profile-content">
                <strong>{user.name}</strong>
                <span>{roleLabel[user.role]}</span>
              </div>

              <span className="layout-mobile-menu-arrow" aria-hidden="true">
                ›
              </span>
            </Link>
          ) : (
            <section className="layout-mobile-login-section">
              <div>
                <strong>로그인이 필요합니다.</strong>
                <p>로그인하고 주문과 찜 목록을 확인해 보세요.</p>
              </div>

              <Link href="/login">로그인</Link>
            </section>
          )}

          <nav
            className="layout-mobile-menu-list"
            aria-label="모바일 사용자 메뉴"
          >
            {user?.role === "ADMIN" && (
              <Link href="/admin/seller-applications">
                <span>관리자</span>
                <span className="layout-mobile-menu-arrow" aria-hidden="true">
                  ›
                </span>
              </Link>
            )}

            <Link href="/seller">
              <span>판매자</span>
              <span className="layout-mobile-menu-arrow" aria-hidden="true">
                ›
              </span>
            </Link>

            <Link href="/my/wishlist">
              <span>찜</span>
              <span className="layout-mobile-menu-arrow" aria-hidden="true">
                ›
              </span>
            </Link>

            <Link href="/cart">
              <span>장바구니</span>
              <span className="layout-mobile-menu-arrow" aria-hidden="true">
                ›
              </span>
            </Link>

            {isAuthenticated && user && (
              <Link href="/my">
                <span>마이페이지</span>
                <span className="layout-mobile-menu-arrow" aria-hidden="true">
                  ›
                </span>
              </Link>
            )}
          </nav>
        </aside>
      </div>
    </>
  );
}
