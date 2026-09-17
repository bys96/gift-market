"use client";

import Image from "next/image";
import { type TouchEventHandler, useRef } from "react";

import Modal from "@/components/common/modal/Modal";

interface ProductImageModalProps {
  imageUrls: string[];
  currentIndex: number;
  productName: string;
  onPrevious: () => void;
  onNext: () => void;
  onTouchStart: TouchEventHandler<HTMLDivElement>;
  onTouchEnd: TouchEventHandler<HTMLDivElement>;
  onClose: () => void;
}

export default function ProductImageModal({
  imageUrls,
  currentIndex,
  productName,
  onPrevious,
  onNext,
  onTouchStart,
  onTouchEnd,
  onClose,
}: ProductImageModalProps) {
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  return (
    <Modal
      overlayClassName="product-image-modal"
      contentClassName="product-image-modal-content"
      ariaLabel={`${productName} 이미지 확대`}
      initialFocusRef={closeButtonRef}
      onClose={onClose}
      onContentKeyDown={(event) => {
        if (imageUrls.length < 2) return;
        if (event.key === "ArrowLeft") {
          event.preventDefault();
          onPrevious();
        } else if (event.key === "ArrowRight") {
          event.preventDefault();
          onNext();
        }
      }}
    >
      <button
        ref={closeButtonRef}
        type="button"
        className="product-image-modal-close"
        aria-label="이미지 확대 닫기"
        onClick={onClose}
      >
        ×
      </button>

      <div
        className="product-image-modal-stage"
        onTouchStart={onTouchStart}
        onTouchEnd={onTouchEnd}
      >
        <Image
          src={imageUrls[currentIndex]}
          alt={`${productName} 확대 이미지 ${currentIndex + 1}`}
          fill
          priority
          sizes="100vw"
          className="product-image-modal-image"
        />
        {imageUrls.length > 1 && (
          <>
            <button
              type="button"
              className="product-image-modal-arrow product-image-modal-arrow-previous"
              aria-label="이전 확대 이미지"
              onClick={onPrevious}
            >
              ‹
            </button>
            <button
              type="button"
              className="product-image-modal-arrow product-image-modal-arrow-next"
              aria-label="다음 확대 이미지"
              onClick={onNext}
            >
              ›
            </button>
            <span className="product-image-modal-count" aria-live="polite">
              {currentIndex + 1} / {imageUrls.length}
            </span>
          </>
        )}
      </div>
    </Modal>
  );
}
