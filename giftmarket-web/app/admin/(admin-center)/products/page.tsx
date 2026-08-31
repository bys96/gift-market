"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useCallback, useEffect, useState } from "react";

import AdminProductImage from "@/components/admin/AdminProductImage";
import Pagination from "@/components/common/Pagination";
import { getAdminProducts } from "@/lib/admin-api";
import type { AdminProductDeletedFilter, AdminProductPage, AdminProductStatus } from "@/types/admin";

const PAGE_SIZE = 20;
const statuses: AdminProductStatus[] = ["DRAFT", "ON_SALE", "SOLD_OUT", "HIDDEN"];
const deletedFilters: AdminProductDeletedFilter[] = ["ALL", "ACTIVE", "DELETED"];
const statusLabel: Record<AdminProductStatus, string> = { DRAFT: "작성 중", ON_SALE: "판매 중", SOLD_OUT: "품절", HIDDEN: "숨김" };
const deletedLabel: Record<AdminProductDeletedFilter, string> = { ALL: "전체", ACTIVE: "정상 상품", DELETED: "삭제 상품" };

function ProductSearch({ initial, onSearch }: { initial: string; onSearch: (value: string) => void }) {
  const [value, setValue] = useState(initial);
  return <form className="admin-user-search" onSubmit={(event: FormEvent) => { event.preventDefault(); onSearch(value.trim()); }}><label className="admin-user-search-input"><span className="sr-only">상품 검색</span><input value={value} onChange={(event) => setValue(event.target.value)} placeholder="상품명 또는 스토어명 검색" /></label><button type="submit">검색</button></form>;
}

function AdminProductsContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const page = Math.max(0, Number(searchParams.get("page")) || 0);
  const keyword = searchParams.get("keyword")?.trim() ?? "";
  const statusValue = searchParams.get("status");
  const status = statusValue && statuses.includes(statusValue as AdminProductStatus) ? statusValue as AdminProductStatus : undefined;
  const deletedValue = searchParams.get("deleted") ?? "ALL";
  const deleted = deletedFilters.includes(deletedValue as AdminProductDeletedFilter) ? deletedValue as AdminProductDeletedFilter : "ALL";
  const [productPage, setProductPage] = useState<AdminProductPage | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  const buildHref = useCallback((changes: Record<string, string | number | undefined>) => {
    const next = new URLSearchParams(searchParams.toString());
    Object.entries(changes).forEach(([key, value]) => {
      if (value === undefined || value === "" || (key === "page" && value === 0) || (key === "deleted" && value === "ALL")) next.delete(key);
      else next.set(key, String(value));
    });
    const query = next.toString();
    return query ? `/admin/products?${query}` : "/admin/products";
  }, [searchParams]);

  const loadProducts = useCallback(async () => {
    try {
      setIsLoading(true); setError("");
      const result = await getAdminProducts({ page, size: PAGE_SIZE, keyword: keyword || undefined, status, deleted });
      if (page > 0 && (result.totalPages === 0 || page >= result.totalPages)) {
        router.replace(buildHref({ page: Math.max(0, result.totalPages - 1) }), { scroll: false }); return;
      }
      setProductPage(result);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "상품 목록을 불러오지 못했습니다.");
    } finally { setIsLoading(false); }
  }, [buildHref, deleted, keyword, page, router, status]);

  useEffect(() => {
    // URL 검색 조건에 맞춰 상품 목록을 동기화한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadProducts();
  }, [loadProducts]);

  return <main className="admin-users-page admin-products-page">
    <header className="admin-users-header"><div><p>PRODUCT MANAGEMENT</p><h1>상품 관리</h1><span>정상 상품과 삭제 상품을 포함한 운영 현황을 조회합니다.</span></div><div><small>검색 결과</small><strong>{isLoading || error || !productPage ? "-" : productPage.totalElements.toLocaleString("ko-KR")}</strong><span>개</span></div></header>
    <section className="admin-user-filter-panel" aria-label="상품 검색 및 필터"><ProductSearch key={keyword} initial={keyword} onSearch={(value) => router.push(buildHref({ keyword: value || undefined, page: 0 }), { scroll: false })} /><div className="admin-user-filters"><label><span>상품 상태</span><select value={status ?? ""} onChange={(event) => router.push(buildHref({ status: event.target.value || undefined, page: 0 }), { scroll: false })}><option value="">전체</option>{statuses.map((value) => <option key={value} value={value}>{statusLabel[value]}</option>)}</select></label><label><span>삭제 상태</span><select value={deleted} onChange={(event) => router.push(buildHref({ deleted: event.target.value, page: 0 }), { scroll: false })}>{deletedFilters.map((value) => <option key={value} value={value}>{deletedLabel[value]}</option>)}</select></label></div></section>
    {error && <div className="admin-dashboard-error" role="alert"><span>{error}</span><button type="button" onClick={loadProducts}>다시 시도</button></div>}
    <section className="admin-user-list-section"><div className="admin-user-list-heading"><h2>상품 목록</h2>{productPage && !error && <span>총 {productPage.totalElements.toLocaleString("ko-KR")}개</span>}</div>
      {isLoading && !productPage ? <div className="admin-user-state">상품 목록을 불러오고 있습니다.</div> : productPage?.content.length ? <div className="admin-user-table-wrap"><table className="admin-user-table admin-product-table"><thead><tr><th>상품</th><th>판매자</th><th>가격</th><th>재고</th><th>상태</th><th>삭제 여부</th><th>등록일</th><th><span className="sr-only">상세</span></th></tr></thead><tbody>{productPage.content.map((product) => <tr key={product.productId}><td data-label="상품"><Link href={`/admin/products/${product.productId}`} className="admin-product-name"><span className="admin-product-thumb"><AdminProductImage imageKey={product.representativeImageKey} name={product.productName} /></span><span><strong>{product.productName}</strong><small>#{product.productId}</small></span></Link></td><td data-label="판매자"><Link href={`/admin/sellers/${product.sellerId}`} className="admin-seller-owner-link">{product.storeName}</Link></td><td data-label="가격">{product.price.toLocaleString("ko-KR")}원</td><td data-label="재고">{product.availableStock.toLocaleString("ko-KR")}개</td><td data-label="상태"><span className={`admin-product-status admin-product-status-${product.status.toLowerCase()}`}>{statusLabel[product.status]}</span></td><td data-label="삭제 여부">{product.deleted ? <span className="admin-product-deleted">삭제</span> : "정상"}</td><td data-label="등록일">{new Date(product.createdAt).toLocaleDateString("ko-KR")}</td><td><Link href={`/admin/products/${product.productId}`} className="admin-user-detail-link">상세</Link></td></tr>)}</tbody></table></div> : <div className="admin-user-state"><strong>조건에 맞는 상품이 없습니다.</strong><span>검색어나 필터 조건을 변경해 보세요.</span></div>}
      <Pagination currentPage={productPage?.page ?? page} totalPages={productPage?.totalPages ?? 0} ariaLabel="상품 목록 페이지" mode="numbers" disabled={isLoading} getPageHref={(target) => buildHref({ page: target })} scroll={false} className="admin-user-pagination" />
    </section>
  </main>;
}

export default function AdminProductsPage() { return <Suspense fallback={<div className="admin-user-state">상품 목록을 준비하고 있습니다.</div>}><AdminProductsContent /></Suspense>; }
