export interface Address {
  id: number;
  name: string;
  recipientName: string;
  phoneNumber: string;
  postalCode: string;
  address: string;
  detailAddress: string;
  isDefault: boolean;
}

export type AddressFormData = Omit<Address, "id">;
