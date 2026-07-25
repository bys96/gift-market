"use client";

import { ChangeEvent, FormEvent, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";

import type { User } from "@/types/user";

interface ProfileFormProps {
  user: User;
  onSave: (name: string, profileImageUrl: string) => void;
}

const MAX_PROFILE_IMAGE_SIZE = 5 * 1024 * 1024;

export default function ProfileForm({ user, onSave }: ProfileFormProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const router = useRouter();

  const [name, setName] = useState(user.name);
  const [profileImageUrl, setProfileImageUrl] = useState(user.profileImageUrl);
  const [errorMessage, setErrorMessage] = useState("");
  const [isSaving, setIsSaving] = useState(false);
  const [isSaved, setIsSaved] = useState(false);

  const trimmedName = name.trim();

  const hasChanges =
    trimmedName !== user.name || profileImageUrl !== user.profileImageUrl;

  useEffect(() => {
    setName(user.name);
    setProfileImageUrl(user.profileImageUrl);
    setErrorMessage("");
  }, [user]);

  const handleProfileImageButtonClick = () => {
    fileInputRef.current?.click();
  };

  const handleProfileImageChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];

    if (!file) {
      return;
    }

    setErrorMessage("");
    setIsSaved(false);

    if (!file.type.startsWith("image/")) {
      setErrorMessage("이미지 파일만 업로드할 수 있습니다.");
      event.target.value = "";
      return;
    }

    if (file.size > MAX_PROFILE_IMAGE_SIZE) {
      setErrorMessage("프로필 이미지는 5MB 이하만 업로드할 수 있습니다.");
      event.target.value = "";
      return;
    }

    const reader = new FileReader();

    reader.onload = () => {
      if (typeof reader.result !== "string") {
        return;
      }

      // 현재는 프론트 미리보기용 Data URL을 사용한다.
      // 백엔드 연동 시 File 객체를 multipart/form-data로 전송한다.
      setProfileImageUrl(reader.result);
    };

    reader.onerror = () => {
      setErrorMessage("이미지를 불러오지 못했습니다.");
      event.target.value = "";
    };

    reader.readAsDataURL(file);
  };

  const handleNameChange = (event: ChangeEvent<HTMLInputElement>) => {
    setName(event.target.value);
    setErrorMessage("");
    setIsSaved(false);
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!hasChanges || isSaving) {
      return;
    }

    if (!trimmedName) {
      setErrorMessage("이름을 입력해 주세요.");
      return;
    }

    if (trimmedName.length > 30) {
      setErrorMessage("이름은 30자 이하로 입력해 주세요.");
      return;
    }

    setErrorMessage("");
    setIsSaving(true);
    setIsSaved(false);

    try {
      onSave(trimmedName, profileImageUrl);
      setName(trimmedName);
      setIsSaved(true);

      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }

      alert("회원정보가 변경되었습니다.");

      router.push("/my");
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <form className="profile-form" onSubmit={handleSubmit}>
      <section className="profile-form-section">
        <h2 className="profile-form-section-title">프로필 이미지</h2>

        <div className="profile-image-editor">
          <div className="profile-image-preview">
            {profileImageUrl ? (
              <img
                className="profile-image"
                src={profileImageUrl}
                alt={`${trimmedName || user.name} 프로필`}
              />
            ) : (
              <span className="profile-image-placeholder" aria-hidden="true">
                {trimmedName.charAt(0) || "회"}
              </span>
            )}
          </div>

          <div className="profile-image-actions">
            <input
              ref={fileInputRef}
              className="profile-image-input"
              type="file"
              accept="image/jpeg,image/png,image/webp,image/gif"
              onChange={handleProfileImageChange}
            />

            <button
              className="profile-secondary-button"
              type="button"
              onClick={handleProfileImageButtonClick}
            >
              이미지 변경
            </button>

            <p className="profile-image-guide">
              JPG, PNG, WEBP, GIF 파일을 최대 5MB까지 선택할 수 있습니다.
            </p>
          </div>
        </div>
      </section>

      <section className="profile-form-section">
        <h2 className="profile-form-section-title">기본 정보</h2>

        <div className="profile-field-list">
          <div className="profile-field">
            <label className="profile-label" htmlFor="profile-name">
              이름
            </label>

            <input
              id="profile-name"
              className="profile-input"
              type="text"
              value={name}
              maxLength={30}
              autoComplete="name"
              onChange={handleNameChange}
            />

            <span className="profile-field-count">{name.length}/30</span>
          </div>

          <div className="profile-field">
            <label className="profile-label" htmlFor="profile-email">
              이메일
            </label>

            <input
              id="profile-email"
              className="profile-input profile-input-readonly"
              type="email"
              value={user.email}
              readOnly
            />

            <p className="profile-field-guide">
              소셜 로그인 계정의 이메일은 변경할 수 없습니다.
            </p>
          </div>

          <div className="profile-field">
            <span className="profile-label">회원 권한</span>

            <div className="profile-readonly-value">
              {user.role === "ADMIN" ? "관리자" : "일반 회원"}
            </div>
          </div>
        </div>
      </section>

      {errorMessage && (
        <p className="profile-form-message profile-form-error" role="alert">
          {errorMessage}
        </p>
      )}

      {isSaved && !hasChanges && (
        <p className="profile-form-message profile-form-success" role="status">
          회원정보가 변경되었습니다.
        </p>
      )}

      <div className="profile-form-actions">
        <button
          className="profile-save-button"
          type="submit"
          disabled={!hasChanges || isSaving}
        >
          {isSaving ? "저장 중..." : "저장"}
        </button>

        <button
          type="button"
          className="profile-cancel-button"
          onClick={() => router.push("/my")}
        >
          취소
        </button>
      </div>
    </form>
  );
}
