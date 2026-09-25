import type { UserRole } from "@/types/user";

export function isAdminRole(role: UserRole | null | undefined): boolean {
  return role === "ADMIN" || role === "SUPER_ADMIN";
}

export function isSuperAdminRole(role: UserRole | null | undefined): boolean {
  return role === "SUPER_ADMIN";
}
