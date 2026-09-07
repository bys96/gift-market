package com.giftmarket.global.storage.service;

import com.giftmarket.global.storage.dto.PresignedUrlRequest;
import com.giftmarket.global.storage.provider.StorageProvider;
import com.giftmarket.global.storage.type.StorageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageServiceProductMediaTest {
    private static final long MB = 1024 * 1024L;
    private final StorageProvider provider = mock(StorageProvider.class);
    private final StorageService service = new StorageService(provider);

    @ParameterizedTest
    @EnumSource(value = StorageType.class, names = {"PRODUCT_REPRESENTATIVE", "PRODUCT_GALLERY", "PRODUCT_CONTENT"})
    void productImagesAccept20MbAndRejectOneByteOver(StorageType type) {
        var response = service.createPresignedUrl(12L, new PresignedUrlRequest(type, "photo.jpg", "image/jpeg", 20 * MB));
        assertThat(response.objectKey()).startsWith("products/12/").endsWith(".jpg");
        verify(provider).createUploadUrl(response.objectKey(), 300, 20 * MB, "image/jpeg");
        assertThatThrownBy(() -> service.createPresignedUrl(12L, new PresignedUrlRequest(type, "photo.jpg", "image/jpeg", 20 * MB + 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("20MB");
        verifyNoMoreInteractions(provider);
    }

    @ParameterizedTest
    @EnumSource(value = StorageType.class, names = {"PROFILE", "REVIEW", "RETURN_EVIDENCE", "EXCHANGE_EVIDENCE", "BANNER"})
    void otherImagesKeep5MbLimit(StorageType type) {
        assertThatCode(() -> service.createPresignedUrl(12L, new PresignedUrlRequest(type, "photo.png", "image/png", 5 * MB))).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.createPresignedUrl(12L, new PresignedUrlRequest(type, "photo.png", "image/png", 5 * MB + 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("5MB");
    }

    @Test
    void videoAccepts50MbAndCreatesSellerScopedKey() {
        var response = service.createPresignedUrl(12L, video("clip.MP4", "video/mp4", 50 * MB));
        assertThat(response.objectKey()).matches("products/12/content/video/[a-f0-9-]{36}\\.mp4");
        verify(provider).createUploadUrl(response.objectKey(), 300, 50 * MB, "video/mp4");
        assertThatThrownBy(() -> service.createPresignedUrl(12L, video("clip.mp4", "video/mp4", 50 * MB + 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("50MB");
        verifyNoMoreInteractions(provider);
    }

    @ParameterizedTest
    @CsvSource({"clip.webm,video/webm", "clip.mov,video/mp4", "clip.mp4,image/png", "clip.mp4,application/octet-stream"})
    void videoRejectsUnsupportedMimeOrExtension(String name, String mime) {
        assertThatThrownBy(() -> service.createPresignedUrl(12L, video(name, mime, MB)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("MP4");
        verifyNoInteractions(provider);
    }

    @Test
    void videoRequiresOwnerAndPositiveSize() {
        assertThatThrownBy(() -> service.createPresignedUrl(null, video("clip.mp4", "video/mp4", MB)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("소유자");
        for (Long size : new Long[]{null, 0L, -1L}) {
            assertThatThrownBy(() -> service.createPresignedUrl(12L, video("clip.mp4", "video/mp4", size)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("크기");
        }
        verifyNoInteractions(provider);
    }

    @Test
    void imagesStillRejectVideoAndMismatchedExtension() {
        assertThatThrownBy(() -> service.createPresignedUrl(12L, new PresignedUrlRequest(StorageType.PRODUCT_CONTENT, "clip.mp4", "video/mp4", MB)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createPresignedUrl(12L, new PresignedUrlRequest(StorageType.PRODUCT_CONTENT, "photo.gif", "image/png", MB)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(provider);
    }

    private PresignedUrlRequest video(String name, String mime, Long size) {
        return new PresignedUrlRequest(StorageType.PRODUCT_CONTENT_VIDEO, name, mime, size);
    }
}
