"use client";

import Script from "next/script";
import { useEffect, useId, useRef } from "react";

import Modal from "@/components/common/modal/Modal";

interface DaumPostcodeData {
  zonecode: string;
  address: string;
  roadAddress: string;
  jibunAddress: string;
}

interface DaumPostcodeInstance {
  embed: (element: HTMLElement, options?: { autoClose?: boolean }) => void;
}

type DaumPostcodeConstructor = new (options: {
  oncomplete: (data: DaumPostcodeData) => void;
}) => DaumPostcodeInstance;

type DaumWindow = Window & {
  daum?: { Postcode?: DaumPostcodeConstructor };
};

interface AddressSearchModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelect: (postalCode: string, address: string) => void;
  onLoadError: () => void;
  classNamePrefix?: "address-search-layer" | "order-address-layer";
}

function PostcodeEmbed({
  onSelect,
  onClose,
  onLoadError,
  className,
}: Pick<AddressSearchModalProps, "onSelect" | "onClose" | "onLoadError"> & {
  className: string;
}) {
  const layerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const layer = layerRef.current;
    const Postcode = (window as DaumWindow).daum?.Postcode;

    if (!layer || !Postcode) {
      onLoadError();
      onClose();
      return;
    }

    layer.innerHTML = "";
    new Postcode({
      oncomplete: (data) => {
        const selectedAddress =
          data.address || data.roadAddress || data.jibunAddress;
        onSelect(data.zonecode, selectedAddress);
        onClose();
      },
    }).embed(layer, { autoClose: false });

    return () => {
      layer.innerHTML = "";
    };
  }, [onClose, onLoadError, onSelect]);

  return <div ref={layerRef} className={className} />;
}

export default function AddressSearchModal({
  isOpen,
  onClose,
  onSelect,
  onLoadError,
  classNamePrefix = "address-search-layer",
}: AddressSearchModalProps) {
  const titleId = useId();
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  return (
    <>
      <Script
        src="https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js"
        strategy="afterInteractive"
      />

      {isOpen && (
        <Modal
          overlayClassName={`${classNamePrefix}-overlay`}
          contentClassName={classNamePrefix}
          ariaLabelledBy={titleId}
          initialFocusRef={closeButtonRef}
          onClose={onClose}
        >
          <div className={`${classNamePrefix}-header`}>
            <strong id={titleId}>주소 찾기</strong>
            <button
              ref={closeButtonRef}
              type="button"
              className={`${classNamePrefix}-close`}
              onClick={onClose}
              aria-label="주소 검색 닫기"
            >
              ×
            </button>
          </div>
          <PostcodeEmbed
            className={`${classNamePrefix}-content`}
            onSelect={onSelect}
            onClose={onClose}
            onLoadError={onLoadError}
          />
        </Modal>
      )}
    </>
  );
}
