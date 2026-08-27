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
    RETURN_EVIDENCE("returns"),
    EXCHANGE_EVIDENCE("exchanges"),
    REVIEW("reviews"),
    BANNER("banner");

    private final String directory;
}
