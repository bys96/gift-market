package com.giftmarket.global.storage.service;

import com.giftmarket.global.storage.config.MinioProperties;
import com.giftmarket.global.storage.dto.PresignedUrlRequest;
import com.giftmarket.global.storage.dto.PresignedUrlResponse;
import com.giftmarket.global.storage.type.StorageType;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private static final int PRESIGNED_URL_EXPIRATION_SECONDS = 300;
    private static final long MAX_IMAGE_FILE_SIZE = 5 * 1024 * 1024L;

    private static final Map<String, String> ALLOWED_IMAGE_CONTENT_TYPES =
            Map.of(
                    "image/jpeg", ".jpg",
                    "image/png", ".png",
                    "image/webp", ".webp",
                    "image/gif", ".gif"
            );

    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS =
            Set.of(
                    ".jpg",
                    ".jpeg",
                    ".png",
                    ".webp",
                    ".gif"
            );

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public PresignedUrlResponse createPresignedUrl(
            Long ownerId,
            PresignedUrlRequest request
    ) {
        validateImageFile(
                request.fileName(),
                request.contentType()
        );

        validateFileSize(request.fileSize());

        String objectKey = createObjectKey(
                ownerId,
                request.type(),
                request.fileName()
        );

        try {
            String uploadUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(minioProperties.bucket())
                            .object(objectKey)
                            .expiry(PRESIGNED_URL_EXPIRATION_SECONDS)
                            .build()
            );

            return new PresignedUrlResponse(
                    uploadUrl,
                    objectKey
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "파일 업로드 URL 생성에 실패했습니다.",
                    exception
            );
        }
    }

    public void deleteObject(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            log.warn("MinIO 삭제 생략: objectKey가 비어 있습니다.");
            return;
        }

        try {
            log.info("MinIO 객체 삭제 시작. objectKey={}", objectKey);

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.bucket())
                            .object(objectKey)
                            .build()
            );

            log.info("MinIO 객체 삭제 요청 완료. objectKey={}", objectKey);
        } catch (Exception exception) {
            log.error(
                    "MinIO 객체 삭제 실패. objectKey={}",
                    objectKey,
                    exception
            );

            throw new IllegalStateException(
                    "파일 삭제에 실패했습니다.",
                    exception
            );
        }
    }

    public String createReadUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("조회할 파일 정보가 필요합니다.");
        }
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioProperties.bucket())
                            .object(objectKey)
                            .expiry(PRESIGNED_URL_EXPIRATION_SECONDS)
                            .build()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("파일 조회 URL 생성에 실패했습니다.", exception);
        }
    }

    private String createObjectKey(
            Long ownerId,
            StorageType storageType,
            String originalFileName
    ) {
        String extension = extractExtension(originalFileName);
        String fileName = UUID.randomUUID() + extension;

        return switch (storageType) {
            case PROFILE -> {
                if (ownerId == null) {
                    throw new IllegalArgumentException("프로필 이미지 업로드 소유자 정보가 필요합니다.");
                }
                yield "profiles/" + ownerId + "/" + fileName;
            }

            case PRODUCT_REPRESENTATIVE -> {
                validateProductOwnerId(ownerId);

                yield "products/"
                        + ownerId
                        + "/representative/"
                        + fileName;
            }

            case PRODUCT_GALLERY -> {
                validateProductOwnerId(ownerId);

                yield "products/"
                        + ownerId
                        + "/gallery/"
                        + fileName;
            }

            case PRODUCT_CONTENT -> {
                validateProductOwnerId(ownerId);

                yield "products/"
                        + ownerId
                        + "/content/"
                        + fileName;
            }

            case RETURN_EVIDENCE -> {
                if (ownerId == null) {
                    throw new IllegalArgumentException("반품 이미지 업로드 소유자 정보가 필요합니다.");
                }
                yield "returns/" + ownerId + "/" + fileName;
            }

            case EXCHANGE_EVIDENCE -> {
                if (ownerId == null) {
                    throw new IllegalArgumentException("교환 이미지 업로드 소유자 정보가 필요합니다.");
                }
                yield "exchanges/" + ownerId + "/" + fileName;
            }

            case REVIEW -> {
                if (ownerId == null) {
                    throw new IllegalArgumentException("리뷰 이미지 업로드 소유자 정보가 필요합니다.");
                }
                yield "reviews/" + ownerId + "/" + fileName;
            }

            default -> storageType.getDirectory()
                    + "/"
                    + fileName;
        };
    }

    private void validateProductOwnerId(Long ownerId) {
        if (ownerId == null) {
            throw new IllegalArgumentException(
                    "상품 이미지 업로드 소유자 정보가 필요합니다."
            );
        }
    }

    private String extractExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');

        if (lastDotIndex < 0) {
            return "";
        }

        return fileName
                .substring(lastDotIndex)
                .toLowerCase();
    }

    private void validateImageFile(
            String fileName,
            String contentType
    ) {
        String normalizedContentType =
                contentType.trim().toLowerCase();

        String extension = extractExtension(fileName);

        if (!ALLOWED_IMAGE_CONTENT_TYPES.containsKey(
                normalizedContentType
        )) {
            throw new IllegalArgumentException(
                    "지원하지 않는 이미지 형식입니다."
            );
        }

        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "지원하지 않는 이미지 확장자입니다."
            );
        }

        String expectedExtension =
                ALLOWED_IMAGE_CONTENT_TYPES.get(normalizedContentType);

        boolean jpegExtension =
                normalizedContentType.equals("image/jpeg")
                        && (
                        extension.equals(".jpg")
                                || extension.equals(".jpeg")
                );

        if (!jpegExtension
                && !expectedExtension.equals(extension)) {
            throw new IllegalArgumentException(
                    "파일 확장자와 콘텐츠 타입이 일치하지 않습니다."
            );
        }
    }

    private void validateFileSize(Long fileSize) {
        if (fileSize == null || fileSize <= 0) {
            throw new IllegalArgumentException(
                    "올바른 파일 크기를 입력해주세요."
            );
        }

        if (fileSize > MAX_IMAGE_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "이미지 파일은 최대 5MB까지 업로드할 수 있습니다."
            );
        }
    }
}
