package com.giftmarket.global.storage.service;

import com.giftmarket.global.storage.config.MinioProperties;
import com.giftmarket.global.storage.dto.PresignedUrlRequest;
import com.giftmarket.global.storage.type.StorageType;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StorageServiceReviewTest {
    @Test void reviewPresignCreatesOwnerScopedObjectKey() throws Exception {
        MinioClient minio = mock(MinioClient.class);
        when(minio.getPresignedObjectUrl(any())).thenReturn("https://upload");
        StorageService service = new StorageService(minio,
                new MinioProperties("endpoint", "access", "secret", "bucket"));

        var response = service.createPresignedUrl(7L,
                new PresignedUrlRequest(StorageType.REVIEW, "photo.webp", "image/webp", 1024L));

        assertThat(response.uploadUrl()).isEqualTo("https://upload");
        assertThat(response.objectKey()).startsWith("reviews/7/").endsWith(".webp");
    }

    @Test void reviewPresignRejectsFileOverFiveMegabytes() {
        StorageService service = new StorageService(mock(MinioClient.class),
                new MinioProperties("endpoint", "access", "secret", "bucket"));

        assertThatThrownBy(() -> service.createPresignedUrl(7L,
                new PresignedUrlRequest(StorageType.REVIEW, "photo.jpg", "image/jpeg", 5L * 1024 * 1024 + 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5MB");
    }

    @Test void reviewPresignRequiresAuthenticatedOwner() {
        StorageService service = new StorageService(mock(MinioClient.class),
                new MinioProperties("endpoint", "access", "secret", "bucket"));

        assertThatThrownBy(() -> service.createPresignedUrl(null,
                new PresignedUrlRequest(StorageType.REVIEW, "photo.png", "image/png", 1024L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("소유자");
    }
}
