"use client";

import Script from "next/script";
import { ChangeEvent, FormEvent, useEffect, useRef, useState } from "react";

import type { Address, AddressFormData } from "@/types/address";
import { formatKoreanPhoneNumber } from "@/utils/phone";

interface AddressFormProps {
  address: Address | null;
  onSubmit: (formData: AddressFormData) => void | Promise<void>;
  onClose: () => void;
  isSubmitting?: boolean;
  submitErrorMessage?: string;
}

interface DaumPostcodeData {
  zonecode: string;
  address: string;
  roadAddress: string;
  jibunAddress: string;
}

interface DaumPostcodeInstance {
  embed: (
    element: HTMLElement,
    options?: {
      autoClose?: boolean;
    },
  ) => void;
}

type DaumPostcodeConstructor = new (options: {
  oncomplete: (data: DaumPostcodeData) => void;
}) => DaumPostcodeInstance;

type DaumWindow = Window & {
  daum?: {
    Postcode?: DaumPostcodeConstructor;
  };
};

const EMPTY_FORM: AddressFormData = {
  name: "",
  recipientName: "",
  phoneNumber: "",
  postalCode: "",
  address: "",
  detailAddress: "",
  isDefault: false,
};

export default function AddressForm({
  address,
  onSubmit,
  onClose,
  isSubmitting = false,
  submitErrorMessage = "",
}: AddressFormProps) {
  const postcodeLayerRef = useRef<HTMLDivElement | null>(null);

  const detailAddressRef = useRef<HTMLInputElement | null>(null);

  const [formData, setFormData] = useState<AddressFormData>(EMPTY_FORM);

  const [errorMessage, setErrorMessage] = useState("");

  const [isAddressLayerOpen, setIsAddressLayerOpen] = useState(false);

  useEffect(() => {
    setErrorMessage("");
    setIsAddressLayerOpen(false);

    if (address) {
      setFormData({
        name: address.name,
        recipientName: address.recipientName,
        phoneNumber: address.phoneNumber,
        postalCode: address.postalCode,
        address: address.address,
        detailAddress: address.detailAddress ?? "",
        isDefault: address.isDefault,
      });

      return;
    }

    setFormData(EMPTY_FORM);
  }, [address]);

  const clearErrorMessage = () => {
    if (errorMessage) {
      setErrorMessage("");
    }
  };

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    const { name, value, checked, type } = event.target;

    setFormData((currentFormData) => ({
      ...currentFormData,
      [name]: type === "checkbox" ? checked : value,
    }));

    clearErrorMessage();
  };

  const handlePhoneChange = (event: ChangeEvent<HTMLInputElement>) => {
    const formattedPhoneNumber = formatKoreanPhoneNumber(event.target.value);

    setFormData((currentFormData) => ({
      ...currentFormData,
      phoneNumber: formattedPhoneNumber,
    }));

    clearErrorMessage();
  };

  const closeAddressLayer = () => {
    setIsAddressLayerOpen(false);

    if (postcodeLayerRef.current) {
      postcodeLayerRef.current.innerHTML = "";
    }
  };

  const handleAddressSearch = () => {
    if (isSubmitting) {
      return;
    }

    const daumWindow = window as DaumWindow;

    const Postcode = daumWindow.daum?.Postcode;

    /*
     * Next.js Script는 이미 로드된 스크립트를
     * 다시 mount할 때 onLoad가 재실행되지 않을 수 있으므로
     * 별도 state가 아니라 실제 전역 객체 존재 여부로 판단한다.
     */
    if (!Postcode) {
      setErrorMessage(
        "주소 검색 서비스를 불러오지 못했습니다. 페이지를 새로고침한 후 다시 시도해주세요.",
      );
      return;
    }

    setErrorMessage("");
    setIsAddressLayerOpen(true);

    /*
     * 주소검색 Layer가 DOM에 생성된 다음
     * Daum Postcode를 embed 해야 한다.
     */
    requestAnimationFrame(() => {
      const layer = postcodeLayerRef.current;

      if (!layer) {
        return;
      }

      layer.innerHTML = "";

      new Postcode({
        oncomplete: (data) => {
          const selectedAddress =
            data.roadAddress || data.jibunAddress || data.address;

          setFormData((currentFormData) => ({
            ...currentFormData,
            postalCode: data.zonecode,
            address: selectedAddress,

            /*
             * 기본 주소를 변경했는데 기존 상세주소가
             * 남아 있으면 오배송 위험이 있으므로 초기화한다.
             */
            detailAddress: "",
          }));

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

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (isSubmitting) {
      return;
    }

    if (!formData.name.trim()) {
      setErrorMessage("배송지명을 입력해주세요.");
      return;
    }

    if (!formData.recipientName.trim()) {
      setErrorMessage("받는 분 이름을 입력해주세요.");
      return;
    }

    if (!formData.phoneNumber.trim()) {
      setErrorMessage("연락처를 입력해주세요.");
      return;
    }

    const phonePattern = /^0\d{1,2}-\d{3,4}-\d{4}$/;

    if (!phonePattern.test(formData.phoneNumber)) {
      setErrorMessage("올바른 연락처를 입력해주세요.");
      return;
    }

    /*
     * 우편번호와 기본주소는 주소검색을 통해
     * 하나의 세트로 입력된다.
     */
    if (!formData.postalCode.trim() || !formData.address.trim()) {
      setErrorMessage("주소를 입력해주세요.");
      return;
    }

    await onSubmit({
      name: formData.name.trim(),
      recipientName: formData.recipientName.trim(),
      phoneNumber: formData.phoneNumber.trim(),
      postalCode: formData.postalCode.trim(),
      address: formData.address.trim(),
      detailAddress: formData.detailAddress.trim(),
      isDefault: formData.isDefault,
    });
  };

  const handleClose = () => {
    if (isSubmitting) {
      return;
    }

    closeAddressLayer();
    onClose();
  };

  return (
    <>
      <Script
        src="https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js"
        strategy="afterInteractive"
      />

      <div
        className="address-modal-overlay"
        role="presentation"
        onMouseDown={(event) => {
          if (event.target === event.currentTarget) {
            handleClose();
          }
        }}
      >
        <div
          className="address-modal"
          role="dialog"
          aria-modal="true"
          aria-labelledby="address-form-title"
        >
          <div className="address-modal-header">
            <h2 id="address-form-title" className="address-modal-title">
              {address ? "배송지 수정" : "배송지 추가"}
            </h2>

            <button
              type="button"
              className="address-modal-close"
              aria-label="배송지 입력창 닫기"
              onClick={handleClose}
              disabled={isSubmitting}
            >
              ×
            </button>
          </div>

          <form className="address-form" onSubmit={handleSubmit}>
            <div className="address-form-field">
              <label htmlFor="address-name" className="address-form-label">
                배송지명
                <span className="address-required">*</span>
              </label>

              <input
                id="address-name"
                name="name"
                type="text"
                className="address-form-input"
                placeholder="예: 우리집, 회사"
                maxLength={20}
                value={formData.name}
                onChange={handleChange}
                disabled={isSubmitting}
              />
            </div>

            <div className="address-form-field">
              <label
                htmlFor="address-recipient-name"
                className="address-form-label"
              >
                받는 분<span className="address-required">*</span>
              </label>

              <input
                id="address-recipient-name"
                name="recipientName"
                type="text"
                className="address-form-input"
                placeholder="받는 분의 이름을 입력해주세요."
                maxLength={30}
                autoComplete="shipping name"
                value={formData.recipientName}
                onChange={handleChange}
                disabled={isSubmitting}
              />
            </div>

            <div className="address-form-field">
              <label
                htmlFor="address-phone-number"
                className="address-form-label"
              >
                연락처
                <span className="address-required">*</span>
              </label>

              <input
                id="address-phone-number"
                name="phoneNumber"
                type="tel"
                inputMode="numeric"
                className="address-form-input"
                placeholder="010-0000-0000"
                maxLength={13}
                autoComplete="shipping tel"
                value={formData.phoneNumber}
                onChange={handlePhoneChange}
                disabled={isSubmitting}
              />
            </div>

            <div className="address-form-field">
              <label
                htmlFor="address-postal-code"
                className="address-form-label"
              >
                주소
                <span className="address-required">*</span>
              </label>

              <div className="address-search-row">
                <input
                  id="address-postal-code"
                  type="text"
                  className="address-form-input address-postal-input"
                  placeholder="우편번호"
                  value={formData.postalCode}
                  readOnly
                  tabIndex={-1}
                  aria-label="우편번호"
                />

                <button
                  type="button"
                  className="address-search-button"
                  onClick={handleAddressSearch}
                  disabled={isSubmitting}
                >
                  주소 찾기
                </button>
              </div>
            </div>

            <div className="address-form-field">
              <label
                htmlFor="address-base-address"
                className="address-form-label"
              >
                기본 주소
              </label>

              <input
                id="address-base-address"
                type="text"
                className="address-form-input address-form-input-readonly"
                placeholder="주소 찾기를 이용해주세요."
                value={formData.address}
                readOnly
                tabIndex={-1}
                autoComplete="shipping address-line1"
              />
            </div>

            <div className="address-form-field">
              <label htmlFor="address-detail" className="address-form-label">
                상세 주소
              </label>

              <input
                ref={detailAddressRef}
                id="address-detail"
                name="detailAddress"
                type="text"
                className="address-form-input"
                placeholder="동, 호수 등 상세주소를 입력해주세요."
                maxLength={500}
                autoComplete="shipping address-line2"
                value={formData.detailAddress}
                onChange={handleChange}
                disabled={isSubmitting}
              />
            </div>

            <label className="address-default-checkbox">
              <input
                name="isDefault"
                type="checkbox"
                checked={formData.isDefault}
                disabled={isSubmitting || address?.isDefault === true}
                onChange={handleChange}
              />

              <span>기본 배송지로 설정</span>
            </label>

            {(errorMessage || submitErrorMessage) && (
              <p className="address-form-error" role="alert">
                {errorMessage || submitErrorMessage}
              </p>
            )}

            <div className="address-form-actions">
              <button
                type="button"
                className="address-form-cancel-button"
                onClick={handleClose}
                disabled={isSubmitting}
              >
                취소
              </button>

              <button
                type="submit"
                className="address-form-submit-button"
                disabled={isSubmitting}
              >
                {isSubmitting
                  ? "저장 중..."
                  : address
                    ? "수정 완료"
                    : "배송지 저장"}
              </button>
            </div>
          </form>
        </div>
      </div>

      {isAddressLayerOpen && (
        <div
          className="address-search-layer-overlay"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) {
              closeAddressLayer();
            }
          }}
        >
          <div
            className="address-search-layer"
            role="dialog"
            aria-modal="true"
            aria-label="주소 검색"
          >
            <div className="address-search-layer-header">
              <strong>주소 찾기</strong>

              <button
                type="button"
                className="address-search-layer-close"
                onClick={closeAddressLayer}
                aria-label="주소 검색 닫기"
              >
                ×
              </button>
            </div>

            <div
              ref={postcodeLayerRef}
              className="address-search-layer-content"
            />
          </div>
        </div>
      )}
    </>
  );
}
