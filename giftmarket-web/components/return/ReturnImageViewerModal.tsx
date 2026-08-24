"use client";

import { useEffect, useRef, useState } from "react";
import type { ReturnRequestImage } from "@/types/return";

interface Props {
  images: ReturnRequestImage[];
  initialIndex: number;
  onClose: () => void;
  label?: string;
}

export default function ReturnImageViewerModal({
  images,
  initialIndex,
  onClose,
  label = "반품 첨부 이미지",
}: Props) {
  const [currentIndex, setCurrentIndex] = useState(() =>
    Math.min(Math.max(initialIndex, 0), images.length - 1),
  );
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
      if (event.key === "ArrowLeft") {
        setCurrentIndex((index) => Math.max(0, index - 1));
      }
      if (event.key === "ArrowRight") {
        setCurrentIndex((index) => Math.min(images.length - 1, index + 1));
      }
    };

    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", handleKeyDown);
    closeButtonRef.current?.focus();

    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [images.length, onClose]);

  if (images.length === 0) return null;

  const currentImage = images[currentIndex];

  return (
    <div
      className="return-image-viewer-overlay"
      role="dialog"
      aria-modal="true"
      aria-label={`${label} 뷰어`}
      onClick={onClose}
    >
      <div
        className="return-image-viewer"
        onClick={(event) => event.stopPropagation()}
      >
        <button
          ref={closeButtonRef}
          type="button"
          className="return-image-viewer-close"
          aria-label="이미지 뷰어 닫기"
          onClick={onClose}
        >
          ×
        </button>
        <div className="return-image-viewer-stage">
          <button
            type="button"
            className="return-image-viewer-navigation previous"
            aria-label="이전 이미지"
            disabled={currentIndex === 0}
            onClick={() => setCurrentIndex((index) => Math.max(0, index - 1))}
          >
            ‹
          </button>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={currentImage.url}
            alt={`${label} ${currentIndex + 1}`}
          />
          <button
            type="button"
            className="return-image-viewer-navigation next"
            aria-label="다음 이미지"
            disabled={currentIndex === images.length - 1}
            onClick={() =>
              setCurrentIndex((index) => Math.min(images.length - 1, index + 1))
            }
          >
            ›
          </button>
        </div>
        <span className="return-image-viewer-counter" aria-live="polite">
          {currentIndex + 1} / {images.length}
        </span>
      </div>
    </div>
  );
}
