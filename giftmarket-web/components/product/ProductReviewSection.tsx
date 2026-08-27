"use client";
/* eslint-disable @next/next/no-img-element */
import { useEffect, useState } from "react";
import ReturnImageViewerModal from "@/components/return/ReturnImageViewerModal";
import { getProductReviews } from "@/lib/review-api";
import type { ReviewPage } from "@/types/review";

export default function ProductReviewSection({ productId }: { productId: number }) {
  const [data, setData] = useState<ReviewPage | null>(null); const [page, setPage] = useState(0);
  const [error, setError] = useState(""); const [view, setView] = useState<{ images: { imageId:number; url:string; sortOrder:number }[]; index:number } | null>(null);
  useEffect(() => { let active=true; getProductReviews(productId,page).then(v=>{if(active){setData(v);setError("");}}).catch(e=>{if(active)setError(e instanceof Error?e.message:"리뷰를 불러오지 못했습니다.");}); return()=>{active=false}; }, [productId,page]);
  return <section id="product-reviews" className="product-review-section">
    <header><div><h2>리뷰</h2><p>구매확정한 고객의 실제 구매 후기입니다.</p></div><strong><span aria-hidden="true">★</span> {(data?.averageRating ?? 0).toFixed(1)} <small>리뷰 {data?.reviewCount ?? 0}개</small></strong></header>
    {error ? <p className="product-review-empty">{error}</p> : !data ? <p className="product-review-empty">리뷰를 불러오는 중입니다.</p> : data.reviews.length===0 ? <p className="product-review-empty">아직 작성된 리뷰가 없습니다.</p> : <>
      <div className="product-review-list">{data.reviews.map(review=><article key={review.reviewId}>
        <div className="product-review-meta"><strong aria-label={`${review.rating}점`}>{"★".repeat(review.rating)}<span>{"★".repeat(5-review.rating)}</span></strong><span>{review.writerName}</span><time>{new Date(review.createdAt).toLocaleDateString("ko-KR")}</time></div>
        {review.optionSnapshot && <p className="product-review-option">구매 옵션 · {review.optionSnapshot}</p>}
        <p className="product-review-content">{review.content}</p>
        {review.images.length>0 && <div className="product-review-images">{review.images.map((url,index)=><button key={url} type="button" aria-label={`리뷰 이미지 ${index+1} 크게 보기`} onClick={()=>setView({images:review.images.map((u,i)=>({imageId:i,url:u,sortOrder:i})),index})}><img src={url} alt="" /></button>)}</div>}
      </article>)}</div>
      {data.totalPages>1 && <nav className="product-review-pagination" aria-label="리뷰 페이지">{Array.from({length:data.totalPages},(_,i)=><button key={i} type="button" aria-current={i===page?"page":undefined} onClick={()=>setPage(i)}>{i+1}</button>)}</nav>}
    </>}
    {view && <ReturnImageViewerModal images={view.images} initialIndex={view.index} label="리뷰 이미지" onClose={()=>setView(null)} />}
  </section>;
}
