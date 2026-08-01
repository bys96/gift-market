package com.giftmarket.global.storage.service;

import java.util.Map;
import java.util.Set;

import com.giftmarket.global.storage.config.MinioProperties;
import com.giftmarket.global.storage.dto.PresignedUrlRequest;
import com.giftmarket.global.storage.dto.PresignedUrlResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private static final int PRESIGNED_URL_EXPIRATION_SECONDS = 300;

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
            PresignedUrlRequest request
    ) {
        validateImageFile(
                request.fileName(),
                request.contentType()
        );

        String objectKey = createObjectKey(
                request.type().getDirectory(),
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
        } catch (Exception e) {
            throw new IllegalStateException(
                    "파일 업로드 URL 생성에 실패했습니다.",
                    e
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

    private String createObjectKey(
            String directory,
            String originalFileName
    ) {
        String extension = extractExtension(originalFileName);

        return directory
                + "/"
                + UUID.randomUUID()
                + extension;
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
}