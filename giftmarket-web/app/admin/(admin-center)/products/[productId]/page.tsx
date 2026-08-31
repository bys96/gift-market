"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import AdminProductImage from "@/components/admin/AdminProductImage";
import { getAdminProduct } from "@/lib/admin-api";
import type { AdminProductDetail } from "@/types/admin";

const productStatusLabel = { DRAFT: "작성 중", ON_SALE: "판매 중", SOLD_OUT: "품절", HIDDEN: "숨김" } as const;
const sellerStatusLabel = { ACTIVE: "운영 중", SUSPENDED: "운영 정지", WITHDRAWN: "탈퇴" } as const;
const formatDateTime = (value: string | null) => value ? new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(value)) : "-";

export default function AdminProductDetailPage() {
  const params = useParams<{ productId: string }>();
  const productId = Number(params.productId);
  const [product, setProduct] = useState<AdminProductDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  const loadProduct = useCallback(async () => {
    if (!Number.isSafeInteger(productId) || productId < 1) { setError("올바르지 않은 상품 번호입니다."); setIsLoading(false); return; }
    try { setIsLoading(true); setError(""); setProduct(await getAdminProduct(productId)); }
    catch (failure) { setError(failure instanceof Error ? failure.message : "상품 정보를 불러오지 못했습니다."); }
    finally { setIsLoading(false); }
  }, [productId]);

  useEffect(() => {
    // URL의 상품 번호에 맞춰 상세 정보를 동기화한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadProduct();
  }, [loadProduct]);

  return <main className="admin-user-detail-page admin-product-detail-page">
    <Link href="/admin/products" className="admin-user-back-link">← 상품 목록으로 돌아가기</Link>
    {error && <div className="admin-dashboard-error" role="alert"><span>{error}</span><button type="button" onClick={loadProduct}>다시 시도</button></div>}
    {isLoading && !product ? <div className="admin-user-state">상품 정보를 불러오고 있습니다.</div> : product ? <>
      {product.deleted && <div className="admin-product-deleted-notice" role="status">삭제된 상품입니다. 과거 주문과 운영 이력 확인을 위해 조회만 제공합니다.</div>}
      <header className="admin-user-detail-header admin-product-detail-header"><div className="admin-product-detail-image"><AdminProductImage imageKey={product.representativeImageKey} name={product.name} sizes="96px" /></div><div><p>PRODUCT DETAIL · #{product.productId}</p><h1>{product.name}</h1><span>{product.brandName || product.summary || "상품 부가 정보 없음"}</span></div><div className="admin-product-header-badges"><span className={`admin-product-status admin-product-status-${product.status.toLowerCase()}`}>{productStatusLabel[product.status]}</span>{product.deleted && <span className="admin-product-deleted">삭제 상품</span>}</div></header>
      <div className="admin-user-detail-grid">
        <section className="admin-user-detail-card"><header><p>PRODUCT INFORMATION</p><h2>기본 정보</h2></header><dl><div><dt>상품 번호</dt><dd>#{product.productId}</dd></div><div><dt>기본 판매가</dt><dd>{product.price.toLocaleString("ko-KR")}원</dd></div><div><dt>판매 가능 재고</dt><dd>{product.availableStock.toLocaleString("ko-KR")}개</dd></div><div><dt>상태</dt><dd>{productStatusLabel[product.status]}</dd></div><div><dt>등록일</dt><dd>{formatDateTime(product.createdAt)}</dd></div><div><dt>수정일</dt><dd>{formatDateTime(product.updatedAt)}</dd></div><div><dt>삭제일</dt><dd>{formatDateTime(product.deletedAt)}</dd></div><div><dt>배송</dt><dd>{product.freeShipping ? "무료배송" : `${product.shippingFee.toLocaleString("ko-KR")}원`}</dd></div></dl></section>
        <section className="admin-user-detail-card"><header><p>SELLER & CATEGORY</p><h2>판매자 / 카테고리</h2></header><dl><div><dt>스토어</dt><dd>{product.seller.storeName}</dd></div><div><dt>판매자 상태</dt><dd>{sellerStatusLabel[product.seller.status]}</dd></div><div><dt>카테고리</dt><dd>{product.category.parentCategoryName ? `${product.category.parentCategoryName} > ` : ""}{product.category.categoryName}</dd></div><div><dt>출고 준비</dt><dd>{product.shippingPreparationDays}일</dd></div><div><dt>반품 배송비</dt><dd>{product.returnShippingFee.toLocaleString("ko-KR")}원</dd></div><div><dt>교환 배송비</dt><dd>{product.exchangeShippingFee.toLocaleString("ko-KR")}원</dd></div></dl><Link href={`/admin/sellers/${product.seller.sellerId}`} className="admin-seller-owner-button">판매자 상세 보기 →</Link>{!product.deleted && (product.status === "ON_SALE" || product.status === "SOLD_OUT") && <Link href={`/products/${product.productId}`} className="admin-product-store-link">쇼핑몰에서 보기 →</Link>}</section>
        <section className="admin-user-detail-card"><header><p>OPERATION SUMMARY</p><h2>운영 요약</h2></header><dl className="admin-user-activity"><div><dt>리뷰</dt><dd>{product.operationSummary.reviewCount.toLocaleString("ko-KR")}</dd></div><div><dt>평균 평점</dt><dd>{product.operationSummary.averageRating.toFixed(1)}</dd></div><div><dt>문의</dt><dd>{product.operationSummary.inquiryCount.toLocaleString("ko-KR")}</dd></div></dl></section>
        <section className="admin-user-detail-card"><header><p>DESCRIPTION</p><h2>상품 설명</h2></header>{product.description ? <div className="admin-product-description" dangerouslySetInnerHTML={{ __html: product.description }} /> : <p className="admin-user-detail-empty">등록된 상품 설명이 없습니다.</p>}</section>
        <section className="admin-user-detail-card admin-product-media-card"><header><p>IMAGES</p><h2>상품 이미지</h2></header><div className="admin-product-gallery"><div><AdminProductImage imageKey={product.representativeImageKey} name={product.name} sizes="160px" /></div>{product.galleryImageKeys.map((key, index) => <div key={`${key}-${index}`}><AdminProductImage imageKey={key} name={`${product.name} ${index + 1}`} sizes="160px" /></div>)}</div></section>
        <section className="admin-user-detail-card admin-product-options-card"><header><p>OPTIONS & VARIANTS</p><h2>옵션 / Variant</h2></header>{product.optionGroups.length ? <div className="admin-product-option-groups">{product.optionGroups.map((group) => <div key={group.optionGroupId}><strong>{group.name}</strong><span>{group.values.map((value) => value.value).join(" · ")}</span></div>)}</div> : <p className="admin-product-no-options">옵션이 없는 단일 상품입니다.</p>}{product.variants.length ? <div className="admin-product-variant-wrap"><table className="admin-product-variant-table"><thead><tr><th>Variant / 옵션 조합</th><th>SKU</th><th>추가 금액</th><th>판매가</th><th>재고</th><th>활성 여부</th></tr></thead><tbody>{product.variants.map((variant) => <tr key={variant.variantId}><td><strong>#{variant.variantId}</strong><span>{variant.optionValues.length ? variant.optionValues.map((value) => `${value.groupName}: ${value.value}`).join(" / ") : variant.combinationKey}</span></td><td>{variant.skuCode}</td><td>{variant.additionalPrice.toLocaleString("ko-KR")}원</td><td>{variant.price.toLocaleString("ko-KR")}원</td><td>{variant.stockQuantity.toLocaleString("ko-KR")}</td><td><span className={variant.active ? "admin-variant-active" : "admin-variant-inactive"}>{variant.active ? "활성" : "비활성"}</span></td></tr>)}</tbody></table></div> : <p className="admin-user-detail-empty">등록된 Variant가 없습니다.</p>}</section>
      </div>
    </> : null}
  </main>;
}
