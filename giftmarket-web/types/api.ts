export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

export interface PresignedUrlRequest {
  type: "PROFILE";
  fileName: string;
  contentType: string;
}

export interface PresignedUrlResponse {
  uploadUrl: string;
  objectKey: string;
}
