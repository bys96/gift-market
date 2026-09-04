package com.giftmarket.global.storage.service;

import com.giftmarket.global.storage.dto.PresignedUrlRequest;
import com.giftmarket.global.storage.provider.StorageProvider;
import com.giftmarket.global.storage.type.StorageType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class StorageServiceReviewTest {

    @Test
    void reviewPresignCreatesOwnerScopedObjectKey() {
        StorageProvider storageProvider = mock(StorageProvider.class);
        when(storageProvider.createUploadUrl(anyString(), anyInt()))
                .thenReturn("https://upload");

        StorageService service = new StorageService(storageProvider);

        var response = service.createPresignedUrl(
                7L,
                new PresignedUrlRequest(
                        StorageType.REVIEW,
                        "photo.webp",
                        "image/webp",
                        1024L
                )
        );

        assertThat(response.uploadUrl()).isEqualTo("https://upload");
        assertThat(response.objectKey())
                .startsWith("reviews/7/")
                .endsWith(".webp");
    }

    @Test
    void reviewPresignRejectsFileOverFiveMegabytes() {
        StorageService service = new StorageService(
                mock(StorageProvider.class)
        );

        assertThatThrownBy(() -> service.createPresignedUrl(
                7L,
                new PresignedUrlRequest(
                        StorageType.REVIEW,
                        "photo.jpg",
                        "image/jpeg",
                        5L * 1024 * 1024 + 1
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5MB");
    }

    @Test
    void reviewPresignRequiresAuthenticatedOwner() {
        StorageService service = new StorageService(
                mock(StorageProvider.class)
        );

        assertThatThrownBy(() -> service.createPresignedUrl(
                null,
                new PresignedUrlRequest(
                        StorageType.REVIEW,
                        "photo.png",
                        "image/png",
                        1024L
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("소유자");
    }
}