"use client";

import { useRef, useState } from "react";

import AddressSearchModal from "@/components/address/AddressSearchModal";
import Modal from "@/components/common/modal/Modal";
import { formatKoreanPhoneNumber } from "@/utils/phone";
import type { Address } from "@/types/address";

interface OrderRecipientFormProps {
  addresses: Address[];
  addressMode: "saved" | "new";
  selectedAddressId: number | null;
  isAddressLoading: boolean;
  name: string;
  phone: string;
  postalCode: string;
  address: string;
  addressDetail: string;

  onChange: (
    field: "name" | "phone" | "postalCode" | "address" | "addressDetail",
    value: string,
  ) => void;
  onSelectAddress: (addressId: number) => void;
  onSelectNewAddress: () => void;
  saveAddress: boolean;
  onSaveAddressChange: (checked: boolean) => void;
  addressName: string;
  onAddressNameChange: (value: string) => void;
  setAsDefault: boolean;
  onSetAsDefaultChange: (checked: boolean) => void;
  canSaveAddress: boolean;
}

export default function OrderRecipientForm({
  addresses,
  addressMode,
  selectedAddressId,
  isAddressLoading,
  name,
  phone,
  postalCode,
  address,
  addressDetail,
  onChange,
  onSelectAddress,
  onSelectNewAddress,
  saveAddress,
  onSaveAddressChange,
  addressName,
  onAddressNameChange,
  setAsDefault,
  onSetAsDefaultChange,
  canSaveAddress,
}: OrderRecipientFormProps) {
  const detailAddressRef = useRef<HTMLInputElement | null>(null);
  const addressSearchButtonRef = useRef<HTMLButtonElement | null>(null);
  const selectorCloseButtonRef = useRef<HTMLButtonElement | null>(null);

  const [isAddressLayerOpen, setIsAddressLayerOpen] = useState(false);
  const [isSelectorOpen, setIsSelectorOpen] = useState(false);

  const handlePhoneChange = (value: string) => {
    onChange("phone", formatKoreanPhoneNumber(value));
  };

  const selectedAddress = addresses.find(
    (savedAddress) => savedAddress.id === selectedAddressId,
  );

  return (
    <>
      <section className="order-section">
        <div className="order-section-header">
          <h2 className="order-section-title">배송지 정보</h2>
        </div>

        <div className="order-address-selector">
          {isAddressLoading ? (
            <p className="order-address-selector-message">배송지를 불러오는 중입니다.</p>
          ) : (
            <div className="order-address-current">
              <div>
                <strong>
                  {addressMode === "saved" ? selectedAddress?.name : "새 배송지"}
                </strong>
                {selectedAddress?.isDefault && <em>기본 배송지</em>}
                <p>
                  {addressMode === "saved"
                    ? `${selectedAddress?.recipientName ?? ""} · ${selectedAddress?.phoneNumber ?? ""}`
                    : "배송 정보를 직접 입력해주세요."}
                </p>
              </div>
              <button type="button" onClick={() => setIsSelectorOpen(true)}>
                배송지 변경
              </button>
            </div>
          )}
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
              readOnly={addressMode === "saved"}
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
              readOnly={addressMode === "saved"}
            />
          </div>

          <div className="order-form-group">
            <label className="order-form-label">주소</label>

            <div
              className={`order-address-search-row ${
                addressMode === "saved" ? "is-readonly" : ""
              }`}
            >
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
                ref={addressSearchButtonRef}
                hidden={addressMode === "saved"}
                type="button"
                className="order-address-search-button"
                onClick={() => setIsAddressLayerOpen(true)}
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
              readOnly={addressMode === "saved"}
            />
          </div>

          {addressMode === "new" && (
            <div className="order-save-address-box">
              <label className="order-save-address-check">
                <input
                  type="checkbox"
                  checked={saveAddress}
                  onChange={(event) => onSaveAddressChange(event.target.checked)}
                  disabled={!canSaveAddress}
                />
                배송지에 저장
              </label>

              {!canSaveAddress && (
                <p>배송지는 최대 10개까지 등록할 수 있습니다.</p>
              )}

              {saveAddress && (
                <div className="order-save-address-fields">
                  <div className="order-form-group">
                    <label className="order-form-label" htmlFor="order-address-name">배송지명</label>
                    <input
                      id="order-address-name"
                      className="order-form-input"
                      value={addressName}
                      onChange={(event) => onAddressNameChange(event.target.value)}
                      placeholder="예: 우리집, 회사"
                      maxLength={20}
                    />
                  </div>
                  <label className="order-save-address-check">
                    <input
                      type="checkbox"
                      checked={setAsDefault}
                      onChange={(event) => onSetAsDefaultChange(event.target.checked)}
                    />
                    기본 배송지로 설정
                  </label>
                </div>
              )}
            </div>
          )}
        </div>
      </section>

      {isSelectorOpen && (
        <Modal
          overlayClassName="order-address-select-overlay"
          contentClassName="order-address-select-modal"
          ariaLabelledBy="order-address-select-title"
          initialFocusRef={selectorCloseButtonRef}
          onClose={() => setIsSelectorOpen(false)}
        >
          <div className="order-address-select-header">
            <strong id="order-address-select-title">배송지 변경</strong>
            <button ref={selectorCloseButtonRef} type="button" onClick={() => setIsSelectorOpen(false)} aria-label="배송지 선택 닫기">×</button>
          </div>
          <div className="order-address-options" role="radiogroup" aria-label="배송지 선택">
            {addresses.map((savedAddress) => (
              <label key={savedAddress.id} className={`order-address-option ${selectedAddressId === savedAddress.id && addressMode === "saved" ? "is-selected" : ""}`}>
                <input
                  type="radio"
                  name="order-address"
                  checked={selectedAddressId === savedAddress.id && addressMode === "saved"}
                  onChange={() => {
                    onSelectAddress(savedAddress.id);
                    setIsSelectorOpen(false);
                  }}
                />
                <span>
                  <strong>{savedAddress.name}</strong>
                  {savedAddress.isDefault && <em>기본 배송지</em>}
                  <small>{savedAddress.recipientName} · {savedAddress.phoneNumber}<br />[{savedAddress.postalCode}] {savedAddress.address} {savedAddress.detailAddress ?? ""}</small>
                </span>
              </label>
            ))}
            <label className={`order-address-option ${addressMode === "new" ? "is-selected" : ""}`}>
              <input
                type="radio"
                name="order-address"
                checked={addressMode === "new"}
                onChange={() => {
                  onSelectNewAddress();
                  setIsSelectorOpen(false);
                }}
              />
              <span><strong>새 배송지 입력</strong><small>새로운 배송 정보를 직접 입력합니다.</small></span>
            </label>
          </div>
        </Modal>
      )}

      <AddressSearchModal
        isOpen={isAddressLayerOpen}
        classNamePrefix="order-address-layer"
        onClose={() => setIsAddressLayerOpen(false)}
        onSelect={(postalCode, selectedAddress) => {
          onChange("postalCode", postalCode);
          onChange("address", selectedAddress);
          onChange("addressDetail", "");
          requestAnimationFrame(() => detailAddressRef.current?.focus());
        }}
        onLoadError={() => window.alert("주소 검색 서비스를 불러오는 중입니다. 잠시 후 다시 시도해주세요.")}
      />
    </>
  );
}
