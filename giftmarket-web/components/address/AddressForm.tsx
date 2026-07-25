"use client";

import { FormEvent, useEffect, useState } from "react";
import type { Address, AddressFormData } from "@/types/address";

interface AddressFormProps {
  address: Address | null;
  onSubmit: (formData: AddressFormData) => void;
  onClose: () => void;
}

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
}: AddressFormProps) {
  const [formData, setFormData] = useState<AddressFormData>(EMPTY_FORM);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    if (address) {
      setFormData({
        name: address.name,
        recipientName: address.recipientName,
        phoneNumber: address.phoneNumber,
        postalCode: address.postalCode,
        address: address.address,
        detailAddress: address.detailAddress,
        isDefault: address.isDefault,
      });
      return;
    }

    setFormData(EMPTY_FORM);
  }, [address]);

  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value, checked, type } = event.target;

    setFormData((currentFormData) => ({
      ...currentFormData,
      [name]: type === "checkbox" ? checked : value,
    }));

    if (errorMessage) {
      setErrorMessage("");
    }
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const hasEmptyRequiredField =
      !formData.name.trim() ||
      !formData.recipientName.trim() ||
      !formData.phoneNumber.trim() ||
      !formData.postalCode.trim() ||
      !formData.address.trim();

    if (hasEmptyRequiredField) {
      setErrorMessage("필수 입력 항목을 모두 입력해주세요.");
      return;
    }

    onSubmit({
      name: formData.name.trim(),
      recipientName: formData.recipientName.trim(),
      phoneNumber: formData.phoneNumber.trim(),
      postalCode: formData.postalCode.trim(),
      address: formData.address.trim(),
      detailAddress: formData.detailAddress.trim(),
      isDefault: formData.isDefault,
    });
  };

  return (
    <div
      className="address-modal-overlay"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) {
          onClose();
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
            onClick={onClose}
          >
            ×
          </button>
        </div>

        <form className="address-form" onSubmit={handleSubmit}>
          <div className="address-form-field">
            <label htmlFor="name" className="address-form-label">
              배송지명 <span className="address-required">*</span>
            </label>

            <input
              id="name"
              name="name"
              type="text"
              className="address-form-input"
              placeholder="예: 우리 집, 회사"
              maxLength={20}
              value={formData.name}
              onChange={handleChange}
            />
          </div>

          <div className="address-form-field">
            <label htmlFor="recipientName" className="address-form-label">
              받는 분 <span className="address-required">*</span>
            </label>

            <input
              id="recipientName"
              name="recipientName"
              type="text"
              className="address-form-input"
              placeholder="받는 분의 이름을 입력해주세요."
              maxLength={30}
              value={formData.recipientName}
              onChange={handleChange}
            />
          </div>

          <div className="address-form-field">
            <label htmlFor="phoneNumber" className="address-form-label">
              휴대폰 번호 <span className="address-required">*</span>
            </label>

            <input
              id="phoneNumber"
              name="phoneNumber"
              type="tel"
              className="address-form-input"
              placeholder="010-0000-0000"
              maxLength={13}
              value={formData.phoneNumber}
              onChange={handleChange}
            />
          </div>

          <div className="address-form-field">
            <label htmlFor="postalCode" className="address-form-label">
              우편번호 <span className="address-required">*</span>
            </label>

            <input
              id="postalCode"
              name="postalCode"
              type="text"
              inputMode="numeric"
              className="address-form-input address-postal-input"
              placeholder="우편번호"
              maxLength={5}
              value={formData.postalCode}
              onChange={handleChange}
            />
          </div>

          <div className="address-form-field">
            <label htmlFor="address" className="address-form-label">
              주소 <span className="address-required">*</span>
            </label>

            <input
              id="address"
              name="address"
              type="text"
              className="address-form-input"
              placeholder="주소를 입력해주세요."
              value={formData.address}
              onChange={handleChange}
            />
          </div>

          <div className="address-form-field">
            <label htmlFor="detailAddress" className="address-form-label">
              상세 주소
            </label>

            <input
              id="detailAddress"
              name="detailAddress"
              type="text"
              className="address-form-input"
              placeholder="상세 주소를 입력해주세요."
              value={formData.detailAddress}
              onChange={handleChange}
            />
          </div>

          <label className="address-default-checkbox">
            <input
              name="isDefault"
              type="checkbox"
              checked={formData.isDefault}
              disabled={address?.isDefault}
              onChange={handleChange}
            />

            <span>기본 배송지로 설정</span>
          </label>

          {errorMessage && (
            <p className="address-form-error" role="alert">
              {errorMessage}
            </p>
          )}

          <div className="address-form-actions">
            <button
              type="button"
              className="address-form-cancel-button"
              onClick={onClose}
            >
              취소
            </button>

            <button type="submit" className="address-form-submit-button">
              {address ? "수정 완료" : "배송지 저장"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
