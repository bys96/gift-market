"use client";

import { useEffect } from "react";
import { initializeAuth } from "@/lib/auth-initialization";

export default function AuthInitializer() {
  useEffect(() => {
    void initializeAuth();
  }, []);

  return null;
}
