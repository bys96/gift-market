"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";

import OrderProductList from "@/components/order/OrderProductList";
import OrderRecipientForm from "@/components/order/OrderRecipientForm";
import OrderSummary from "@/components/order/OrderSummary";
import { createOrder } from "@/lib/order-api";
import { useAuthStore } from "@/stores/auth-store";
import { useCartStore } from "@/stores/cart-store";

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

  const [errorMessage, setErrorMessage] = useState("");

  const requestedCartItemIds = useMemo(
    () => parseCartItemIds(searchParams.get("cartItemIds")),
    [searchParams],
  );

  useEffect(() => {
    if (!authInitialized) {
      return;
    }

    if (!isAuthenticated || !user) {
      router.replace(
        `/login?redirect=${encodeURIComponent(
          `/order?cartItemIds=${requestedCartItemIds.join(",")}`,
        )}`,
      );
      return;
    }

    void loadCart();
  }, [
    authInitialized,
    isAuthenticated,
    user,
    loadCart,
    router,
    requestedCartItemIds,
  ]);

  useEffect(() => {
    if (!user) {
      return;
    }

    setRecipient((current) => ({
      ...current,
      name: current.name || user.name,
    }));
  }, [user]);

  const orderItems = useMemo(
    () =>
      items.filter((item) => requestedCartItemIds.includes(item.cartItemId)),
    [items, requestedCartItemIds],
  );

  /*
   * URL로 요청한 CartItem 중 하나라도
   * 없어졌거나 현재 구매불가가 되었으면
   * 일부 상품만 조용히 주문하지 않고
   * 주문서 자체를 막습니다.
   */
  const hasInvalidOrderItems =
    cartInitialized &&
    (requestedCartItemIds.length === 0 ||
      orderItems.length !== requestedCartItemIds.length ||
      orderItems.some((item) => !item.purchasable));

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

  const validateOrder = () => {
    if (!isAuthenticated || !user) {
      setErrorMessage("로그인이 필요합니다.");
      return false;
    }

    if (requestedCartItemIds.length === 0 || orderItems.length === 0) {
      setErrorMessage("주문할 상품을 확인할 수 없습니다.");
      return false;
    }

    if (hasInvalidOrderItems) {
      setErrorMessage(
        "상품 상태 또는 재고가 변경되었습니다. 장바구니를 다시 확인해주세요.",
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

      const createdOrder = await createOrder({
        cartItemIds: requestedCartItemIds,
        recipientName: recipient.name.trim(),
        recipientPhone: recipient.phone.trim(),
        postalCode: recipient.postalCode.trim(),
        address: recipient.address.trim(),
        addressDetail: recipient.addressDetail.trim() || null,
      });

      /*
       * Backend가 주문된 CartItem을 삭제했으므로
       * Header / Cart count도 즉시 최신화합니다.
       */
      await loadCart();

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
      await loadCart();
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!authInitialized || (isAuthenticated && !cartInitialized)) {
    return (
      <div className="order-empty">
        <h1 className="order-empty-title">주문 정보를 불러오는 중입니다.</h1>
      </div>
    );
  }

  if (!isAuthenticated || !user) {
    return null;
  }

  if (hasInvalidOrderItems) {
    return (
      <div className="order-empty">
        <h1 className="order-empty-title">주문 상품을 다시 확인해주세요.</h1>

        <p className="order-empty-description">
          상품의 판매 상태 또는 재고가 변경되었을 수 있습니다.
        </p>

        <button
          className="order-empty-button"
          type="button"
          onClick={() => router.replace("/cart")}
        >
          장바구니로 돌아가기
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
            name={recipient.name}
            phone={recipient.phone}
            postalCode={recipient.postalCode}
            address={recipient.address}
            addressDetail={recipient.addressDetail}
            onChange={handleRecipientChange}
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
