package com.giftmarket.global.storage.controller;

import com.giftmarket.global.storage.dto.PresignedUrlRequest;
import com.giftmarket.global.storage.service.StorageService;
import com.giftmarket.global.storage.type.StorageType;
import com.giftmarket.product.exception.ProductException;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.repository.SellerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageControllerProductVideoTest {
    private final StorageService service = mock(StorageService.class);
    private final SellerRepository sellers = mock(SellerRepository.class);
    private final StorageController controller = new StorageController(service, sellers);
    private final PresignedUrlRequest request = new PresignedUrlRequest(StorageType.PRODUCT_CONTENT_VIDEO, "clip.mp4", "video/mp4", 1024L);

    @Test
    void activeSellerUsesSellerIdInsteadOfUserId() {
        Seller seller = mock(Seller.class);
        when(seller.getId()).thenReturn(99L);
        when(seller.getStatus()).thenReturn(SellerStatus.ACTIVE);
        when(sellers.findByUserId(12L)).thenReturn(Optional.of(seller));
        controller.createPresignedUrl(12L, request);
        verify(service).createPresignedUrl(99L, request);
    }

    @Test
    void missingSellerIsRejected() {
        when(sellers.findByUserId(12L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> controller.createPresignedUrl(12L, request)).isInstanceOf(ProductException.class);
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @EnumSource(value = SellerStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "ACTIVE")
    void inactiveSellerIsRejected(SellerStatus status) {
        Seller seller = mock(Seller.class);
        when(seller.getStatus()).thenReturn(status);
        when(sellers.findByUserId(12L)).thenReturn(Optional.of(seller));
        assertThatThrownBy(() -> controller.createPresignedUrl(12L, request)).isInstanceOf(ProductException.class);
        verifyNoInteractions(service);
    }
}
