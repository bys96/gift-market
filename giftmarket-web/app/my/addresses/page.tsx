"use client";

import { useState } from "react";
import type { Address, AddressFormData } from "@/types/address";
import AddressCard from "@/components/address/AddressCard";
import AddressForm from "@/components/address/AddressForm";

const INITIAL_ADDRESSES: Address[] = [
  {
    id: 1,
    name: "우리 집",
    recipientName: "홍길동",
    phoneNumber: "010-1234-5678",
    postalCode: "06236",
    address: "서울특별시 강남구 테헤란로 123",
    detailAddress: "101동 1001호",
    isDefault: true,
  },
  {
    id: 2,
    name: "회사",
    recipientName: "홍길동",
    phoneNumber: "010-1234-5678",
    postalCode: "04524",
    address: "서울특별시 중구 세종대로 110",
    detailAddress: "10층",
    isDefault: false,
  },
];

export default function MyAddressesPage() {
  const [addresses, setAddresses] = useState<Address[]>(INITIAL_ADDRESSES);
  const [editingAddress, setEditingAddress] = useState<Address | null>(null);
  const [isFormOpen, setIsFormOpen] = useState(false);

  const handleOpenCreateForm = () => {
    setEditingAddress(null);
    setIsFormOpen(true);
  };

  const handleOpenEditForm = (address: Address) => {
    setEditingAddress(address);
    setIsFormOpen(true);
  };

  const handleCloseForm = () => {
    setEditingAddress(null);
    setIsFormOpen(false);
  };

  const handleSaveAddress = (formData: AddressFormData) => {
    if (editingAddress) {
      setAddresses((currentAddresses) => {
        let updatedAddresses = currentAddresses.map((address) =>
          address.id === editingAddress.id
            ? {
                ...address,
                ...formData,
              }
            : address,
        );

        if (formData.isDefault) {
          updatedAddresses = updatedAddresses.map((address) => ({
            ...address,
            isDefault: address.id === editingAddress.id,
          }));
        }

        return updatedAddresses;
      });
    } else {
      setAddresses((currentAddresses) => {
        const nextId =
          currentAddresses.length > 0
            ? Math.max(...currentAddresses.map((address) => address.id)) + 1
            : 1;

        const newAddress: Address = {
          id: nextId,
          ...formData,
          isDefault: currentAddresses.length === 0 ? true : formData.isDefault,
        };

        if (newAddress.isDefault) {
          return [
            ...currentAddresses.map((address) => ({
              ...address,
              isDefault: false,
            })),
            newAddress,
          ];
        }

        return [...currentAddresses, newAddress];
      });
    }

    handleCloseForm();
  };

  const handleDeleteAddress = (addressId: number) => {
    const targetAddress = addresses.find((address) => address.id === addressId);

    if (!targetAddress) {
      return;
    }

    const isConfirmed = window.confirm(
      `"${targetAddress.name}" 배송지를 삭제하시겠습니까?`,
    );

    if (!isConfirmed) {
      return;
    }

    setAddresses((currentAddresses) => {
      const filteredAddresses = currentAddresses.filter(
        (address) => address.id !== addressId,
      );

      if (
        targetAddress.isDefault &&
        filteredAddresses.length > 0 &&
        !filteredAddresses.some((address) => address.isDefault)
      ) {
        return filteredAddresses.map((address, index) => ({
          ...address,
          isDefault: index === 0,
        }));
      }

      return filteredAddresses;
    });
  };

  const handleSetDefaultAddress = (addressId: number) => {
    setAddresses((currentAddresses) =>
      currentAddresses.map((address) => ({
        ...address,
        isDefault: address.id === addressId,
      })),
    );
  };

  return (
    <main className="mypage-main">
      <div className="common-inner">
        <div className="mypage-content">
          <div className="mypage-section-header">
            <div>
              <h1 className="mypage-title">배송지 관리</h1>
              <p className="mypage-description">
                주문 시 사용할 배송지를 관리할 수 있습니다.
              </p>
            </div>

            <button
              type="button"
              className="address-add-button"
              onClick={handleOpenCreateForm}
            >
              배송지 추가
            </button>
          </div>

          {addresses.length > 0 ? (
            <div className="address-list">
              {addresses.map((address) => (
                <AddressCard
                  key={address.id}
                  address={address}
                  onEdit={handleOpenEditForm}
                  onDelete={handleDeleteAddress}
                  onSetDefault={handleSetDefaultAddress}
                />
              ))}
            </div>
          ) : (
            <div className="address-empty">
              <p className="address-empty-title">등록된 배송지가 없습니다.</p>
              <p className="address-empty-description">
                배송지를 추가하면 주문할 때 편리하게 선택할 수 있습니다.
              </p>

              <button
                type="button"
                className="address-empty-button"
                onClick={handleOpenCreateForm}
              >
                배송지 추가
              </button>
            </div>
          )}
        </div>
      </div>

      {isFormOpen && (
        <AddressForm
          address={editingAddress}
          onSubmit={handleSaveAddress}
          onClose={handleCloseForm}
        />
      )}
    </main>
  );
}
