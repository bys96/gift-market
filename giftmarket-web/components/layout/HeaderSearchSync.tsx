"use client";

import { useEffect } from "react";
import { useSearchParams } from "next/navigation";

interface Props {
  pathname: string;
  onNavigate: (search: { location: string; keyword: string }) => void;
}

// Keep query observation inside its own Suspense boundary so the header stays prerendered.
export default function HeaderSearchSync({ pathname, onNavigate }: Props) {
  const searchParams = useSearchParams();
  const location = `${pathname}?${searchParams.toString()}`;
  const keyword = pathname === "/products" ? searchParams.get("keyword")?.trim() ?? "" : "";

  useEffect(() => {
    onNavigate({ location, keyword });
  }, [location, keyword, onNavigate]);

  return null;
}
