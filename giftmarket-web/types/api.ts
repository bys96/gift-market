export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

export type StorageType =
  | "PROFILE"
  | "PRODUCT_REPRESENTATIVE"
  | "PRODUCT_GALLERY"
  | "PRODUCT_CONTENT"
  | "PRODUCT_CONTENT_VIDEO"
  | "RETURN_EVIDENCE"
  | "EXCHANGE_EVIDENCE"
  | "REVIEW"
  | "STORE_LOGO"
  | "STORE_BANNER"
  | "BANNER";

export interface PresignedUrlRequest {
  type: StorageType;
  fileName: string;
  contentType: string;
  fileSize: number;
}

export interface PresignedUrlResponse {
  uploadUrl: string;
  objectKey: string;
}
