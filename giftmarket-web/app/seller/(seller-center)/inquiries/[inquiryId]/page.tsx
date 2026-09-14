"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { FormEvent, useCallback, useEffect, useState } from "react";

import { formatInquiryDateTime } from "@/lib/inquiry-date";
import { answerProductInquiry, getSellerProductInquiry } from "@/lib/inquiry-api";
import { getLoginRedirectUrl } from "@/lib/login-redirect";
import { useAuthStore } from "@/stores/auth-store";
import type { ProductInquiry } from "@/types/inquiry";

export default function SellerInquiryDetailPage() {
  const params = useParams<{ inquiryId: string }>();
  const router = useRouter();
  const inquiryId = Number(params.inquiryId);
  const initialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const authenticated = useAuthStore((state) => state.isAuthenticated);
  const [inquiry, setInquiry] = useState<ProductInquiry | null>(null);
  const [answer, setAnswer] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    if (!Number.isInteger(inquiryId) || inquiryId < 1) {
      setError("올바른 문의 번호가 아닙니다.");
      setLoading(false);
      return;
    }
    try {
      setLoading(true);
      setError("");
      const value = await getSellerProductInquiry(inquiryId);
      setInquiry(value);
      setAnswer(value.answerContent ?? "");
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "문의를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [inquiryId]);

  useEffect(() => {
    if (!initialized) return;
    if (!authenticated || !user) {
      router.replace(getLoginRedirectUrl());
      return;
    }
    // 인증 확인 후 문의 상세를 동기화한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [authenticated, initialized, load, router, user]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!answer.trim()) {
      setError("답변 내용을 입력해주세요.");
      return;
    }
    try {
      setBusy(true);
      setError("");
      const value = await answerProductInquiry(inquiryId, answer);
      setInquiry(value);
      setAnswer(value.answerContent ?? "");
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "답변을 저장하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  };

  if (!initialized || !authenticated || !user) return null;
  if (loading) {
    return <main className="seller-inquiries-page"><div className="seller-inquiry-state">문의를 불러오고 있습니다.</div></main>;
  }
  if (error && !inquiry) {
    return (
      <main className="seller-inquiries-page">
        <div className="seller-inquiry-state">
          <p>{error}</p>
          <Link href="/seller/inquiries">목록으로</Link>
        </div>
      </main>
    );
  }
  if (!inquiry) return null;

  const answered = inquiry.status === "ANSWERED";

  return (
    <main className="seller-inquiries-page">
      <div className="seller-inquiries-container seller-inquiry-detail">
        <header className="seller-inquiries-header seller-inquiry-detail-header">
          <div>
            <p>PRODUCT Q&amp;A · #{inquiry.id}</p>
            <h1>상품 문의 상세</h1>
          </div>
          <Link href="/seller/inquiries">목록으로</Link>
        </header>

        <section className="seller-inquiry-card seller-inquiry-question-card">
          <header className="seller-inquiry-card-header">
            <div>
              <span className={`seller-inquiry-status ${answered ? "answered" : ""}`}>
                {answered ? "답변 완료" : "답변 대기"}
              </span>
              {inquiry.isPrivate && <span className="seller-inquiry-privacy">비공개 문의</span>}
            </div>
            <time dateTime={inquiry.createdAt}>{formatInquiryDateTime(inquiry.createdAt)}</time>
          </header>

          <dl className="seller-inquiry-meta-list">
            <div><dt>상품</dt><dd>{inquiry.productName}</dd></div>
            <div><dt>작성자</dt><dd>{inquiry.writerName}</dd></div>
          </dl>

          <div className="seller-inquiry-question">
            <span>문의 내용</span>
            <h2>{inquiry.title}</h2>
            <p className="seller-inquiry-body">{inquiry.content}</p>
          </div>
        </section>

        <section className="seller-inquiry-card seller-inquiry-answer-card">
          <header className="seller-inquiry-answer-header">
            <div>
              <p>SELLER ANSWER</p>
              <h2>{answered ? "답변 수정" : "답변 등록"}</h2>
            </div>
            {inquiry.answeredAt && (
              <time dateTime={inquiry.answeredAt}>
                최근 답변 {formatInquiryDateTime(inquiry.answeredAt)}
              </time>
            )}
          </header>

          <form className="seller-inquiry-answer-form" onSubmit={submit}>
            <label htmlFor="seller-inquiry-answer">답변 내용</label>
            <textarea
              id="seller-inquiry-answer"
              maxLength={2000}
              value={answer}
              placeholder="구매자가 이해하기 쉽도록 답변을 작성해주세요."
              onChange={(event) => setAnswer(event.target.value)}
            />
            <div className="seller-inquiry-answer-footer">
              <small>{answer.length}/2000</small>
              <button type="submit" disabled={busy}>
                {busy ? "저장 중..." : answered ? "답변 수정" : "답변 등록"}
              </button>
            </div>
            {error && <p className="seller-inquiry-error" role="alert">{error}</p>}
          </form>
        </section>
      </div>
    </main>
  );
}
