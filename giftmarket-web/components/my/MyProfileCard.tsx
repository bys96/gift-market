import Image from "next/image";
import type { User } from "@/types/user";
import { resolveImageUrl } from "@/utils/image-url";

interface MyProfileCardProps {
  user: User;
}

export default function MyProfileCard({ user }: MyProfileCardProps) {
  const profileImageSrc = resolveImageUrl(user.profileImageUrl);

  return (
    <section className="my-profile-card">
      <div className="my-profile-image-wrapper">
        {profileImageSrc ? (
          <Image
            className="my-profile-image"
            src={profileImageSrc}
            alt={`${user.name} 프로필 이미지`}
            fill
            sizes="(max-width: 768px) 64px, 80px"
          />
        ) : (
          <div className="my-profile-placeholder">{user.name.slice(0, 1)}</div>
        )}
      </div>

      <div className="my-profile-info">
        <div className="my-profile-name-row">
          <h2 className="my-profile-name">{user.name}</h2>

          <span className="my-profile-role">
            {user.role === "ADMIN" ? "관리자" : "회원"}
          </span>
        </div>

        <p className="my-profile-email">{user.email}</p>
      </div>
    </section>
  );
}
