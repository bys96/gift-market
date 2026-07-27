export type AuthProvider = "GOOGLE" | "KAKAO" | "LOCAL";
export type UserRole = "USER" | "ADMIN";

export interface User {
  id: number;
  email: string;
  name: string;
  profileImageUrl: string;
  provider: AuthProvider;
  role: UserRole;
}
