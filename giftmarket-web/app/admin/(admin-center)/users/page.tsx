"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useCallback, useEffect, useState } from "react";

import Pagination from "@/components/common/Pagination";
import { getAdminUsers } from "@/lib/admin-api";
import type {
  AdminAuthProvider,
  AdminUserPage,
  AdminUserRole,
  AdminUserStatus,
} from "@/types/admin";

const PAGE_SIZE = 20;
const roles: AdminUserRole[] = ["USER", "SELLER", "ADMIN"];
const providers: AdminAuthProvider[] = ["GOOGLE", "KAKAO"];
const statuses: AdminUserStatus[] = ["ACTIVE", "SUSPENDED", "WITHDRAWN"];
const roleLabel: Record<AdminUserRole, string> = { USER: "일반 회원", SELLER: "판매자", ADMIN: "관리자" };
const providerLabel: Record<AdminAuthProvider, string> = { GOOGLE: "Google", KAKAO: "Kakao" };
const statusLabel: Record<AdminUserStatus, string> = { ACTIVE: "활성", SUSPENDED: "정지", WITHDRAWN: "탈퇴" };

function enumValue<T extends string>(value: string | null, values: T[]): T | undefined {
  return value && values.includes(value as T) ? value as T : undefined;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date(value));
}

interface SearchFormProps {
  initialKeyword: string;
  onSearch: (keyword: string) => void;
}

function SearchForm({ initialKeyword, onSearch }: SearchFormProps) {
  const [keyword, setKeyword] = useState(initialKeyword);
  return (
    <form className="admin-user-search" onSubmit={(event: FormEvent) => { event.preventDefault(); onSearch(keyword.trim()); }}>
      <label className="admin-user-search-input">
        <span className="sr-only">회원 검색</span>
        <input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="이름 또는 이메일 검색" />
      </label>
      <button type="submit">검색</button>
    </form>
  );
}

function AdminUsersContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const page = Math.max(0, Number(searchParams.get("page")) || 0);
  const keyword = searchParams.get("keyword")?.trim() ?? "";
  const role = enumValue(searchParams.get("role"), roles);
  const provider = enumValue(searchParams.get("provider"), providers);
  const status = enumValue(searchParams.get("status"), statuses);
  const [userPage, setUserPage] = useState<AdminUserPage | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  const buildHref = useCallback((changes: Record<string, string | number | undefined>) => {
    const next = new URLSearchParams(searchParams.toString());
    Object.entries(changes).forEach(([key, value]) => {
      if (value === undefined || value === "" || (key === "page" && value === 0)) next.delete(key);
      else next.set(key, String(value));
    });
    const query = next.toString();
    return query ? `/admin/users?${query}` : "/admin/users";
  }, [searchParams]);

  const loadUsers = useCallback(async () => {
    try {
      setIsLoading(true);
      setError("");
      const result = await getAdminUsers({ page, size: PAGE_SIZE, keyword: keyword || undefined, role, provider, status });
      if (page > 0 && (result.totalPages === 0 || page >= result.totalPages)) {
        router.replace(buildHref({ page: Math.max(0, result.totalPages - 1) }), { scroll: false });
        return;
      }
      setUserPage(result);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "회원 목록을 불러오지 못했습니다.");
    } finally {
      setIsLoading(false);
    }
  }, [buildHref, keyword, page, provider, role, router, status]);

  useEffect(() => {
    // URL 검색 조건에 맞춰 회원 목록을 동기화한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadUsers();
  }, [loadUsers]);

  const changeFilter = (key: "role" | "provider" | "status", value: string) => {
    router.push(buildHref({ [key]: value || undefined, page: 0 }), { scroll: false });
  };

  return (
    <main className="admin-users-page">
      <header className="admin-users-header"><div><p>USER MANAGEMENT</p><h1>회원 관리</h1><span>가입 회원의 계정 상태와 판매자 여부를 조회합니다.</span></div><div><small>검색 결과</small><strong>{isLoading || error || !userPage ? "-" : userPage.totalElements.toLocaleString("ko-KR")}</strong><span>명</span></div></header>

      <section className="admin-user-filter-panel" aria-label="회원 검색 및 필터">
        <SearchForm key={keyword} initialKeyword={keyword} onSearch={(value) => router.push(buildHref({ keyword: value || undefined, page: 0 }), { scroll: false })} />
        <div className="admin-user-filters">
          <label><span>역할</span><select value={role ?? ""} onChange={(event) => changeFilter("role", event.target.value)}><option value="">전체</option>{roles.map((value) => <option key={value} value={value}>{roleLabel[value]}</option>)}</select></label>
          <label><span>가입 방식</span><select value={provider ?? ""} onChange={(event) => changeFilter("provider", event.target.value)}><option value="">전체</option>{providers.map((value) => <option key={value} value={value}>{providerLabel[value]}</option>)}</select></label>
          <label><span>상태</span><select value={status ?? ""} onChange={(event) => changeFilter("status", event.target.value)}><option value="">전체</option>{statuses.map((value) => <option key={value} value={value}>{statusLabel[value]}</option>)}</select></label>
        </div>
      </section>

      {error && <div className="admin-dashboard-error" role="alert"><span>{error}</span><button type="button" onClick={loadUsers}>다시 시도</button></div>}

      <section className="admin-user-list-section">
        <div className="admin-user-list-heading"><h2>회원 목록</h2>{userPage && !error && <span>총 {userPage.totalElements.toLocaleString("ko-KR")}명</span>}</div>
        {isLoading && !userPage ? <div className="admin-user-state">회원 목록을 불러오고 있습니다.</div> : userPage?.content.length ? (
          <div className="admin-user-table-wrap"><table className="admin-user-table"><thead><tr><th>회원</th><th>이메일</th><th>역할</th><th>가입 방식</th><th>상태</th><th>판매자</th><th>가입일</th><th><span className="sr-only">상세</span></th></tr></thead><tbody>{userPage.content.map((user) => (
            <tr key={user.id}><td data-label="회원"><Link href={`/admin/users/${user.id}`} className="admin-user-name"><span>{user.name.slice(0, 1)}</span><strong>{user.name}</strong></Link></td><td data-label="이메일">{user.email ?? "-"}</td><td data-label="역할"><span className={`admin-user-badge admin-user-role-${user.role.toLowerCase()}`}>{roleLabel[user.role]}</span></td><td data-label="가입 방식">{providerLabel[user.provider]}</td><td data-label="상태"><span className={`admin-user-status admin-user-status-${user.status.toLowerCase()}`}>{statusLabel[user.status]}</span></td><td data-label="판매자">{user.activeSeller ? "운영 중" : "-"}</td><td data-label="가입일">{formatDate(user.createdAt)}</td><td><Link href={`/admin/users/${user.id}`} className="admin-user-detail-link">상세</Link></td></tr>
          ))}</tbody></table></div>
        ) : <div className="admin-user-state"><strong>조건에 맞는 회원이 없습니다.</strong><span>검색어나 필터 조건을 변경해 보세요.</span></div>}

        <Pagination currentPage={userPage?.page ?? page} totalPages={userPage?.totalPages ?? 0} ariaLabel="회원 목록 페이지" mode="numbers" disabled={isLoading} getPageHref={(targetPage) => buildHref({ page: targetPage })} scroll={false} className="admin-user-pagination" />
      </section>
    </main>
  );
}

export default function AdminUsersPage() {
  return <Suspense fallback={<div className="admin-user-state">회원 목록을 준비하고 있습니다.</div>}><AdminUsersContent /></Suspense>;
}
