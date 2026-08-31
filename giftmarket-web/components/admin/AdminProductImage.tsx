"use client";

import Image from "next/image";
import { useState } from "react";

import { resolveImageUrl } from "@/utils/image-url";

export default function AdminProductImage({ imageKey, name, sizes = "56px" }: { imageKey: string | null; name: string; sizes?: string }) {
  const [failed, setFailed] = useState(false);
  const source = resolveImageUrl(imageKey);
  return source && !failed
    ? <Image src={source} alt={`${name} 이미지`} fill sizes={sizes} onError={() => setFailed(true)} />
    : <span aria-hidden="true">{name.slice(0, 1)}</span>;
}
