const storageBaseUrl = process.env.NEXT_PUBLIC_STORAGE_BASE_URL;

export function resolveImageUrl(
  imageUrl: string | null | undefined,
): string | null {
  if (!imageUrl) {
    return null;
  }

  // 구글·카카오처럼 이미 완성된 URL
  if (
    imageUrl.startsWith("http://") ||
    imageUrl.startsWith("https://") ||
    imageUrl.startsWith("blob:") ||
    imageUrl.startsWith("data:")
  ) {
    return imageUrl;
  }

  // MinIO objectKey
  if (!storageBaseUrl) {
    return null;
  }

  return `${storageBaseUrl.replace(/\/$/, "")}/${imageUrl.replace(/^\//, "")}`;
}
