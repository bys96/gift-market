"use client";

import { ChangeEvent, FormEvent, useEffect, useState } from "react";
import Image from "next/image";
import { getSellerStore, updateSellerStore } from "@/lib/seller-api";
import { uploadStoreBanner, uploadStoreLogo } from "@/lib/storage-api";
import { resolveImageUrl } from "@/utils/image-url";
import type { SellerStoreUpdateRequest } from "@/types/seller";

const empty: SellerStoreUpdateRequest = { storeName: "", introduction: null, logoImageKey: null, bannerImageKey: null, customerServicePhone: null, customerServiceEmail: null, customerServiceHours: null, shippingGuide: null, returnExchangeGuide: null };

export default function SellerStoreSettingsPage() {
  const [form, setForm] = useState(empty); const [loading, setLoading] = useState(true); const [saving, setSaving] = useState(false); const [message, setMessage] = useState(""); const [error, setError] = useState("");
  useEffect(() => { void getSellerStore().then(setForm).catch((e) => setError(e instanceof Error ? e.message : "스토어 정보를 불러오지 못했습니다.")).finally(() => setLoading(false)); }, []);
  const change = (field: keyof SellerStoreUpdateRequest) => (e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => setForm((v) => ({ ...v, [field]: e.target.value }));
  const upload = async (field: "logoImageKey" | "bannerImageKey", file: File | undefined) => { if (!file) return; setError(""); try { const key = field === "logoImageKey" ? await uploadStoreLogo(file) : await uploadStoreBanner(file); setForm((v) => ({ ...v, [field]: key })); } catch (e) { setError(e instanceof Error ? e.message : "이미지 업로드에 실패했습니다."); } };
  const submit = async (e: FormEvent) => { e.preventDefault(); setError(""); setMessage(""); if (form.storeName.trim().length < 2 || form.storeName.trim().length > 30) { setError("스토어명은 2~30자로 입력해 주세요."); return; } setSaving(true); try { setForm(await updateSellerStore({ ...form, storeName: form.storeName.trim() })); setMessage("스토어 설정을 저장했습니다."); } catch (failure) { setError(failure instanceof Error ? failure.message : "저장에 실패했습니다."); } finally { setSaving(false); } };
  if (loading) return <main className="seller-center-page"><p>스토어 정보를 불러오는 중입니다.</p></main>;
  return <main className="seller-center-page"><h1>스토어 설정</h1>{message && <p role="status">{message}</p>}{error && <p role="alert">{error}</p>}<section className="seller-store-preview"><div>{form.bannerImageKey && <Image src={resolveImageUrl(form.bannerImageKey) ?? ""} alt="스토어 배너" width={320} height={120} />}</div><strong>{form.storeName || "스토어명"}</strong><p>{form.introduction || "스토어 소개를 입력해 주세요."}</p></section><form onSubmit={submit}>
    <label>스토어명<input value={form.storeName} maxLength={30} onChange={change("storeName")} required /></label><small>{form.storeName.length}/30</small>
    <label>스토어 소개<textarea value={form.introduction ?? ""} maxLength={500} onChange={change("introduction")} /><small>{(form.introduction ?? "").length}/500</small></label>
    <label>로고<input type="file" accept="image/jpeg,image/png,image/webp" onChange={(e) => void upload("logoImageKey", e.target.files?.[0])} /></label>{form.logoImageKey && <><Image src={resolveImageUrl(form.logoImageKey) ?? ""} alt="스토어 로고" width={120} height={120} /><button type="button" onClick={() => setForm((v) => ({ ...v, logoImageKey: null }))}>삭제</button></>}
    <label>배너<input type="file" accept="image/jpeg,image/png,image/webp" onChange={(e) => void upload("bannerImageKey", e.target.files?.[0])} /></label>{form.bannerImageKey && <><Image src={resolveImageUrl(form.bannerImageKey) ?? ""} alt="스토어 배너" width={320} height={120} /><button type="button" onClick={() => setForm((v) => ({ ...v, bannerImageKey: null }))}>삭제</button></>}
    <label>고객센터 전화번호<input value={form.customerServicePhone ?? ""} maxLength={30} onChange={change("customerServicePhone")} /></label><label>이메일<input type="email" value={form.customerServiceEmail ?? ""} maxLength={255} onChange={change("customerServiceEmail")} /></label><label>운영시간<input value={form.customerServiceHours ?? ""} maxLength={255} onChange={change("customerServiceHours")} /></label><label>배송 안내<textarea value={form.shippingGuide ?? ""} maxLength={1000} onChange={change("shippingGuide")} /></label><label>반품/교환 안내<textarea value={form.returnExchangeGuide ?? ""} maxLength={1000} onChange={change("returnExchangeGuide")} /></label><button type="submit" disabled={saving}>{saving ? "저장 중..." : "저장"}</button>
  </form></main>;
}
