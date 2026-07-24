"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import OrderCustomerForm from "@/components/order/OrderCustomerForm";
import OrderRecipientForm from "@/components/order/OrderRecipientForm";
import OrderProductList from "@/components/order/OrderProductList";
import OrderSummary from "@/components/order/OrderSummary";
import { useAuthStore } from "@/stores/auth-store";
import { useCartStore } from "@/stores/cart-store";

const DELIVERY_FEE = 3000;

interface CustomerForm {
  name: string;
  email: string;
  phone: string;
}

interface RecipientForm {
  name: string;
  phone: string;
  address: string;
  detailAddress: string;
  deliveryMessage: string;
}

export default function OrderPage() {
  const router = useRouter();

  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const items = useCartStore((state) => state.items);
  const clearCart = useCartStore((state) => state.clearCart);

  const [customer, setCustomer] = useState<CustomerForm>({
    name: "",
    email: "",
    phone: "",
  });

  const [recipient, setRecipient] = useState<RecipientForm>({
    name: "",
    phone: "",
    address: "",
    detailAddress: "",
    deliveryMessage: "",
  });

  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!user) {
      return;
    }

    setCustomer((current) => ({
      ...current,
      name: current.name || user.name,
      email: current.email || user.email,
    }));
  }, [user]);

  const productAmount = useMemo(
    () => items.reduce((total, item) => total + item.price * item.quantity, 0),
    [items],
  );

  const deliveryFee = useMemo(() => {
    if (items.length === 0) {
      return 0;
    }

    const hasPaidShippingItem = items.some((item) => !item.isFreeShipping);

    return hasPaidShippingItem ? DELIVERY_FEE : 0;
  }, [items]);

  const totalAmount = productAmount + deliveryFee;

  const handleCustomerChange = (field: keyof CustomerForm, value: string) => {
    setCustomer((current) => ({
      ...current,
      [field]: value,
    }));
  };

  const handleRecipientChange = (field: keyof RecipientForm, value: string) => {
    setRecipient((current) => ({
      ...current,
      [field]: value,
    }));
  };

  const validateOrder = () => {
    if (!isAuthenticated) {
      alert("로그인이 필요합니다.");
      return false;
    }

    if (items.length === 0) {
      alert("장바구니에 상품이 없습니다.");
      return false;
    }

    if (!customer.name.trim()) {
      alert("주문자 이름을 입력해주세요.");
      return false;
    }

    if (!customer.email.trim()) {
      alert("주문자 이메일을 입력해주세요.");
      return false;
    }

    if (!customer.phone.trim()) {
      alert("주문자 휴대폰 번호를 입력해주세요.");
      return false;
    }

    if (!recipient.name.trim()) {
      alert("받는 사람 이름을 입력해주세요.");
      return false;
    }

    if (!recipient.phone.trim()) {
      alert("받는 사람 휴대폰 번호를 입력해주세요.");
      return false;
    }

    if (!recipient.address.trim()) {
      alert("배송 주소를 입력해주세요.");
      return false;
    }

    if (!recipient.detailAddress.trim()) {
      alert("상세 주소를 입력해주세요.");
      return false;
    }

    return true;
  };

  const handleSubmit = async () => {
    if (!validateOrder()) {
      return;
    }

    try {
      setIsSubmitting(true);

      const orderRequest = {
        customer,
        recipient: {
          ...recipient,
          deliveryMessage: recipient.deliveryMessage.trim(),
        },
        items: items.map((item) => ({
          productId: item.productId,
          quantity: item.quantity,
        })),
      };

      console.log("orderRequest", orderRequest);

      // TODO: 주문 API 구현 후 교체
      // await fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/orders`, {
      //   method: "POST",
      //   headers: {
      //     "Content-Type": "application/json",
      //     Authorization: `Bearer ${accessToken}`,
      //   },
      //   body: JSON.stringify(orderRequest),
      // });

      clearCart();
      router.push("/order/complete");
    } catch (error) {
      console.error(error);
      alert("주문 처리 중 오류가 발생했습니다.");
    } finally {
      setIsSubmitting(false);
    }
  };

  if (items.length === 0) {
    return (
      <div className="order-empty">
        <h1 className="order-empty-title">주문할 상품이 없습니다</h1>

        <p className="order-empty-description">
          장바구니에 상품을 담은 후 주문해주세요.
        </p>

        <button
          className="order-empty-button"
          type="button"
          onClick={() => router.push("/products")}
        >
          상품 보러 가기
        </button>
      </div>
    );
  }

  return (
    <div className="order-page">
      <div className="order-page-header">
        <h1 className="order-page-title">주문 / 결제</h1>
      </div>

      <div className="order-layout">
        <div className="order-content">
          <OrderProductList items={items} />

          <OrderCustomerForm
            name={customer.name}
            email={customer.email}
            phone={customer.phone}
            onChange={handleCustomerChange}
          />

          <OrderRecipientForm
            name={recipient.name}
            phone={recipient.phone}
            address={recipient.address}
            detailAddress={recipient.detailAddress}
            deliveryMessage={recipient.deliveryMessage}
            onChange={handleRecipientChange}
          />
        </div>

        <OrderSummary
          productAmount={productAmount}
          deliveryFee={deliveryFee}
          totalAmount={totalAmount}
          isSubmitting={isSubmitting}
          onSubmit={handleSubmit}
        />
      </div>
    </div>
  );
}
