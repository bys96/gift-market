"use client";

import Script from "next/script";
import { useRef, useState } from "react";

import { formatKoreanPhoneNumber } from "@/utils/phone";

interface OrderRecipientFormProps {
  name: string;
  phone: string;
  postalCode: string;
  address: string;
  addressDetail: string;

  onChange: (
    field: "name" | "phone" | "postalCode" | "address" | "addressDetail",
    value: string,
  ) => void;
}

interface KakaoPostcodeData {
  zonecode: string;
  address: string;
  roadAddress: string;
  jibunAddress: string;
}

interface KakaoPostcodeInstance {
  embed: (
    element: HTMLElement,
    options?: {
      autoClose?: boolean;
    },
  ) => void;
}

type KakaoPostcodeConstructor = new (options: {
  oncomplete: (data: KakaoPostcodeData) => void;
}) => KakaoPostcodeInstance;

type KakaoWindow = Window & {
  kakao?: {
    Postcode?: KakaoPostcodeConstructor;
  };
};

export default function OrderRecipientForm({
  name,
  phone,
  postalCode,
  address,
  addressDetail,
  onChange,
}: OrderRecipientFormProps) {
  const postcodeLayerRef = useRef<HTMLDivElement | null>(null);

  const detailAddressRef = useRef<HTMLInputElement | null>(null);

  const [isPostcodeScriptLoaded, setIsPostcodeScriptLoaded] = useState(false);

  const [isAddressLayerOpen, setIsAddressLayerOpen] = useState(false);

  const handlePhoneChange = (value: string) => {
    onChange("phone", formatKoreanPhoneNumber(value));
  };

  const closeAddressLayer = () => {
    setIsAddressLayerOpen(false);

    if (postcodeLayerRef.current) {
      postcodeLayerRef.current.innerHTML = "";
    }
  };

  const handleAddressSearch = () => {
    const kakaoWindow = window as KakaoWindow;

    const Postcode = kakaoWindow.kakao?.Postcode;

    if (!isPostcodeScriptLoaded || !Postcode) {
      window.alert(
        "주소 검색 서비스를 불러오는 중입니다. 잠시 후 다시 시도해주세요.",
      );

      return;
    }

    setIsAddressLayerOpen(true);

    requestAnimationFrame(() => {
      const layer = postcodeLayerRef.current;

      if (!layer) {
        return;
      }

      layer.innerHTML = "";

      new Postcode({
        oncomplete: (data) => {
          /*
           * 사용자가 검색 결과에서 선택한 주소.
           * Kakao 우편번호 서비스의 address 값을
           * 주문 기본주소 Snapshot으로 사용합니다.
           */
          const selectedAddress =
            data.address || data.roadAddress || data.jibunAddress;

          onChange("postalCode", data.zonecode);

          onChange("address", selectedAddress);

          /*
           * 주소를 다시 검색한 경우
           * 이전 상세주소를 그대로 두면 잘못된 주소가
           * 주문될 수 있으므로 초기화합니다.
           */
          onChange("addressDetail", "");

          closeAddressLayer();

          requestAnimationFrame(() => {
            detailAddressRef.current?.focus();
          });
        },
      }).embed(layer, {
        autoClose: false,
      });
    });
  };

  return (
    <>
      <Script
        src="https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js"
        strategy="afterInteractive"
        onLoad={() => setIsPostcodeScriptLoaded(true)}
      />

      <section className="order-section">
        <div className="order-section-header">
          <h2 className="order-section-title">배송지 정보</h2>
        </div>

        <div className="order-form">
          <div className="order-form-group">
            <label className="order-form-label" htmlFor="recipient-name">
              받는 분
            </label>

            <input
              id="recipient-name"
              className="order-form-input"
              type="text"
              value={name}
              onChange={(event) => onChange("name", event.target.value)}
              placeholder="받는 분 이름"
              autoComplete="shipping name"
              maxLength={100}
            />
          </div>

          <div className="order-form-group">
            <label className="order-form-label" htmlFor="recipient-phone">
              연락처
            </label>

            <input
              id="recipient-phone"
              className="order-form-input"
              type="tel"
              inputMode="numeric"
              value={phone}
              onChange={(event) => handlePhoneChange(event.target.value)}
              placeholder="010-0000-0000"
              autoComplete="shipping tel"
              maxLength={13}
            />
          </div>

          <div className="order-form-group">
            <label className="order-form-label">주소</label>

            <div className="order-address-search-row">
              <input
                className="order-form-input order-postal-code-input"
                type="text"
                value={postalCode}
                placeholder="우편번호"
                readOnly
                tabIndex={-1}
                aria-label="우편번호"
              />

              <button
                type="button"
                className="order-address-search-button"
                onClick={handleAddressSearch}
              >
                주소 찾기
              </button>
            </div>
          </div>

          <div className="order-form-group">
            <label className="order-form-label" htmlFor="recipient-address">
              기본 주소
            </label>

            <input
              id="recipient-address"
              className="order-form-input order-form-input-readonly"
              type="text"
              value={address}
              placeholder="주소 찾기를 이용해주세요."
              readOnly
              tabIndex={-1}
              autoComplete="shipping address-line1"
            />
          </div>

          <div className="order-form-group">
            <label
              className="order-form-label"
              htmlFor="recipient-address-detail"
            >
              상세 주소
            </label>

            <input
              ref={detailAddressRef}
              id="recipient-address-detail"
              className="order-form-input"
              type="text"
              value={addressDetail}
              onChange={(event) =>
                onChange("addressDetail", event.target.value)
              }
              placeholder="동, 호수 등 상세주소를 입력해주세요."
              autoComplete="shipping address-line2"
              maxLength={500}
            />
          </div>
        </div>
      </section>

      {isAddressLayerOpen && (
        <div
          className="order-address-layer-overlay"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) {
              closeAddressLayer();
            }
          }}
        >
          <div
            className="order-address-layer"
            role="dialog"
            aria-modal="true"
            aria-label="주소 검색"
          >
            <div className="order-address-layer-header">
              <strong>주소 찾기</strong>

              <button
                type="button"
                className="order-address-layer-close"
                onClick={closeAddressLayer}
                aria-label="주소 검색 닫기"
              >
                ×
              </button>
            </div>

            <div
              ref={postcodeLayerRef}
              className="order-address-layer-content"
            />
          </div>
        </div>
      )}
    </>
  );
}
