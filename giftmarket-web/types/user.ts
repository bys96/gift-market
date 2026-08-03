export type AuthProvider = "GOOGLE" | "KAKAO" | "LOCAL";
export type UserRole = "USER" | "SELLER" | "ADMIN";

export interface User {
  id: number;
  email: string;
  name: string;
  profileImageUrl: string;
  provider: AuthProvider;
  role: UserRole;
}

export const roleLabel = {
  USER: "일반 회원",
  SELLER: "판매자",
  ADMIN: "관리자",
};
