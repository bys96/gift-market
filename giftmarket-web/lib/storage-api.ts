import { apiFetch } from "@/lib/api";
import type {
  ApiResponse,
  PresignedUrlRequest,
  PresignedUrlResponse,
} from "@/types/api";

export async function createPresignedUrl(
  request: PresignedUrlRequest,
): Promise<PresignedUrlResponse> {
  const response = await apiFetch<ApiResponse<PresignedUrlResponse>>(
    "/api/storage/presigned-url",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  if (!response.data) {
    throw new Error("파일 업로드 URL을 확인할 수 없습니다.");
  }

  return response.data;
}

export async function uploadFileToStorage(
  uploadUrl: string,
  file: File,
): Promise<void> {
  const response = await fetch(uploadUrl, {
    method: "PUT",
    headers: {
      "Content-Type": file.type,
    },
    body: file,
  });

  if (!response.ok) {
    throw new Error("파일 업로드에 실패했습니다.");
  }
}

export async function uploadImage(
  file: File,
  type: PresignedUrlRequest["type"],
): Promise<string> {
  const presignedUrl = await createPresignedUrl({
    type,
    fileName: file.name,
    contentType: file.type,
    fileSize: file.size,
  });

  await uploadFileToStorage(presignedUrl.uploadUrl, file);

  return presignedUrl.objectKey;
}

export async function uploadRepresentativeImage(file: File): Promise<string> {
  return uploadImage(file, "PRODUCT_REPRESENTATIVE");
}

export async function uploadGalleryImage(file: File): Promise<string> {
  return uploadImage(file, "PRODUCT_GALLERY");
}

export async function uploadContentImage(file: File): Promise<string> {
  return uploadImage(file, "PRODUCT_CONTENT");
}
