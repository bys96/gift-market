"use client";

import { ChangeEvent, FormEvent, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";

import type { User } from "@/types/user";
import { roleLabel } from "@/types/user";
import { resolveImageUrl } from "@/utils/image-url";

interface ProfileFormProps {
  user: User;
  onSave: (name: string, profileImageFile: File | null) => Promise<void>;
}

const MAX_PROFILE_IMAGE_SIZE = 5 * 1024 * 1024;

const ALLOWED_PROFILE_IMAGE_TYPES = [
  "image/jpeg",
  "image/png",
  "image/webp",
  "image/gif",
];

export default function ProfileForm({ user, onSave }: ProfileFormProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const router = useRouter();

  const [name, setName] = useState(user.name);
  const [profileImageUrl, setProfileImageUrl] = useState(user.profileImageUrl);
  const [selectedProfileImage, setSelectedProfileImage] = useState<File | null>(
    null,
  );

  const [errorMessage, setErrorMessage] = useState("");
  const [isSaving, setIsSaving] = useState(false);

  const profileImageSrc = resolveImageUrl(profileImageUrl);

  const trimmedName = name.trim();

  const hasChanges = trimmedName !== user.name || selectedProfileImage !== null;

  useEffect(() => {
    setName(user.name);
    setProfileImageUrl(user.profileImageUrl);
    setSelectedProfileImage(null);
    setErrorMessage("");

    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  }, [user]);

  const handleProfileImageButtonClick = () => {
    if (isSaving) {
      return;
    }

    fileInputRef.current?.click();
  };

  const handleProfileImageChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];

    if (!file) {
      return;
    }

    setErrorMessage("");

    if (!ALLOWED_PROFILE_IMAGE_TYPES.includes(file.type)) {
      setErrorMessage("JPG, PNG, WEBP, GIF 이미지만 업로드할 수 있습니다.");
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
        setErrorMessage("이미지를 불러오지 못했습니다.");
        event.target.value = "";
        return;
      }

      setSelectedProfileImage(file);
      setProfileImageUrl(reader.result);
    };

    reader.onerror = () => {
      setSelectedProfileImage(null);
      setErrorMessage("이미지를 불러오지 못했습니다.");
      event.target.value = "";
    };

    reader.readAsDataURL(file);
  };

  const handleNameChange = (event: ChangeEvent<HTMLInputElement>) => {
    setName(event.target.value);
    setErrorMessage("");
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
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

    try {
      await onSave(trimmedName, selectedProfileImage);

      setName(trimmedName);
      setSelectedProfileImage(null);

      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }

      alert("회원정보가 변경되었습니다.");
      router.push("/my");
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "회원정보 변경 중 오류가 발생했습니다.",
      );
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
            {profileImageSrc ? (
              <img
                className="profile-image"
                src={profileImageSrc}
                alt={`${name || user.name} 프로필`}
              />
            ) : (
              <div className="profile-image-placeholder">
                {name?.charAt(0) || user.name?.charAt(0) || "회"}
              </div>
            )}
          </div>

          <div className="profile-image-actions">
            <input
              ref={fileInputRef}
              className="profile-image-input"
              type="file"
              accept="image/jpeg,image/png,image/webp,image/gif"
              disabled={isSaving}
              onChange={handleProfileImageChange}
            />

            <button
              className="profile-secondary-button"
              type="button"
              disabled={isSaving}
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
              disabled={isSaving}
              onChange={handleNameChange}
            />

            <span className="profile-field-count">{name.length}/30</span>
          </div>

          <div className="profile-field">
            <label className="profile-label" htmlFor="profile-email">
              이메일
            </label>

            <div id="profile-email" className="profile-readonly-value">
              {user.email ?? "등록된 이메일 없음"}
            </div>

            <p className="profile-field-guide">
              {user.email
                ? "소셜 로그인 계정의 이메일은 변경할 수 없습니다."
                : "로그인 제공자로부터 전달받은 이메일이 없습니다."}
            </p>
          </div>

          <div className="profile-field">
            <span className="profile-label">로그인 방식</span>

            <div className="profile-readonly-value">
              {user.provider === "GOOGLE"
                ? "Google"
                : user.provider === "KAKAO"
                  ? "Kakao"
                  : "이메일"}
            </div>
          </div>

          <div className="profile-field">
            <span className="profile-label">회원 권한</span>

            <div className="profile-readonly-value">{roleLabel[user.role]}</div>
          </div>
        </div>
      </section>

      {errorMessage && (
        <p className="profile-form-message profile-form-error" role="alert">
          {errorMessage}
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
          disabled={isSaving}
          onClick={() => router.push("/my")}
        >
          취소
        </button>
      </div>
    </form>
  );
}
