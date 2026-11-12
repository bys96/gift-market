"use client";

import { useEffect } from "react";
import { initializeAuth } from "@/lib/auth-initialization";
import { observeAuthRecovery } from "@/lib/auth-recovery";

export default function AuthInitializer() {
  useEffect(() => {
    void initializeAuth();
    return observeAuthRecovery();
  }, []);

  return null;
}
