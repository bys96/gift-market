package com.giftmarket.global.storage.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StorageType {

    PROFILE("profile"),
    PRODUCT_REPRESENTATIVE("products/representative"),
    PRODUCT_GALLERY("products/gallery"),
    PRODUCT_CONTENT("products/content"),
    PRODUCT_CONTENT_VIDEO("products/content/video"),
    RETURN_EVIDENCE("returns"),
    EXCHANGE_EVIDENCE("exchanges"),
    REVIEW("reviews"),
    BANNER("banner"),
    STORE_LOGO("stores/logo"),
    STORE_BANNER("stores/banner");

    private final String directory;
}
