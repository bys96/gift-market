"use client";

import { useAuthStore } from "@/stores/auth-store";

export default function Home() {
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  return (
    <main style={{ padding: 20 }}>
      <h1>Gift Market</h1>

      <p>로그인 여부 : {isAuthenticated ? "로그인" : "로그아웃"}</p>

      <pre>{JSON.stringify(user, null, 2)}</pre>
    </main>
  );
}
