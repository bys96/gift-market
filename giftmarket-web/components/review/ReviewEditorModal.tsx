"use client";
/* eslint-disable @next/next/no-img-element */
import { useEffect, useMemo, useRef, useState } from "react";
import Modal from "@/components/common/modal/Modal";
import { createReview, deleteReview, getReview, getReviewEligibility, updateReview } from "@/lib/review-api";
import { uploadImage } from "@/lib/storage-api";

export default function ReviewEditorModal({ orderItemId, reviewId, onClose, onSaved }: { orderItemId:number; reviewId:number|null; onClose:()=>void; onSaved:()=>void }) {
  const [rating,setRating]=useState(0), [content,setContent]=useState(""), [existing,setExisting]=useState<Array<{url:string;key:string}>>([]), [files,setFiles]=useState<File[]>([]);
  const [target,setTarget]=useState<{productName:string;optionSnapshot:string|null}|null>(null), [error,setError]=useState(""), [saving,setSaving]=useState(false); const closeRef=useRef<HTMLButtonElement>(null);
  const previews=useMemo(()=>files.map(file=>({file,url:URL.createObjectURL(file)})),[files]);
  useEffect(()=>()=>previews.forEach(p=>URL.revokeObjectURL(p.url)),[previews]);
  useEffect(()=>{ Promise.all([getReviewEligibility(orderItemId), reviewId?getReview(reviewId):Promise.resolve(null)]).then(([eligibility,edit])=>{setTarget(eligibility);if(edit){setRating(edit.review.rating);setContent(edit.review.content);setExisting(edit.review.images.map((url,index)=>({url,key:edit.imageObjectKeys[index]})));}}).catch(e=>setError(e instanceof Error?e.message:"리뷰 정보를 불러오지 못했습니다.")); },[orderItemId,reviewId]);
  const addFiles=(selected:FileList|null)=>{if(!selected)return;const next=[...selected];if(existing.length+files.length+next.length>5){window.alert("사진은 최대 5장까지 첨부할 수 있습니다.");return;}if(next.some(f=>!["image/jpeg","image/png","image/webp"].includes(f.type)||f.size>5*1024*1024)){window.alert("JPG, PNG, WEBP 이미지만 파일당 5MB까지 첨부할 수 있습니다.");return;}setFiles(v=>[...v,...next]);};
  const submit=async()=>{if(rating<1){setError("별점을 선택해주세요.");return;}if(!content.trim()){setError("리뷰 내용을 입력해주세요.");return;}try{setSaving(true);setError("");const uploaded=await Promise.all(files.map(file=>uploadImage(file,"REVIEW")));const imageObjectKeys=[...existing.map(image=>image.key),...uploaded];if(reviewId)await updateReview(reviewId,{rating,content,imageObjectKeys});else await createReview({orderItemId,rating,content,imageObjectKeys});onSaved();}catch(e){setError(e instanceof Error?e.message:"리뷰를 저장하지 못했습니다.");}finally{setSaving(false);}};
  const remove=async()=>{if(!reviewId||!window.confirm("리뷰를 삭제하시겠습니까? 삭제 후 다시 작성할 수 있습니다."))return;try{setSaving(true);await deleteReview(reviewId);onSaved();}catch(e){setError(e instanceof Error?e.message:"리뷰를 삭제하지 못했습니다.");setSaving(false);}};
  return <Modal overlayClassName="review-editor-overlay" contentClassName="review-editor" ariaLabelledBy="review-editor-title" initialFocusRef={closeRef} onClose={onClose}>
    <header><div><h2 id="review-editor-title">{reviewId?"리뷰 수정":"리뷰 작성"}</h2><p>{target?.productName??"상품 정보를 불러오는 중입니다."}</p>{target?.optionSnapshot&&<small>실제 수령 옵션 · {target.optionSnapshot}</small>}</div><button ref={closeRef} type="button" aria-label="닫기" onClick={onClose}>×</button></header>
    <fieldset className="review-rating"><legend>별점</legend>{[1,2,3,4,5].map(score=><button key={score} type="button" aria-label={`${score}점`} aria-pressed={rating===score} onClick={()=>setRating(score)}>★</button>)}</fieldset>
    <label className="review-content-field"><span>리뷰 내용</span><textarea value={content} maxLength={2000} rows={7} placeholder="상품을 사용한 솔직한 경험을 남겨주세요." onChange={e=>setContent(e.target.value)} /><small>{content.length}/2000</small></label>
    <div className="review-image-field"><div><strong>사진</strong><span>{existing.length+files.length}/5</span></div><div className="review-image-previews">{existing.map((image,index)=><div key={image.key}><img src={image.url} alt={`첨부 이미지 ${index+1}`} /><button type="button" aria-label="이미지 삭제" onClick={()=>setExisting(v=>v.filter(x=>x.key!==image.key))}>×</button></div>)}{previews.map((p,index)=><div key={p.url}><img src={p.url} alt={`새 이미지 ${index+1}`} /><button type="button" aria-label="이미지 삭제" onClick={()=>setFiles(v=>v.filter(file=>file!==p.file))}>×</button></div>)}</div><label className="review-image-add">사진 추가<input type="file" accept="image/jpeg,image/png,image/webp" multiple onChange={e=>{addFiles(e.target.files);e.target.value=""}} /></label></div>
    {error&&<p className="review-editor-error">{error}</p>}<footer>{reviewId&&<button type="button" className="review-delete-button" disabled={saving} onClick={()=>void remove()}>삭제</button>}<button type="button" onClick={onClose}>취소</button><button type="button" disabled={saving||!target} onClick={()=>void submit()}>{saving?"저장 중...":"저장"}</button></footer>
  </Modal>;
}
