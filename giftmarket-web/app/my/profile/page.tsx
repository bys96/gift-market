"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect } from "react";

import ProfileForm from "@/components/my/ProfileForm";
import { useAuthStore } from "@/stores/auth-store";
import type { ApiResponse } from "@/types/api";
import type { User } from "@/types/user";

export default function MyProfilePage() {
  const router = useRouter();

  const ApiUrl = process.env.NEXT_PUBLIC_API_BASE_URL;
  const { user, isAuthenticated, initialized, setUser, accessToken } =
    useAuthStore();

  useEffect(() => {
    if (!initialized) {
      return;
    }

    if (!isAuthenticated || !user) {
      router.replace("/");
    }
  }, [initialized, isAuthenticated, user, router]);

  const handleSave = async (name: string, profileImageFile: File | null) => {
    if (!accessToken) {
      throw new Error("로그인 정보가 없습니다.");
    }

    if (profileImageFile) {
      await uploadProfileImage(profileImageFile);
    }

    const response = await fetch(`${ApiUrl}/api/users/me`, {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${accessToken}`,
      },
      credentials: "include",
      body: JSON.stringify({
        name,
      }),
    });

    const result: ApiResponse<User> = await response.json();

    if (!response.ok || !result.success) {
      throw new Error(result.message ?? "회원정보 변경에 실패했습니다.");
    }

    setUser(result.data);
  };

  const uploadProfileImage = async (
    profileImageFile: File,
  ): Promise<string | null> => {
    // TODO: Presigned URL 발급
    // TODO: Object Storage 직접 업로드
    // TODO: 업로드한 objectKey 반환

    console.log("추후 업로드할 이미지:", profileImageFile.name);

    return null;
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
