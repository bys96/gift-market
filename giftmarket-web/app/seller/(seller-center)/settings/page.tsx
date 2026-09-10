"use client";

import Image from "next/image";
import { useEffect, useRef, useState } from "react";
import type { ChangeEvent, FormEvent } from "react";
import { getSellerStore, updateSellerStore } from "@/lib/seller-api";
import { uploadStoreBanner, uploadStoreLogo } from "@/lib/storage-api";
import type { SellerStore, SellerStoreUpdateRequest } from "@/types/seller";
import { resolveImageUrl } from "@/utils/image-url";
import { formatKoreanPhoneNumber } from "@/utils/phone";

type ImageField = "logoImageKey" | "bannerImageKey";
type TextField = Exclude<
  keyof SellerStoreUpdateRequest,
  ImageField | "customerServiceClosedDays"
>;

const weekdays = [
  { value: "MONDAY", label: "월" },
  { value: "TUESDAY", label: "화" },
  { value: "WEDNESDAY", label: "수" },
  { value: "THURSDAY", label: "목" },
  { value: "FRIDAY", label: "금" },
  { value: "SATURDAY", label: "토" },
  { value: "SUNDAY", label: "일" },
] as const;

type Weekday = (typeof weekdays)[number]["value"];
type ImagePreviews = Partial<Record<ImageField, string>>;

function parseClosedDays(value: string | null) {
  const selected = new Set(value?.split(",").map((day) => day.trim()));
  return weekdays.filter((day) => selected.has(day.value));
}

function showTimePicker(input: HTMLInputElement) {
  if (input.disabled || input.readOnly) return;
  try {
    input.showPicker?.();
  } catch {
    // 미지원 환경 또는 사용자 활성화 제한에서는 기본 시간 입력을 유지한다.
  }
}

const contactFields = [
  {
    name: "customerServicePhone",
    label: "전화번호",
    type: "tel",
    maxLength: 13,
  },
  {
    name: "customerServiceEmail",
    label: "이메일",
    type: "email",
    maxLength: 255,
  },
  {
    name: "customerServiceOpenTime",
    label: "상담 시작 시간",
    type: "time",
    maxLength: 5,
  },
  {
    name: "customerServiceCloseTime",
    label: "상담 종료 시간",
    type: "time",
    maxLength: 5,
  },
] as const;

const imageFields = [
  { name: "logoImageKey", label: "로고", help: "최대 5MB · 1:1 권장" },
  { name: "bannerImageKey", label: "배너", help: "최대 10MB · 가로형 권장" },
] as const;

function toEditForm(store: SellerStore): SellerStoreUpdateRequest {
  return {
    storeName: store.storeName,
    introduction: store.introduction,
    logoImageKey: store.logoImageKey,
    bannerImageKey: store.bannerImageKey,
    customerServicePhone: store.customerServicePhone
      ? formatStorePhoneNumber(store.customerServicePhone)
      : null,
    customerServiceEmail: store.customerServiceEmail,
    customerServiceOpenTime: store.customerServiceOpenTime,
    customerServiceCloseTime: store.customerServiceCloseTime,
    customerServiceClosedDays:
      parseClosedDays(store.customerServiceClosedDays)
        .map((day) => day.value)
        .join(",") || null,
    customerServiceNote: store.customerServiceNote,
  };
}

function displayValue(value: string | null) {
  return value?.trim() ? value : "등록된 정보가 없습니다.";
}

function formatStorePhoneNumber(value: string) {
  const digits = value.replace(/\D/g, "");
  // 고객센터 대표번호는 지역번호/휴대폰과 달리 4-4 형식이다.
  if (digits.startsWith("1")) {
    return digits.length <= 4
      ? digits
      : `${digits.slice(0, 4)}-${digits.slice(4, 8)}`;
  }
  return formatKoreanPhoneNumber(value);
}

