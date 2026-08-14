"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";

import OrderProductList from "@/components/order/OrderProductList";
import OrderRecipientForm from "@/components/order/OrderRecipientForm";
import OrderSummary from "@/components/order/OrderSummary";
import { createAddress, getMyAddresses } from "@/lib/address-api";
import { createDirectOrder, createOrder } from "@/lib/order-api";
import { getProduct } from "@/lib/product-api";
import { useAuthStore } from "@/stores/auth-store";
import { useCartStore } from "@/stores/cart-store";
import type { Address } from "@/types/address";
import type { OrderProductItem } from "@/types/order";
import type { ProductDetail } from "@/types/product";

interface RecipientForm {
  name: string;
  phone: string;
  postalCode: string;
  address: string;
  addressDetail: string;
}

function parseCartItemIds(value: string | null): number[] {
  if (!value) {
    return [];
  }

  const ids = value
    .split(",")
    .map((item) => Number(item.trim()))
    .filter((id) => Number.isInteger(id) && id > 0);

  return Array.from(new Set(ids));
}

function parsePositiveInteger(value: string | null): number | null {
  if (!value) return null;

  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

export default function OrderPage() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const authInitialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const cartInitialized = useCartStore((state) => state.initialized);
  const items = useCartStore((state) => state.items);
  const loadCart = useCartStore((state) => state.loadCart);

  const [recipient, setRecipient] = useState<RecipientForm>({
    name: "",
    phone: "",
    postalCode: "",
    address: "",
    addressDetail: "",
  });

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [isAddressLoading, setIsAddressLoading] = useState(false);
  const [addressMode, setAddressMode] = useState<"saved" | "new">("new");
  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null);
  const [saveAddress, setSaveAddress] = useState(false);
  const [addressName, setAddressName] = useState("");
  const [setAsDefault, setSetAsDefault] = useState(false);
  const [directProduct, setDirectProduct] = useState<ProductDetail | null>(null);
  const [isDirectProductLoading, setIsDirectProductLoading] = useState(false);

  const [errorMessage, setErrorMessage] = useState("");

  const requestedCartItemIds = useMemo(
    () => parseCartItemIds(searchParams.get("cartItemIds")),
    [searchParams],
  );

  const directProductId = useMemo(
    () => parsePositiveInteger(searchParams.get("productId")),
    [searchParams],
  );
  const directVariantId = useMemo(
    () => parsePositiveInteger(searchParams.get("variantId")),
    [searchParams],
  );
  const directQuantity = useMemo(
    () => parsePositiveInteger(searchParams.get("quantity")),
    [searchParams],
  );
  const isDirectOrder = directProductId !== null;

  useEffect(() => {
    if (!authInitialized) {
      return;
    }

    if (!isAuthenticated || !user) {
      router.replace(`/login?redirect=${encodeURIComponent(`/order?${searchParams.toString()}`)}`);
      return;
    }

    if (!isDirectOrder) {
      void loadCart();
    }
  }, [
    authInitialized,
    isAuthenticated,
    user,
    loadCart,
    router,
    requestedCartItemIds,
    searchParams,
    isDirectOrder,
  ]);

  useEffect(() => {
    if (!authInitialized || !isAuthenticated || !user || !isDirectOrder) {
      return;
    }

    let cancelled = false;

    const loadDirectProduct = async () => {
      try {
        setIsDirectProductLoading(true);
        setErrorMessage("");
        const product = await getProduct(directProductId);
        if (!cancelled) setDirectProduct(product);
      } catch (error) {
        if (!cancelled) {
          setErrorMessage(
            error instanceof Error
              ? error.message
              : "바로구매 상품을 불러오지 못했습니다.",
          );
        }
      } finally {
        if (!cancelled) setIsDirectProductLoading(false);
      }
    };

    void loadDirectProduct();
    return () => {
      cancelled = true;
    };
  }, [authInitialized, isAuthenticated, user, isDirectOrder, directProductId]);

  useEffect(() => {
    if (!authInitialized || !isAuthenticated || !user) {
      return;
    }

    let cancelled = false;

    const loadAddresses = async () => {
      try {
        setIsAddressLoading(true);
        const loadedAddresses = await getMyAddresses();

        if (cancelled) return;

        setAddresses(loadedAddresses);

        if (loadedAddresses.length > 0) {
          const initialAddress =
            loadedAddresses.find((address) => address.isDefault) ??
            loadedAddresses[0];

          setAddressMode("saved");
          setSelectedAddressId(initialAddress.id);
          setRecipient({
            name: initialAddress.recipientName,
            phone: initialAddress.phoneNumber,
            postalCode: initialAddress.postalCode,
            address: initialAddress.address,
            addressDetail: initialAddress.detailAddress ?? "",
          });
        } else {
          setRecipient((current) => ({
            ...current,
            name: current.name || user.name,
          }));
        }
      } catch (error) {
        if (!cancelled) {
          setErrorMessage(
            error instanceof Error
              ? error.message
              : "배송지 목록을 불러오지 못했습니다.",
          );
        }
      } finally {
        if (!cancelled) setIsAddressLoading(false);
      }
    };

    void loadAddresses();

    return () => {
      cancelled = true;
    };
  }, [authInitialized, isAuthenticated, user]);

  const cartOrderItems = useMemo(
    () =>
      items.filter((item) => requestedCartItemIds.includes(item.cartItemId)),
    [items, requestedCartItemIds],
  );

  const directVariant = useMemo(
    () =>
      directProduct?.variants.find(
        (variant) => variant.id === directVariantId,
      ) ?? null,
    [directProduct, directVariantId],
  );

  const orderItems = useMemo<OrderProductItem[]>(() => {
    if (isDirectOrder) {
      if (!directProduct || !directQuantity) return [];

      const optionText = directVariant
        ? directProduct.optionGroups
            .map((group) => {
              const value = group.values.find((optionValue) =>
                directVariant.optionValueIds.includes(optionValue.id),
              );
              return value ? `${group.name}: ${value.value}` : null;
            })
            .filter((value): value is string => value !== null)
            .join(" / ")
        : null;

      return [
        {
          key: `direct-${directProduct.id}-${directVariantId ?? "none"}`,
          productName: directProduct.name,
          brandName: directProduct.brandName,
          storeName: directProduct.storeName,
          representativeImageKey: directProduct.representativeImageKey,
          optionText,
          quantity: directQuantity,
          price: directVariant?.price ?? directProduct.price,
          freeShipping: directProduct.freeShipping,
          shippingFee: directProduct.freeShipping
            ? 0
            : directProduct.shippingFee,
        },
      ];
    }

    return cartOrderItems.map((item) => ({
      key: `cart-${item.cartItemId}`,
      productName: item.productName,
      brandName: item.brandName,
      storeName: item.storeName,
      representativeImageKey: item.representativeImageKey,
      optionText:
        item.options.length > 0
          ? item.options
              .map(
                (option) =>
                  `${option.optionGroupName}: ${option.optionValue}`,
              )
              .join(" / ")
          : null,
      quantity: item.quantity,
      price: item.price,
      freeShipping: item.freeShipping,
      shippingFee: item.shippingFee,
    }));
  }, [isDirectOrder, directProduct, directQuantity, directVariant, directVariantId, cartOrderItems]);

  /*
   * URL로 요청한 CartItem 중 하나라도
   * 없어졌거나 현재 구매불가가 되었으면
   * 일부 상품만 조용히 주문하지 않고
   * 주문서 자체를 막습니다.
   */
  const hasInvalidOrderItems =
    cartInitialized &&
    (requestedCartItemIds.length === 0 ||
      cartOrderItems.length !== requestedCartItemIds.length ||
      cartOrderItems.some((item) => !item.purchasable));

  const hasInvalidDirectOrder =
    isDirectOrder &&
    !isDirectProductLoading &&
    (!directProduct ||
      !directQuantity ||
      directProduct.status !== "ON_SALE" ||
      (directProduct.hasOptions &&
        (!directVariantId || !directVariant || !directVariant.available)) ||
      (!directProduct.hasOptions && directVariantId !== null) ||
      directQuantity >
        (directProduct.hasOptions
          ? (directVariant?.stockQuantity ?? 0)
          : directProduct.stockQuantity));

  const productAmount = useMemo(
    () =>
      orderItems.reduce((total, item) => total + item.price * item.quantity, 0),
    [orderItems],
  );

  /*
   * Backend OrderService와 동일한 정책.
   * CartItem별 현재 배송비 합계를 사용합니다.
   */
  const shippingFee = useMemo(
    () =>
      orderItems.reduce(
        (total, item) => total + (item.freeShipping ? 0 : item.shippingFee),
        0,
      ),
    [orderItems],
  );

  const totalAmount = productAmount + shippingFee;

  const handleRecipientChange = (field: keyof RecipientForm, value: string) => {
    setRecipient((current) => ({
      ...current,
      [field]: value,
    }));
  };

  const handleSelectAddress = (addressId: number) => {
    const selectedAddress = addresses.find((address) => address.id === addressId);
    if (!selectedAddress) return;

    setAddressMode("saved");
    setSelectedAddressId(addressId);
    setSaveAddress(false);
    setRecipient({
      name: selectedAddress.recipientName,
      phone: selectedAddress.phoneNumber,
      postalCode: selectedAddress.postalCode,
      address: selectedAddress.address,
      addressDetail: selectedAddress.detailAddress ?? "",
    });
    setErrorMessage("");
  };

  const handleSelectNewAddress = () => {
    setAddressMode("new");
    setSelectedAddressId(null);
    setSaveAddress(false);
    setAddressName("");
    setSetAsDefault(false);
    setRecipient({
      name: user?.name ?? "",
      phone: "",
      postalCode: "",
      address: "",
      addressDetail: "",
    });
    setErrorMessage("");
  };

  const validateOrder = () => {
    if (!isAuthenticated || !user) {
      setErrorMessage("로그인이 필요합니다.");
      return false;
    }

    if (orderItems.length === 0) {
      setErrorMessage("주문할 상품을 확인할 수 없습니다.");
      return false;
    }

    if ((!isDirectOrder && hasInvalidOrderItems) || hasInvalidDirectOrder) {
      setErrorMessage(
        isDirectOrder
          ? "상품 상태, 옵션 또는 재고가 변경되었습니다. 상품 정보를 다시 확인해주세요."
          : "상품 상태 또는 재고가 변경되었습니다. 장바구니를 다시 확인해주세요.",
      );
      return false;
    }

    if (!recipient.name.trim()) {
      setErrorMessage("받는 분 이름을 입력해주세요.");
      return false;
    }

    if (!recipient.phone.trim()) {
      setErrorMessage("받는 분 휴대폰 번호를 입력해주세요.");
      return false;
    }

    if (!/^[0-9-]{9,20}$/.test(recipient.phone.trim())) {
      setErrorMessage("올바른 휴대폰 번호를 입력해주세요.");
      return false;
    }

    if (!recipient.postalCode.trim() || !recipient.address.trim()) {
      setErrorMessage("주소를 입력해주세요.");
      return false;
    }

    if (addressMode === "new" && saveAddress) {
      if (!addressName.trim()) {
        setErrorMessage("배송지명을 입력해주세요.");
        return false;
      }

      if (addressName.trim().length > 20) {
        setErrorMessage("배송지명은 20자 이하로 입력해주세요.");
        return false;
      }

      if (recipient.name.trim().length > 30) {
        setErrorMessage("받는 분 이름은 30자 이하로 입력해주세요.");
        return false;
      }

      if (!/^0\d{1,2}-\d{3,4}-\d{4}$/.test(recipient.phone.trim())) {
        setErrorMessage("올바른 연락처를 입력해주세요.");
        return false;
      }

      if (!/^\d{5}$/.test(recipient.postalCode.trim())) {
        setErrorMessage("올바른 주소를 입력해주세요.");
        return false;
      }

      if (addresses.length >= 10) {
        setErrorMessage("배송지는 최대 10개까지 등록할 수 있습니다.");
        return false;
      }
    }

    setErrorMessage("");

    return true;
  };

  const handleSubmit = async () => {
    if (isSubmitting || !validateOrder()) {
      return;
    }

    try {
      setIsSubmitting(true);
      setErrorMessage("");

      if (addressMode === "new" && saveAddress) {
        const savedAddress = await createAddress({
          name: addressName.trim(),
          recipientName: recipient.name.trim(),
          phoneNumber: recipient.phone.trim(),
          postalCode: recipient.postalCode.trim(),
          address: recipient.address.trim(),
          detailAddress: recipient.addressDetail.trim() || null,
          isDefault: setAsDefault,
        });

        setAddresses((current) => [
          savedAddress,
          ...current.map((address) =>
            savedAddress.isDefault ? { ...address, isDefault: false } : address,
          ),
        ]);
        setAddressMode("saved");
        setSelectedAddressId(savedAddress.id);
        setSaveAddress(false);
      }

      const delivery = {
        recipientName: recipient.name.trim(),
        recipientPhone: recipient.phone.trim(),
        postalCode: recipient.postalCode.trim(),
        address: recipient.address.trim(),
        addressDetail: recipient.addressDetail.trim() || null,
      };

      const createdOrder = isDirectOrder
        ? await createDirectOrder({
            productId: directProductId,
            variantId: directVariantId,
            quantity: directQuantity!,
            ...delivery,
          })
        : await createOrder({
            cartItemIds: requestedCartItemIds,
            ...delivery,
          });

      /*
       * Backend가 주문된 CartItem을 삭제했으므로
       * Header / Cart count도 즉시 최신화합니다.
       */
      if (!isDirectOrder) {
        await loadCart();
      }

      router.replace(`/my/orders/${createdOrder.orderId}`);
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "주문 처리 중 오류가 발생했습니다.",
      );

      /*
       * 주문 시점에 재고/상품상태가 바뀌었을 수 있으므로
       * 실패 후 Cart도 최신 상태로 다시 동기화합니다.
       */
      if (!isDirectOrder) {
        await loadCart();
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  if (
    !authInitialized ||
    (isAuthenticated &&
      (isDirectOrder ? isDirectProductLoading : !cartInitialized))
  ) {
    return (
      <div className="order-empty">
        <h1 className="order-empty-title">주문 정보를 불러오는 중입니다.</h1>
      </div>
    );
  }

  if (!isAuthenticated || !user) {
    return null;
  }

  if ((!isDirectOrder && hasInvalidOrderItems) || hasInvalidDirectOrder) {
    return (
      <div className="order-empty">
        <h1 className="order-empty-title">주문 상품을 다시 확인해주세요.</h1>

        <p className="order-empty-description">
          상품의 판매 상태, 옵션 또는 재고가 변경되었을 수 있습니다.
        </p>

        <button
          className="order-empty-button"
          type="button"
          onClick={() =>
            router.replace(isDirectOrder ? `/products/${directProductId}` : "/cart")
          }
        >
          {isDirectOrder ? "상품 상세로 돌아가기" : "장바구니로 돌아가기"}
        </button>
      </div>
    );
  }

  return (
    <div className="order-page">
      <div className="order-page-header">
        <h1 className="order-page-title">주문서</h1>
      </div>

      {errorMessage && <p className="order-error-message">{errorMessage}</p>}

      <div className="order-layout">
        <div className="order-content">
          <OrderProductList items={orderItems} />

          <OrderRecipientForm
            addresses={addresses}
            addressMode={addressMode}
            selectedAddressId={selectedAddressId}
            isAddressLoading={isAddressLoading}
            name={recipient.name}
            phone={recipient.phone}
            postalCode={recipient.postalCode}
            address={recipient.address}
            addressDetail={recipient.addressDetail}
            onChange={handleRecipientChange}
            onSelectAddress={handleSelectAddress}
            onSelectNewAddress={handleSelectNewAddress}
            saveAddress={saveAddress}
            onSaveAddressChange={setSaveAddress}
            addressName={addressName}
            onAddressNameChange={setAddressName}
            setAsDefault={setAsDefault}
            onSetAsDefaultChange={setSetAsDefault}
            canSaveAddress={addresses.length < 10}
          />
        </div>

        <OrderSummary
          productAmount={productAmount}
          shippingFee={shippingFee}
          totalAmount={totalAmount}
          isSubmitting={isSubmitting}
          onSubmit={() => void handleSubmit()}
        />
      </div>
    </div>
  );
}
