"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import AddressCard from "@/components/address/AddressCard";
import AddressForm from "@/components/address/AddressForm";
import {
  createAddress,
  deleteAddress,
  getMyAddresses,
  setDefaultAddress,
  updateAddress,
} from "@/lib/address-api";
import { useAuthStore } from "@/stores/auth-store";
import type { Address, AddressFormData, AddressRequest } from "@/types/address";

export default function MyAddressesPage() {
  const router = useRouter();

  const initialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const [addresses, setAddresses] = useState<Address[]>([]);
  const [editingAddress, setEditingAddress] = useState<Address | null>(null);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [processingAddressId, setProcessingAddressId] = useState<number | null>(
    null,
  );
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    if (!initialized) {
      return;
    }

    if (!isAuthenticated || !user) {
      router.replace("/login");
    }
  }, [initialized, isAuthenticated, user, router]);

  useEffect(() => {
    if (!initialized || !isAuthenticated || !user) {
      return;
    }

    let cancelled = false;

    const loadAddresses = async () => {
      try {
        setIsLoading(true);
        setErrorMessage("");

        const response = await getMyAddresses();

        if (cancelled) {
          return;
        }

        setAddresses(response);
      } catch (error) {
        if (cancelled) {
          return;
        }

        setErrorMessage(
          error instanceof Error
            ? error.message
            : "배송지 목록을 불러오지 못했습니다.",
        );
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    };

    void loadAddresses();

    return () => {
      cancelled = true;
    };
  }, [initialized, isAuthenticated, user]);

  const handleOpenCreateForm = () => {
    setEditingAddress(null);
    setErrorMessage("");
    setIsFormOpen(true);
  };

  const handleOpenEditForm = (address: Address) => {
    setEditingAddress(address);
    setErrorMessage("");
    setIsFormOpen(true);
  };

  const handleCloseForm = () => {
    if (isSaving) {
      return;
    }

    setEditingAddress(null);
    setIsFormOpen(false);
  };

  const handleSaveAddress = async (formData: AddressFormData) => {
    if (isSaving) {
      return;
    }

    const request: AddressRequest = {
      name: formData.name.trim(),
      recipientName: formData.recipientName.trim(),
      phoneNumber: formData.phoneNumber.trim(),
      postalCode: formData.postalCode.trim(),
      address: formData.address.trim(),
      detailAddress: formData.detailAddress.trim() || null,
      isDefault: formData.isDefault,
    };

    try {
      setIsSaving(true);
      setErrorMessage("");

      if (editingAddress) {
        await updateAddress(editingAddress.id, request);
      } else {
        await createAddress(request);
      }

      const refreshedAddresses = await getMyAddresses();

      setAddresses(refreshedAddresses);
      setEditingAddress(null);
      setIsFormOpen(false);
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "배송지를 저장하지 못했습니다.",
      );
    } finally {
      setIsSaving(false);
    }
  };

  const handleDeleteAddress = async (addressId: number) => {
    if (processingAddressId !== null) {
      return;
    }

    const targetAddress = addresses.find((address) => address.id === addressId);

    if (!targetAddress) {
      return;
    }

    const confirmed = window.confirm(
      `"${targetAddress.name}" 배송지를 삭제하시겠습니까?`,
    );

    if (!confirmed) {
      return;
    }

    try {
      setProcessingAddressId(addressId);
      setErrorMessage("");

      await deleteAddress(addressId);

      const refreshedAddresses = await getMyAddresses();

      setAddresses(refreshedAddresses);
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "배송지를 삭제하지 못했습니다.",
      );
    } finally {
      setProcessingAddressId(null);
    }
  };

  const handleSetDefaultAddress = async (addressId: number) => {
    if (processingAddressId !== null) {
      return;
    }

    try {
      setProcessingAddressId(addressId);
      setErrorMessage("");

      await setDefaultAddress(addressId);

      const refreshedAddresses = await getMyAddresses();

      setAddresses(refreshedAddresses);
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "기본 배송지를 변경하지 못했습니다.",
      );
    } finally {
      setProcessingAddressId(null);
    }
  };

  if (!initialized) {
    return null;
  }

  if (!isAuthenticated || !user) {
    return null;
  }

  return (
    <main className="mypage-main address-page">
      <div className="common-inner">
        <div className="address-page-content">
          <header className="address-page-header">
            <h1 className="address-page-title">배송지 관리</h1>
            <p className="address-page-subtitle">
              주문에 사용할 배송지를 등록하고 관리할 수 있습니다.
            </p>
          </header>

          <section className="address-management">
            <div className="address-management-header">
              <div className="address-management-count">
                <strong>등록된 배송지</strong>

                {!isLoading && (
                  <span>
                    {addresses.length}
                    <em>/10</em>
                  </span>
                )}
              </div>

              <button
                type="button"
                className="address-add-button"
                onClick={handleOpenCreateForm}
                disabled={isLoading || addresses.length >= 10}
              >
                <span aria-hidden="true">+</span>
                배송지 추가
              </button>
            </div>

            {isFormOpen && (
              <AddressForm
                address={editingAddress}
                onSubmit={handleSaveAddress}
                onCancel={handleCloseForm}
                isSubmitting={isSaving}
                submitErrorMessage={errorMessage}
              />
            )}

            {errorMessage && !isFormOpen && (
              <p className="address-page-error" role="alert">
                {errorMessage}
              </p>
            )}

            {isLoading ? (
              <div className="address-empty address-loading">
                <p className="address-empty-title">
                  배송지를 불러오고 있습니다.
                </p>
              </div>
            ) : addresses.length > 0 ? (
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
                <div className="address-empty-icon" aria-hidden="true">
                  +
                </div>

                <p className="address-empty-title">등록된 배송지가 없습니다.</p>

                <p className="address-empty-description">
                  자주 사용하는 배송지를 미리 등록해보세요.
                </p>

                <button
                  type="button"
                  className="address-empty-button"
                  onClick={handleOpenCreateForm}
                >
                  배송지 등록
                </button>
              </div>
            )}
          </section>
        </div>
      </div>

    </main>
  );
}
