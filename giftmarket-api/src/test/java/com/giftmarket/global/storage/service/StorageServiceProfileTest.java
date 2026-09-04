package com.giftmarket.global.storage.service;

import com.giftmarket.global.storage.dto.PresignedUrlRequest;
import com.giftmarket.global.storage.provider.StorageProvider;
import com.giftmarket.global.storage.type.StorageType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class StorageServiceProfileTest {

    @Test
    void profilePresignCreatesUserScopedObjectKey() {
        StorageProvider storageProvider = mock(StorageProvider.class);
        when(storageProvider.createUploadUrl(anyString(), anyInt()))
                .thenReturn("https://upload");

        StorageService service = service(storageProvider);

        var response = service.createPresignedUrl(
                12L,
                new PresignedUrlRequest(
                        StorageType.PROFILE,
                        "avatar.png",
                        "image/png",
                        1024L
                )
        );

        assertThat(response.uploadUrl()).isEqualTo("https://upload");
        assertThat(response.objectKey())
                .startsWith("profiles/12/")
                .endsWith(".png");
        assertThat(response.objectKey()
                .substring("profiles/12/".length()))
                .doesNotContain("/");
    }

    @Test
    void profilePresignRequiresAuthenticatedOwner() {
        StorageService service = service(mock(StorageProvider.class));

        assertThatThrownBy(() -> service.createPresignedUrl(
                null,
                new PresignedUrlRequest(
                        StorageType.PROFILE,
                        "avatar.jpg",
                        "image/jpeg",
                        1024L
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("소유자");
    }

    private StorageService service(StorageProvider storageProvider) {
        return new StorageService(storageProvider);
    }
}