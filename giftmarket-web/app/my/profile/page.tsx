"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect } from "react";

import ProfileForm from "@/components/my/ProfileForm";
import { apiFetch } from "@/lib/api";
import { useAuthStore } from "@/stores/auth-store";
import type { ApiResponse, PresignedUrlResponse } from "@/types/api";
import type { User } from "@/types/user";

export default function MyProfilePage() {
  const router = useRouter();

  const { user, isAuthenticated, initialized, setUser } = useAuthStore();

  useEffect(() => {
    if (!initialized) {
      return;
    }

    if (!isAuthenticated || !user) {
      router.replace("/");
    }
  }, [initialized, isAuthenticated, user, router]);

  const handleSave = async (name: string, profileImageFile: File | null) => {
    let profileImageUrl: string | null = null;

    if (profileImageFile) {
      profileImageUrl = await uploadProfileImage(profileImageFile);
    }

    const result = await apiFetch<ApiResponse<User>>("/api/users/me", {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        name,
        profileImageUrl,
      }),
    });

    if (!result.success) {
      throw new Error(result.message ?? "회원정보 변경에 실패했습니다.");
    }

    setUser(result.data);
  };

  const uploadProfileImage = async (
    profileImageFile: File,
  ): Promise<string> => {
    const result = await apiFetch<ApiResponse<PresignedUrlResponse>>(
      "/api/storage/presigned-url",
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          type: "PROFILE",
          fileName: profileImageFile.name,
          contentType: profileImageFile.type,
          fileSize: profileImageFile.size,
        }),
      },
    );

    if (!result.success) {
      throw new Error(result.message ?? "Presigned URL 발급 실패");
    }

    const { uploadUrl, objectKey } = result.data;

    const uploadResponse = await fetch(uploadUrl, {
      method: "PUT",
      headers: {
        "Content-Type": profileImageFile.type,
      },
      body: profileImageFile,
    });

    if (!uploadResponse.ok) {
      throw new Error("이미지 업로드에 실패했습니다.");
    }

    return objectKey;
  };

  if (!initialized) {
    return (
      <main className="my-page">
        <div className="my-container">
          <div className="profile-page-loading">
            회원정보를 불러오는 중입니다.
          </div>
        </div>
      </main>
    );
  }

  if (!isAuthenticated || !user) {
    return null;
  }

  return (
    <main className="my-page">
      <div className="my-container">
        <div className="profile-page-header">
          <Link className="profile-back-link" href="/my">
            ← 마이페이지
          </Link>

          <h1 className="profile-page-title">회원 정보</h1>

          <p className="profile-page-description">
            프로필 이미지와 기본 정보를 확인하고 수정할 수 있습니다.
          </p>
        </div>

        <ProfileForm user={user} onSave={handleSave} />
      </div>
    </main>
  );
}
