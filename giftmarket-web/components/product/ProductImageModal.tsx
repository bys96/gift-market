"use client";

import Image from "next/image";
import { useEffect } from "react";

interface ProductImageModalProps {
  imageUrl: string;
  productName: string;
  onClose: () => void;
}

export default function ProductImageModal({
  imageUrl,
  productName,
  onClose,
}: ProductImageModalProps) {
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onClose();
      }
    };

    const originalOverflow = document.body.style.overflow;

    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", handleKeyDown);

    return () => {
      document.body.style.overflow = originalOverflow;
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [onClose]);

  return (
    <div
      className="product-image-modal"
      role="dialog"
      aria-modal="true"
      aria-label={`${productName} 이미지 확대`}
      onClick={onClose}
    >
      <button
        type="button"
        className="product-image-modal-close"
        aria-label="이미지 확대 닫기"
        onClick={onClose}
      >
        ×
      </button>

      <div
        className="product-image-modal-content"
        onClick={(event) => event.stopPropagation()}
      >
        <Image
          src={imageUrl}
          alt={`${productName} 확대 이미지`}
          fill
          priority
          sizes="100vw"
          className="product-image-modal-image"
        />
      </div>
    </div>
  );
}
