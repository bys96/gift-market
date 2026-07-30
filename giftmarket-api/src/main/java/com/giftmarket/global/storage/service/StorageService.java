package com.giftmarket.global.storage.service;

import com.giftmarket.global.storage.config.MinioProperties;
import com.giftmarket.global.storage.dto.PresignedUrlRequest;
import com.giftmarket.global.storage.dto.PresignedUrlResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StorageService {

    private static final int PRESIGNED_URL_EXPIRATION_SECONDS = 300;

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public PresignedUrlResponse createPresignedUrl(
            PresignedUrlRequest request
    ) {
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
}