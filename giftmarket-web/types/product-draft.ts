export interface ProductDraftOptionValue {
  clientId: string;
  id: number | null;
  value: string;
}

export interface ProductDraftOptionGroup {
  clientId: string;
  id: number | null;
  name: string;
  values: ProductDraftOptionValue[];
}

export interface ProductDraftVariant {
  clientId: string;
  id: number | null;
  skuCode: string;
  optionValueClientIds: string[];
  additionalPrice: string;
  stockQuantity: string;
  active: boolean;
}

export interface ProductDraftOptionState {
  enabled: boolean;
  optionGroups: ProductDraftOptionGroup[];
  variants: ProductDraftVariant[];
}

export interface ProductDraftData {
  categoryId: string;

  name: string;
  brandName: string;
  summary: string;
  description: string;

  price: string;
  stockQuantity: string;

  representativeImageKey: string | null;
  galleryImageKeys: string[];

  freeShipping: boolean;
  shippingFee: string;
  shippingPreparationDays: string;
  returnShippingFee: string;
  exchangeShippingFee: string;

  options: ProductDraftOptionState;
}

export interface ProductDraft {
  id: number;
  productId: number | null;

  /**
   * 백엔드에서는 JSON 문자열로 저장/응답
   */
  draftData: string;

  createdAt: string;
  updatedAt: string;
}

export interface ProductDraftCreateRequest {
  productId: number | null;
  draftData: string;
}

export interface ProductDraftUpdateRequest {
  draftData: string;
}
