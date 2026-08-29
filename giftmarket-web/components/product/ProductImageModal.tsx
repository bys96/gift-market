"use client";

import Image from "next/image";
import { useRef } from "react";

import Modal from "@/components/common/modal/Modal";

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
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  return (
    <Modal
      overlayClassName="product-image-modal"
      contentClassName="product-image-modal-content"
      ariaLabel={`${productName} 이미지 확대`}
      initialFocusRef={closeButtonRef}
      onClose={onClose}
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

      <Image
        src={imageUrl}
        alt={`${productName} 확대 이미지`}
        fill
        priority
        sizes="100vw"
        className="product-image-modal-image"
      />
    </Modal>
  );
}
