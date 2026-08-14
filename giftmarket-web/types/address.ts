export interface Address {
  id: number;
  name: string;
  recipientName: string;
  phoneNumber: string;
  postalCode: string;
  address: string;
  detailAddress: string | null;
  isDefault: boolean;
}

export interface AddressRequest {
  name: string;
  recipientName: string;
  phoneNumber: string;
  postalCode: string;
  address: string;
  detailAddress: string | null;
  isDefault: boolean;
}

export interface AddressFormData {
  name: string;
  recipientName: string;
  phoneNumber: string;
  postalCode: string;
  address: string;
  detailAddress: string;
  isDefault: boolean;
}
