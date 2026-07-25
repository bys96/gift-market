import type { Address } from "@/types/address";

interface AddressCardProps {
  address: Address;
  onEdit: (address: Address) => void;
  onDelete: (addressId: number) => void;
  onSetDefault: (addressId: number) => void;
}

export default function AddressCard({
  address,
  onEdit,
  onDelete,
  onSetDefault,
}: AddressCardProps) {
  return (
    <article className="address-card">
      <div className="address-card-header">
        <div className="address-card-title-area">
          <h2 className="address-card-title">{address.name}</h2>

          {address.isDefault && (
            <span className="address-default-badge">기본 배송지</span>
          )}
        </div>

        <div className="address-card-actions">
          <button
            type="button"
            className="address-text-button"
            onClick={() => onEdit(address)}
          >
            수정
          </button>

          <span className="address-action-divider" aria-hidden="true">
            |
          </span>

          <button
            type="button"
            className="address-text-button address-delete-button"
            onClick={() => onDelete(address.id)}
          >
            삭제
          </button>
        </div>
      </div>

      <div className="address-card-body">
        <p className="address-recipient">
          <strong>{address.recipientName}</strong>
          <span>{address.phoneNumber}</span>
        </p>

        <p className="address-postal-code">[{address.postalCode}]</p>

        <p className="address-location">
          {address.address} {address.detailAddress}
        </p>
      </div>

      {!address.isDefault && (
        <div className="address-card-footer">
          <button
            type="button"
            className="address-default-button"
            onClick={() => onSetDefault(address.id)}
          >
            기본 배송지로 설정
          </button>
        </div>
      )}
    </article>
  );
}
