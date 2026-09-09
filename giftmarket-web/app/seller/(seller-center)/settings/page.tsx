"use client";

import Image from "next/image";
import { useEffect, useRef, useState } from "react";
import type { ChangeEvent, FormEvent } from "react";
import { getSellerStore, updateSellerStore } from "@/lib/seller-api";
import { uploadStoreBanner, uploadStoreLogo } from "@/lib/storage-api";
import type { SellerStore, SellerStoreUpdateRequest } from "@/types/seller";
import { resolveImageUrl } from "@/utils/image-url";

type ImageField = "logoImageKey" | "bannerImageKey";
type TextField = Exclude<keyof SellerStoreUpdateRequest, ImageField>;

const contactFields = [
  {
    name: "customerServicePhone",
    label: "전화번호",
    type: "tel",
    maxLength: 30,
  },
  {
    name: "customerServiceEmail",
    label: "이메일",
    type: "email",
    maxLength: 255,
  },
  {
    name: "customerServiceHours",
    label: "운영시간",
    type: "text",
    maxLength: 255,
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
    customerServicePhone: store.customerServicePhone,
    customerServiceEmail: store.customerServiceEmail,
    customerServiceHours: store.customerServiceHours,
  };
}

function displayValue(value: string | null) {
  return value?.trim() ? value : "등록된 정보가 없습니다.";
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
  const uploadVersion = useRef(0);

  useEffect(() => {
    let active = true;

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
    };
  }, []);

  function handleEdit() {
    if (!serverStore) return;
    setEditForm(toEditForm(serverStore));
    setMessage("");
    setError("");
  }

  function handleCancel() {
    if (isSaving) return;
    // 업로드 API는 중단을 지원하지 않으므로 취소된 편집의 응답을 무시한다.
    uploadVersion.current += 1;
    setUploadingField(null);
    setEditForm(null);
    setError("");
    setMessage("");
  }

  function handleInputChange(field: TextField) {
    return (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      const value = event.currentTarget.value;
      setEditForm((form) => (form ? { ...form, [field]: value } : null));
    };
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
      const key =
        field === "logoImageKey"
          ? await uploadStoreLogo(file)
          : await uploadStoreBanner(file);
      if (version !== uploadVersion.current) return;
      setEditForm((form) => (form ? { ...form, [field]: key } : null));
    } catch (failure) {
      if (version === uploadVersion.current) {
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
    setEditForm((form) => (form ? { ...form, [field]: null } : null));
  }

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editForm || isSaving || uploadingField) return;

    setError("");
    const request = { ...editForm, storeName: editForm.storeName.trim() };
    if (request.storeName.length < 2 || request.storeName.length > 30) {
      setError("스토어명은 2~30자로 입력해 주세요.");
      return;
    }

    setIsSaving(true);
    try {
      const store = await updateSellerStore(request);
      setServerStore(store);
      setEditForm(null);
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
        <label htmlFor={name}>{label} 이미지</label>
        <input
          id={name}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          aria-describedby={`${name}-help`}
          disabled={isSaving || uploadingField !== null}
          onChange={(event) => void handleImageUpload(name, event)}
        />
        <small id={`${name}-help`}>JPG, PNG, WebP · {help}</small>
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
        {uploadingField === name && (
          <span role="status">{label} 업로드 중...</span>
        )}
      </div>
    );
  }

  const current = editForm ?? serverStore;
  const logoUrl = resolveImageUrl(current?.logoImageKey);
  const bannerUrl = resolveImageUrl(current?.bannerImageKey);

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
                    {logoUrl ? (
                      <Image
                        src={logoUrl}
                        alt="스토어 로고"
                        width={96}
                        height={96}
                      />
                    ) : (
                      <span>{current.storeName.charAt(0) || "G"}</span>
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
                  {bannerUrl ? (
                    <Image
                      src={bannerUrl}
                      alt="스토어 배너"
                      width={880}
                      height={220}
                    />
                  ) : (
                    <p>
                      {current.bannerImageKey
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
                          value={editForm[name] ?? ""}
                          maxLength={maxLength}
                          onChange={handleInputChange(name)}
                        />
                      </div>
                    ))}
                  </div>
                ) : (
                  <dl className="seller-settings-details">
                    {contactFields.map(({ name, label }) => (
                      <div key={name}>
                        <dt>{label}</dt>
                        <dd>{displayValue(current[name])}</dd>
                      </div>
                    ))}
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
