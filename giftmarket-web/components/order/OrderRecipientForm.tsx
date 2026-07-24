"use client";

interface OrderRecipientFormProps {
  name: string;
  phone: string;
  address: string;
  detailAddress: string;
  deliveryMessage: string;
  onChange: (
    field: "name" | "phone" | "address" | "detailAddress" | "deliveryMessage",
    value: string,
  ) => void;
}

const DELIVERY_MESSAGES = [
  "",
  "문 앞에 놓아주세요.",
  "배송 전에 연락해주세요.",
  "경비실에 맡겨주세요.",
  "직접 입력",
];

export default function OrderRecipientForm({
  name,
  phone,
  address,
  detailAddress,
  deliveryMessage,
  onChange,
}: OrderRecipientFormProps) {
  const isCustomMessage =
    deliveryMessage !== "" &&
    !DELIVERY_MESSAGES.slice(1, 4).includes(deliveryMessage);

  const selectedMessage = isCustomMessage ? "직접 입력" : deliveryMessage;

  const handleDeliveryMessageChange = (value: string) => {
    if (value === "직접 입력") {
      onChange("deliveryMessage", " ");
      return;
    }

    onChange("deliveryMessage", value);
  };

  return (
    <section className="order-section">
      <div className="order-section-header">
        <h2 className="order-section-title">받는 사람 정보</h2>
      </div>

      <div className="order-form">
        <div className="order-form-group">
          <label className="order-form-label" htmlFor="recipient-name">
            이름
          </label>

          <input
            id="recipient-name"
            className="order-form-input"
            type="text"
            value={name}
            onChange={(event) => onChange("name", event.target.value)}
            placeholder="받는 사람 이름"
            autoComplete="shipping name"
          />
        </div>

        <div className="order-form-group">
          <label className="order-form-label" htmlFor="recipient-phone">
            휴대폰 번호
          </label>

          <input
            id="recipient-phone"
            className="order-form-input"
            type="tel"
            value={phone}
            onChange={(event) => onChange("phone", event.target.value)}
            placeholder="010-0000-0000"
            autoComplete="shipping tel"
          />
        </div>

        <div className="order-form-group">
          <label className="order-form-label" htmlFor="recipient-address">
            주소
          </label>

          <div className="order-address-row">
            <input
              id="recipient-address"
              className="order-form-input"
              type="text"
              value={address}
              onChange={(event) => onChange("address", event.target.value)}
              placeholder="주소를 입력해주세요."
              autoComplete="shipping street-address"
            />

            <button
              className="order-address-search-button"
              type="button"
              onClick={() => alert("주소 검색 API는 추후 연동합니다.")}
            >
              주소 검색
            </button>
          </div>
        </div>

        <div className="order-form-group">
          <label
            className="order-form-label"
            htmlFor="recipient-detail-address"
          >
            상세 주소
          </label>

          <input
            id="recipient-detail-address"
            className="order-form-input"
            type="text"
            value={detailAddress}
            onChange={(event) => onChange("detailAddress", event.target.value)}
            placeholder="상세 주소를 입력해주세요."
            autoComplete="shipping address-line2"
          />
        </div>

        <div className="order-form-group">
          <label className="order-form-label" htmlFor="delivery-message">
            배송 메시지
          </label>

          <select
            id="delivery-message"
            className="order-form-select"
            value={selectedMessage}
            onChange={(event) =>
              handleDeliveryMessageChange(event.target.value)
            }
          >
            <option value="">배송 메시지를 선택해주세요.</option>
            <option value="문 앞에 놓아주세요.">문 앞에 놓아주세요.</option>
            <option value="배송 전에 연락해주세요.">
              배송 전에 연락해주세요.
            </option>
            <option value="경비실에 맡겨주세요.">경비실에 맡겨주세요.</option>
            <option value="직접 입력">직접 입력</option>
          </select>

          {selectedMessage === "직접 입력" && (
            <textarea
              className="order-form-textarea"
              value={deliveryMessage.trimStart()}
              onChange={(event) =>
                onChange("deliveryMessage", event.target.value)
              }
              placeholder="배송 메시지를 입력해주세요."
              maxLength={100}
            />
          )}
        </div>
      </div>
    </section>
  );
}
