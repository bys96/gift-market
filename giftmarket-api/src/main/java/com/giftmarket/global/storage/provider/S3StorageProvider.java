package com.giftmarket.global.storage.provider;

import com.giftmarket.global.storage.config.S3Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "storage.provider",
        havingValue = "s3"
)
public class S3StorageProvider implements StorageProvider {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    @Override
    public String createUploadUrl(
            String objectKey,
            int expirationSeconds,
            long contentLength,
            String contentType
    ) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3Properties.bucket())
                    .key(objectKey)
                    .contentLength(contentLength)
                    .contentType(contentType)
                    .build();

            PutObjectPresignRequest presignRequest =
                    PutObjectPresignRequest.builder()
                            .signatureDuration(
                                    Duration.ofSeconds(expirationSeconds)
                            )
                            .putObjectRequest(putObjectRequest)
                            .build();

            var signedRequest = s3Presigner.presignPutObject(presignRequest);
            // SDK 변경 시 크기/MIME 제약 없는 URL을 발급하지 않는다.
            if (!signedRequest.signedHeaders().containsKey("content-length")
                    || !signedRequest.signedHeaders().containsKey("content-type")) {
                throw new IllegalStateException("업로드 크기와 Content-Type 서명이 필요합니다.");
            }
            return signedRequest.url().toString();
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
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(s3Properties.bucket())
                    .key(objectKey)
                    .build();

            GetObjectPresignRequest presignRequest =
                    GetObjectPresignRequest.builder()
                            .signatureDuration(
                                    Duration.ofSeconds(expirationSeconds)
                            )
                            .getObjectRequest(getObjectRequest)
                            .build();

            return s3Presigner
                    .presignGetObject(presignRequest)
                    .url()
                    .toString();
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
                    "S3 객체 삭제 시작. objectKey={}",
                    objectKey
            );

            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(s3Properties.bucket())
                    .key(objectKey)
                    .build();

            s3Client.deleteObject(request);

            log.info(
                    "S3 객체 삭제 완료. objectKey={}",
                    objectKey
            );
        } catch (Exception exception) {
            log.error(
                    "S3 객체 삭제 실패. objectKey={}",
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
