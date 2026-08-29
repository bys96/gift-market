"use client";

import { useRef, useState } from "react";

import Modal from "@/components/common/modal/Modal";

interface ViewerImage {
  url: string;
}

interface ImageViewerModalProps {
  images: ViewerImage[];
  initialIndex: number;
  onClose: () => void;
  label?: string;
}

export default function ImageViewerModal({
  images,
  initialIndex,
  onClose,
  label = "첨부 이미지",
}: ImageViewerModalProps) {
  const [currentIndex, setCurrentIndex] = useState(() =>
    Math.min(Math.max(initialIndex, 0), images.length - 1),
  );
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  if (images.length === 0) return null;

  const currentImage = images[currentIndex];

  return (
    <Modal
      overlayClassName="return-image-viewer-overlay"
      contentClassName="return-image-viewer"
      ariaLabel={`${label} 뷰어`}
      initialFocusRef={closeButtonRef}
      onClose={onClose}
      onContentKeyDown={(event) => {
        if (event.key === "ArrowLeft") {
          setCurrentIndex((index) => Math.max(0, index - 1));
        }
        if (event.key === "ArrowRight") {
          setCurrentIndex((index) =>
            Math.min(images.length - 1, index + 1),
          );
        }
      }}
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
        <img src={currentImage.url} alt={`${label} ${currentIndex + 1}`} />
        <button
          type="button"
          className="return-image-viewer-navigation next"
          aria-label="다음 이미지"
          disabled={currentIndex === images.length - 1}
          onClick={() =>
            setCurrentIndex((index) =>
              Math.min(images.length - 1, index + 1),
            )
          }
        >
          ›
        </button>
      </div>
      <span className="return-image-viewer-counter" aria-live="polite">
        {currentIndex + 1} / {images.length}
      </span>
    </Modal>
  );
}
