const storageBaseUrl = process.env.NEXT_PUBLIC_STORAGE_BASE_URL?.trim();
let hasWarnedStorageConfiguration = false;

function warnStorageConfigurationOnce(message: string) {
  if (
    process.env.NODE_ENV === "production" ||
    hasWarnedStorageConfiguration
  ) {
    return;
  }

  hasWarnedStorageConfiguration = true;
  console.warn(message);
}

function getStorageBaseUrl(): string | null {
  if (!storageBaseUrl) {
    warnStorageConfigurationOnce(
      "[Gift Market] 이미지 object key가 있지만 NEXT_PUBLIC_STORAGE_BASE_URL이 설정되지 않았습니다.",
    );
    return null;
  }

  try {
    const parsedUrl = new URL(storageBaseUrl);

    if (parsedUrl.protocol !== "http:" && parsedUrl.protocol !== "https:") {
      throw new Error("unsupported protocol");
    }
  } catch {
    warnStorageConfigurationOnce(
      "[Gift Market] 이미지 object key가 있지만 NEXT_PUBLIC_STORAGE_BASE_URL이 올바른 http/https URL이 아닙니다.",
    );
    return null;
  }

  return storageBaseUrl.replace(/\/+$/, "");
}

export function resolveImageUrl(
  imageUrl: string | null | undefined,
): string | null {
  const normalizedImageUrl = imageUrl?.trim();

  if (!normalizedImageUrl) {
    return null;
  }

  // 구글·카카오처럼 이미 완성된 URL
  if (
    normalizedImageUrl.startsWith("http://") ||
    normalizedImageUrl.startsWith("https://") ||
    normalizedImageUrl.startsWith("blob:") ||
    normalizedImageUrl.startsWith("data:")
  ) {
    return normalizedImageUrl;
  }

  // MinIO objectKey
  const normalizedStorageBaseUrl = getStorageBaseUrl();

  if (!normalizedStorageBaseUrl) {
    return null;
  }

  return `${normalizedStorageBaseUrl}/${normalizedImageUrl.replace(/^\/+/, "")}`;
}
