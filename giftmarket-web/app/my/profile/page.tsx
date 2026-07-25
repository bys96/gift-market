"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect } from "react";

import ProfileForm from "@/components/my/ProfileForm";
import { useAuthStore } from "@/stores/auth-store";

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

  const handleSave = (name: string, profileImageUrl: string) => {
    if (!user) {
      return;
    }

    // 현재는 Zustand Mock 반영.
    // 백엔드 연동 시 API 성공 응답으로 받은 User를 저장한다.
    setUser({
      ...user,
      name,
      profileImageUrl,
    });
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
