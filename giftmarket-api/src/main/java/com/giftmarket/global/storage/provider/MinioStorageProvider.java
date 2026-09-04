package com.giftmarket.global.storage.provider;

import com.giftmarket.global.storage.config.MinioProperties;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "storage.provider",
        havingValue = "minio",
        matchIfMissing = true
)
public class MinioStorageProvider implements StorageProvider {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @Override
    public String createUploadUrl(
            String objectKey,
            int expirationSeconds
    ) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(minioProperties.bucket())
                            .object(objectKey)
                            .expiry(expirationSeconds)
                            .build()
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "파일 업로드 URL 생성에 실패했습니다.",
                    exception
            );
        }
    }

    @Override
    public String createReadUrl(
            String objectKey,
            int expirationSeconds
    ) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioProperties.bucket())
                            .object(objectKey)
                            .expiry(expirationSeconds)
                            .build()
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "파일 조회 URL 생성에 실패했습니다.",
                    exception
            );
        }
    }

    @Override
    public void deleteObject(String objectKey) {
        try {
            log.info(
                    "MinIO 객체 삭제 시작. objectKey={}",
                    objectKey
            );

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.bucket())
                            .object(objectKey)
                            .build()
            );

            log.info(
                    "MinIO 객체 삭제 요청 완료. objectKey={}",
                    objectKey
            );
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
}