export default function SellerStoreSettingsPage() {
  const [serverStore, setServerStore] = useState<SellerStore | null>(null);
  // null이면 조회 모드. 편집 시작 시 마지막 서버 응답으로 초안을 만든다.
  const [editForm, setEditForm] = useState<SellerStoreUpdateRequest | null>(
    null,
  );
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [uploadingField, setUploadingField] = useState<ImageField | null>(null);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [localPreviews, setLocalPreviews] = useState<ImagePreviews>({});
  const [failedImageUrls, setFailedImageUrls] = useState<ImagePreviews>({});
  const editFormRef = useRef<SellerStoreUpdateRequest | null>(null);
  // 비동기 업로드와 무관하게 취소/교체/unmount 시 해제할 리소스를 추적한다.
  const objectUrls = useRef<ImagePreviews>({});
  const uploadVersion = useRef(0);
  const imageInputs = useRef<Record<ImageField, HTMLInputElement | null>>({
    logoImageKey: null,
    bannerImageKey: null,
  });

  useEffect(() => {
    let active = true;
    const previews = objectUrls.current;

    async function loadStore() {
      try {
        const store = await getSellerStore();
        if (active) setServerStore(store);
      } catch (failure) {
        if (active) {
          setError(
            failure instanceof Error
              ? failure.message
              : "스토어 정보를 불러오지 못했습니다.",
          );
        }
      } finally {
        if (active) setIsLoading(false);
      }
    }

    void loadStore();
    return () => {
      active = false;
      uploadVersion.current += 1;
      Object.values(previews).forEach((url) => URL.revokeObjectURL(url));
    };
  }, []);

  function replacePreview(field: ImageField, url: string | null) {
    const previous = objectUrls.current[field];
    if (previous) URL.revokeObjectURL(previous);
    if (url) objectUrls.current[field] = url;
    else delete objectUrls.current[field];
    setFailedImageUrls((urls) => {
      if (!urls[field]) return urls;
      const next = { ...urls };
      delete next[field];
      return next;
    });
    setLocalPreviews({ ...objectUrls.current });
  }

  function clearPreviews() {
    for (const { name } of imageFields) {
      const url = objectUrls.current[name];
      if (url) URL.revokeObjectURL(url);
      delete objectUrls.current[name];
    }
    setLocalPreviews({});
  }

  function handleEdit() {
    if (!serverStore) return;
    const draft = toEditForm(serverStore);
    editFormRef.current = draft;
    setEditForm(draft);
    setMessage("");
    setError("");
  }

  function handleCancel() {
    if (isSaving) return;
    // 업로드 API는 중단을 지원하지 않으므로 취소된 편집의 응답을 무시한다.
    uploadVersion.current += 1;
    setUploadingField(null);
    clearPreviews();
    editFormRef.current = null;
    setEditForm(null);
    setError("");
    setMessage("");
  }

  function handleInputChange(field: TextField) {
    return (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      const value =
        field === "customerServicePhone"
          ? formatStorePhoneNumber(event.currentTarget.value)
          : event.currentTarget.value;
      updateEditForm((form) => (form ? { ...form, [field]: value } : null));
    };
  }

  function updateEditForm(
    updater: (
      form: SellerStoreUpdateRequest | null,
    ) => SellerStoreUpdateRequest | null,
  ) {
    setEditForm((form) => {
      const next = updater(form);
      editFormRef.current = next;
      return next;
    });
  }

  function handleClosedDayToggle(day: Weekday) {
    if (isSaving) return;
    updateEditForm((form) => {
      if (!form) return null;
      const selected = new Set(
        parseClosedDays(form.customerServiceClosedDays).map(
          (item) => item.value,
        ),
      );
      if (selected.has(day)) selected.delete(day);
      else selected.add(day);
      return {
        ...form,
        customerServiceClosedDays:
          weekdays
            .filter((item) => selected.has(item.value))
            .map((item) => item.value)
            .join(",") || null,
      };
    });
  }

  async function handleImageUpload(
    field: ImageField,
    event: ChangeEvent<HTMLInputElement>,
  ) {
    const file = event.currentTarget.files?.[0];
    event.currentTarget.value = "";
    if (!file || !editForm || isSaving || uploadingField) return;

    const version = ++uploadVersion.current;
    setUploadingField(field);
    setError("");
    try {
      replacePreview(field, URL.createObjectURL(file));
      const key =
        field === "logoImageKey"
          ? await uploadStoreLogo(file)
          : await uploadStoreBanner(file);
      if (version !== uploadVersion.current) return;
      updateEditForm((form) => (form ? { ...form, [field]: key } : null));
    } catch (failure) {
      if (version === uploadVersion.current) {
        replacePreview(field, null);
        setError(
          failure instanceof Error
            ? failure.message
            : "이미지 업로드에 실패했습니다.",
        );
      }
    } finally {
      if (version === uploadVersion.current) setUploadingField(null);
    }
  }

  function handleImageRemove(field: ImageField) {
    if (isSaving || uploadingField) return;
    replacePreview(field, null);
    updateEditForm((form) => (form ? { ...form, [field]: null } : null));
  }

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const draft = editFormRef.current;
    if (!draft || isSaving || uploadingField) return;

    setError("");
    const request = {
      ...draft,
      logoImageKey: draft.logoImageKey ?? null,
      bannerImageKey: draft.bannerImageKey ?? null,
      storeName: draft.storeName.trim(),
    };
    if (request.storeName.length < 2 || request.storeName.length > 30) {
      setError("스토어명은 2~30자로 입력해 주세요.");
      return;
    }

    setIsSaving(true);
    try {
      const store = await updateSellerStore(request);
      setServerStore(store);
      editFormRef.current = null;
      setEditForm(null);
      clearPreviews();
      setMessage("스토어 설정을 저장했습니다.");
    } catch (failure) {
      setError(
        failure instanceof Error ? failure.message : "저장에 실패했습니다.",
      );
    } finally {
      setIsSaving(false);
    }
  }

  function renderImageUpload({
    name,
    label,
    help,
  }: (typeof imageFields)[number]) {
    return (
      <div className="seller-settings-upload">
        <input
          id={name}
          type="file"
          hidden
          ref={(element) => {
            imageInputs.current[name] = element;
          }}
          aria-label={`${label} 이미지 선택`}
          accept="image/jpeg,image/png,image/webp"
          aria-describedby={`${name}-help`}
          disabled={isSaving || uploadingField !== null}
          onChange={(event) => void handleImageUpload(name, event)}
        />
        <button
          type="button"
          className="seller-settings-secondary"
          disabled={isSaving || uploadingField !== null}
          onClick={() => imageInputs.current[name]?.click()}
        >
          {label} {editForm?.[name] ? "변경" : "추가"}
        </button>
        {editForm?.[name] && (
          <button
            type="button"
            className="seller-settings-link"
            disabled={isSaving || uploadingField !== null}
            onClick={() => handleImageRemove(name)}
          >
            {label} 삭제
          </button>
        )}
        <small id={`${name}-help`}>JPG, PNG, WebP · {help}</small>
        {uploadingField === name && (
          <span role="status">{label} 업로드 중...</span>
        )}
      </div>
    );
  }

  const current = editForm ?? serverStore;
  const logoUrl =
    localPreviews.logoImageKey ??
    serverStore?.logoImageUrl ??
    resolveImageUrl(current?.logoImageKey);
  const bannerUrl =
    localPreviews.bannerImageKey ??
    serverStore?.bannerImageUrl ??
    resolveImageUrl(current?.bannerImageKey);
  const selectedClosedDays = parseClosedDays(
    editForm?.customerServiceClosedDays ?? null,
  );

  return (
    <main className="seller-settings-page">
      <div className="common-inner">
        <header className="seller-settings-header">
          <div>
            <p className="seller-settings-eyebrow">스토어 관리</p>
            <h1>스토어 설정</h1>
            <p className="seller-settings-description">
              구매자에게 노출되는 스토어 정보와 고객 안내 정보를 관리합니다.
            </p>
          </div>
          {serverStore && (
            <div className="seller-settings-actions">
              {editForm ? (
                <>
                  <button
                    type="button"
                    className="seller-settings-secondary"
                    onClick={handleCancel}
                    disabled={isSaving}
                  >
                    취소
                  </button>
                  <button
                    type="submit"
                    form="seller-settings-form"
                    className="seller-settings-primary"
                    disabled={isSaving || uploadingField !== null}
                  >
                    {isSaving ? "저장 중..." : "저장"}
                  </button>
                </>
              ) : (
                <button
                  type="button"
                  className="seller-settings-primary"
                  onClick={handleEdit}
                >
                  수정
                </button>
              )}
            </div>
          )}
        </header>

        {isLoading && <p role="status">스토어 정보를 불러오는 중입니다.</p>}
        {message && (
          <p className="seller-settings-success" role="status">
            {message}
          </p>
        )}
        {error && (
          <p className="seller-settings-error" role="alert">
            {error}
          </p>
        )}

        {current && (
          <form id="seller-settings-form" onSubmit={handleSave}>
            <fieldset className="seller-settings-sections" disabled={isSaving}>
              <legend className="seller-settings-sr-only">
                스토어 설정 정보
              </legend>
              <section className="seller-settings-card">
                <div className="seller-settings-card-heading">
                  <h2>스토어 정보</h2>
                  <span>기본 공개 정보</span>
                </div>
                <div className="seller-settings-store-info">
                  <div className="seller-settings-logo">
                    {logoUrl && failedImageUrls.logoImageKey !== logoUrl ? (
                      <Image
                        key={logoUrl}
                        src={logoUrl}
                        alt="스토어 로고"
                        width={96}
                        height={96}
                        unoptimized
                        onError={() =>
                          setFailedImageUrls((urls) => ({
                            ...urls,
                            logoImageKey: logoUrl,
                          }))
                        }
                      />
                    ) : (
                      <span
                        role="img"
                        aria-label={
                          current.logoImageKey || localPreviews.logoImageKey
                            ? "로고 이미지를 표시할 수 없습니다."
                            : "등록된 로고가 없습니다."
                        }
                      >
                        {current.storeName.charAt(0) || "G"}
                      </span>
                    )}
                  </div>
                  {editForm ? (
                    <div className="seller-settings-fields">
                      <div className="seller-product-form-field">
                        <label htmlFor="storeName">스토어명</label>
                        <input
                          id="storeName"
                          value={editForm.storeName}
                          maxLength={30}
                          onChange={handleInputChange("storeName")}
                        />
                        <small className="seller-product-form-counter">
                          {editForm.storeName.length}/30
                        </small>
                      </div>
                      <div className="seller-product-form-field">
                        <label htmlFor="introduction">스토어 소개</label>
                        <textarea
                          id="introduction"
                          value={editForm.introduction ?? ""}
                          maxLength={500}
                          onChange={handleInputChange("introduction")}
                        />
                        <small className="seller-product-form-counter">
                          {(editForm.introduction ?? "").length}/500
                        </small>
                      </div>
                    </div>
                  ) : (
                    <dl className="seller-settings-details">
                      <div>
                        <dt>스토어명</dt>
                        <dd>{displayValue(current.storeName)}</dd>
                      </div>
                      <div>
                        <dt>스토어 소개</dt>
                        <dd>{displayValue(current.introduction)}</dd>
                      </div>
                    </dl>
                  )}
                </div>
                {editForm && renderImageUpload(imageFields[0])}
              </section>

              <section className="seller-settings-card">
                <div className="seller-settings-card-heading">
                  <h2>스토어 배너</h2>
                  <span>구매자 미리보기</span>
                </div>
                <div className="seller-settings-banner">
                  {bannerUrl && failedImageUrls.bannerImageKey !== bannerUrl ? (
                    <Image
                      key={bannerUrl}
                      src={bannerUrl}
                      alt="스토어 배너"
                      width={880}
                      height={220}
                      unoptimized
                      onError={() =>
                        setFailedImageUrls((urls) => ({
                          ...urls,
                          bannerImageKey: bannerUrl,
                        }))
                      }
                    />
                  ) : (
                    <p>
                      {current.bannerImageKey || localPreviews.bannerImageKey
                        ? "배너 이미지를 표시할 수 없습니다."
                        : "등록된 배너가 없습니다."}
                    </p>
                  )}
                </div>
                {editForm && renderImageUpload(imageFields[1])}
              </section>

              <section className="seller-settings-card">
                <div className="seller-settings-card-heading">
                  <h2>고객센터 정보</h2>
                </div>
                {editForm ? (
                  <div className="seller-settings-contact-fields">
                    {contactFields.map(({ name, label, type, maxLength }) => (
                      <div className="seller-product-form-field" key={name}>
                        <label htmlFor={name}>{label}</label>
                        <input
                          id={name}
                          type={type}
                          inputMode={type === "tel" ? "tel" : undefined}
                          value={editForm[name] ?? ""}
                          maxLength={maxLength}
                          onChange={handleInputChange(name)}
                          onClick={
                            type === "time"
                              ? (event) => showTimePicker(event.currentTarget)
                              : undefined
                          }
                        />
                      </div>
                    ))}
                    <fieldset className="seller-settings-closed-days seller-settings-contact-wide">
                      <legend>휴무일</legend>
                      <div>
                        {weekdays.map(({ value, label }) => (
                          <button
                            key={value}
                            type="button"
                            aria-label={`${label}요일`}
                            aria-pressed={selectedClosedDays.some(
                              (day) => day.value === value,
                            )}
                            disabled={isSaving}
                            onClick={() => handleClosedDayToggle(value)}
                          >
                            {label}
                          </button>
                        ))}
                      </div>
                    </fieldset>
                    <div className="seller-product-form-field seller-settings-contact-wide">
                      <label htmlFor="customerServiceNote">추가 안내</label>
                      <textarea
                        id="customerServiceNote"
                        value={editForm.customerServiceNote ?? ""}
                        maxLength={500}
                        placeholder="예: 점심시간 12:00~13:00에는 상담이 어렵습니다."
                        onChange={handleInputChange("customerServiceNote")}
                      />
                      <small className="seller-product-form-counter">
                        {(editForm.customerServiceNote ?? "").length}/500
                      </small>
                    </div>
                  </div>
                ) : (
                  <dl className="seller-settings-details">
                    {contactFields.map(({ name, label }) => (
                      <div key={name}>
                        <dt>{label}</dt>
                        <dd>{displayValue(current[name])}</dd>
                      </div>
                    ))}
                    <div>
                      <dt>휴무일</dt>
                      <dd>
                        {parseClosedDays(current.customerServiceClosedDays)
                          .map((day) => `${day.label}요일`)
                          .join(", ") || "등록된 휴무일이 없습니다."}
                      </dd>
                    </div>
                    <div>
                      <dt>추가 안내</dt>
                      <dd>{displayValue(current.customerServiceNote)}</dd>
                    </div>
                  </dl>
                )}
              </section>
            </fieldset>
          </form>
        )}
      </div>
    </main>
  );
}
