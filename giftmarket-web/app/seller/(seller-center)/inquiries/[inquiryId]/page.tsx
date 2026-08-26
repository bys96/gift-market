"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { answerProductInquiry, getSellerProductInquiry } from "@/lib/inquiry-api";
import { useAuthStore } from "@/stores/auth-store";
import type { ProductInquiry } from "@/types/inquiry";

export default function SellerInquiryDetailPage() {
  const params = useParams<{ inquiryId: string }>(); const router = useRouter(); const id = Number(params.inquiryId);
  const initialized = useAuthStore((s) => s.initialized); const user = useAuthStore((s) => s.user); const authenticated = useAuthStore((s) => s.isAuthenticated);
  const [inquiry, setInquiry] = useState<ProductInquiry | null>(null); const [answer, setAnswer] = useState(""); const [loading, setLoading] = useState(true); const [error, setError] = useState(""); const [busy, setBusy] = useState(false);
  const load = useCallback(async () => { try { setLoading(true); setError(""); const value = await getSellerProductInquiry(id); setInquiry(value); setAnswer(value.answerContent ?? ""); } catch (e) { setError(e instanceof Error ? e.message : "문의를 불러오지 못했습니다."); } finally { setLoading(false); } }, [id]);
  useEffect(() => { if (!initialized) return; if (!authenticated || !user) { router.replace("/login"); return; } if (user.role !== "SELLER") { router.replace("/seller"); return; }
    // 인증 확인 후 문의 상세를 동기화한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load(); }, [authenticated, initialized, load, router, user]);
  const submit = async (e: FormEvent) => { e.preventDefault(); if (!answer.trim()) { setError("답변 내용을 입력해주세요."); return; } try { setBusy(true); setError(""); const value = await answerProductInquiry(id, answer); setInquiry(value); setAnswer(value.answerContent ?? ""); } catch (err) { setError(err instanceof Error ? err.message : "답변을 저장하지 못했습니다."); } finally { setBusy(false); } };
  if (!initialized || !authenticated || !user || user.role !== "SELLER") return null;
  if (loading) return <main className="seller-inquiries-page"><div className="seller-inquiry-state">문의를 불러오고 있습니다.</div></main>;
  if (error && !inquiry) return <main className="seller-inquiries-page"><div className="seller-inquiry-state"><p>{error}</p><Link href="/seller/inquiries">목록으로</Link></div></main>;
  if (!inquiry) return null;
  return <main className="seller-inquiries-page"><div className="seller-inquiries-container seller-inquiry-detail"><header className="seller-inquiries-header"><div><p>INQUIRY #{inquiry.id}</p><h1>상품 문의 상세</h1></div><Link href="/seller/inquiries">목록으로</Link></header><section className="seller-inquiry-card"><h2>문의 정보</h2><dl><div><dt>상품</dt><dd>{inquiry.productName}</dd></div><div><dt>작성자</dt><dd>{inquiry.writerName}</dd></div><div><dt>작성일</dt><dd>{new Date(inquiry.createdAt).toLocaleString("ko-KR")}</dd></div><div><dt>공개 여부</dt><dd>{inquiry.isPrivate ? "비공개" : "공개"}</dd></div></dl><h3>{inquiry.title}</h3><p className="seller-inquiry-body">{inquiry.content}</p></section><section className="seller-inquiry-card"><h2>{inquiry.status === "ANSWERED" ? "답변 수정" : "답변 등록"}</h2>{inquiry.answeredAt && <p>최근 답변일 {new Date(inquiry.answeredAt).toLocaleString("ko-KR")}</p>}<form className="seller-inquiry-answer-form" onSubmit={submit}><textarea maxLength={2000} value={answer} onChange={(e) => setAnswer(e.target.value)} /><small>{answer.length}/2000</small>{error && <p className="seller-inquiry-error">{error}</p>}<footer><button type="submit" disabled={busy}>{busy ? "저장 중..." : inquiry.status === "ANSWERED" ? "답변 수정" : "답변 등록"}</button></footer></form></section></div></main>;
}
