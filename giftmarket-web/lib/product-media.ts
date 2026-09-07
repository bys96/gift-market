import { resolveImageUrl } from "@/utils/image-url";

export const MAX_PRODUCT_IMAGE_SIZE = 20 * 1024 * 1024;
export const MAX_PRODUCT_VIDEO_SIZE = 50 * 1024 * 1024;
export const MAX_PRODUCT_VIDEO_COUNT = 3;

const IMAGE_EXTENSIONS: Record<string, string[]> = {
  "image/jpeg": ["jpg", "jpeg"],
  "image/png": ["png"],
  "image/webp": ["webp"],
  "image/gif": ["gif"],
};

type UploadFile = Pick<File, "name" | "type" | "size">;

export function validateProductImage(file: UploadFile): string | null {
  const extension = file.name.split(".").pop()?.toLowerCase() ?? "";
  if (!IMAGE_EXTENSIONS[file.type]?.includes(extension)) {
    return "JPG, PNG, WEBP, GIF 이미지와 일치하는 확장자만 업로드할 수 있습니다.";
  }
  if (file.size <= 0) return "비어 있는 파일은 업로드할 수 없습니다.";
  if (file.size > MAX_PRODUCT_IMAGE_SIZE) return "이미지 파일은 최대 20MB까지 업로드할 수 있습니다.";
  return null;
}

export function validateProductVideo(file: UploadFile, currentCount: number): string | null {
  if (currentCount >= MAX_PRODUCT_VIDEO_COUNT) return "상세 설명에는 동영상을 최대 3개까지 등록할 수 있습니다.";
  if (file.type !== "video/mp4" || !/\.mp4$/i.test(file.name)) return "동영상은 MP4 형식만 업로드할 수 있습니다.";
  if (file.size <= 0) return "비어 있는 파일은 업로드할 수 없습니다.";
  if (file.size > MAX_PRODUCT_VIDEO_SIZE) return "동영상 파일은 최대 50MB까지 업로드할 수 있습니다.";
  return null;
}

export function isProductMediaKey(key: string, tag: "img" | "video"): boolean {
  return tag === "video"
    ? /^products\/[1-9][0-9]*\/content\/video\/[a-fA-F0-9-]{36}\.mp4$/.test(key)
    : /^products\/[1-9][0-9]*\/content\/[a-fA-F0-9-]{36}\.(jpg|jpeg|png|webp|gif)$/.test(key);
}

export function resolveProductMediaSource(key: string | null, src: string | null, tag: "img" | "video"): string | null {
  if (key) return isProductMediaKey(key, tag) ? resolveImageUrl(key) : null;
  // 기존 절대 URL은 호환하되 임의 프로토콜은 허용하지 않는다.
  return src && /^https?:\/\//i.test(src) ? src : null;
}

// Backend에서 sanitize된 HTML에만 적용한다. 전체 HTML을 URL 문자열 치환하지 않는다.
export function resolveProductDescriptionMedia(html: string): string {
  if (!html.includes("data-storage-key") || typeof DOMParser === "undefined") return html;
  const document = new DOMParser().parseFromString(html, "text/html");
  document.querySelectorAll("img[data-storage-key], video[data-storage-key]").forEach((media) => {
    const tag = media.tagName.toLowerCase() as "img" | "video";
    const src = resolveProductMediaSource(media.getAttribute("data-storage-key"), null, tag);
    media.removeAttribute("src");
    if (src) media.setAttribute("src", src);
  });
  return document.body.innerHTML;
}
