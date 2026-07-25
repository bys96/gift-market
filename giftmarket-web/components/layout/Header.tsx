"use client";

import Image from "next/image";
import Link from "next/link";
import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";

import { useAuthStore } from "@/stores/auth-store";

export default function Header() {
  const router = useRouter();
  const [keyword, setKeyword] = useState("");

  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const handleSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const trimmedKeyword = keyword.trim();

    if (!trimmedKeyword) {
      return;
    }

    router.push(`/search?keyword=${encodeURIComponent(trimmedKeyword)}`);
  };

  return (
    <header className="layout-header">
      <div className="layout-header-inner">
        <Link
          href="/"
          className="layout-header-logo"
          aria-label="Open Market 홈"
        >
          Open Market
        </Link>

        <form className="layout-header-search" onSubmit={handleSearch}>
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
          <Link href="/my/wishlist" className="layout-header-action">
            찜
          </Link>

          <Link href="/cart" className="layout-header-action">
            장바구니
          </Link>

          {isAuthenticated && user ? (
            <Link href="/my" className="layout-header-profile">
              {user?.profileImageUrl ? (
                <Image
                  src={user.profileImageUrl}
                  alt={`${user.name} 프로필`}
                  width={32}
                  height={32}
                  className="layout-header-profile-image"
                />
              ) : (
                <span
                  className="layout-header-profile-fallback"
                  aria-hidden="true"
                >
                  {user.name.slice(0, 1)}
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
      </div>
    </header>
  );
}
