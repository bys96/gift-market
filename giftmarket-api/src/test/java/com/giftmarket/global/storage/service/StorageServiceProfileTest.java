package com.giftmarket.global.storage.service;

import com.giftmarket.global.storage.config.MinioProperties;
import com.giftmarket.global.storage.dto.PresignedUrlRequest;
import com.giftmarket.global.storage.type.StorageType;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StorageServiceProfileTest {
    @Test void profilePresignCreatesUserScopedObjectKey() throws Exception {
        MinioClient minio = mock(MinioClient.class);
        when(minio.getPresignedObjectUrl(any())).thenReturn("https://upload");
        StorageService service = service(minio);

        var response = service.createPresignedUrl(12L,
                new PresignedUrlRequest(StorageType.PROFILE, "avatar.png", "image/png", 1024L));

        assertThat(response.uploadUrl()).isEqualTo("https://upload");
        assertThat(response.objectKey()).startsWith("profiles/12/").endsWith(".png");
        assertThat(response.objectKey().substring("profiles/12/".length())).doesNotContain("/");
    }

    @Test void profilePresignRequiresAuthenticatedOwner() {
        StorageService service = service(mock(MinioClient.class));

        assertThatThrownBy(() -> service.createPresignedUrl(null,
                new PresignedUrlRequest(StorageType.PROFILE, "avatar.jpg", "image/jpeg", 1024L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("소유자");
    }

    private StorageService service(MinioClient minio) {
        return new StorageService(minio,
                new MinioProperties("endpoint", "access", "secret", "bucket"));
    }
}
