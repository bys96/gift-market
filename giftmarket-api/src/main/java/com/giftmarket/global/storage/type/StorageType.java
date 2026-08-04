package com.giftmarket.global.storage.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StorageType {

    PROFILE("profile"),
    PRODUCT_REPRESENTATIVE("products/representative"),
    PRODUCT_DETAIL("products/detail"),
    REVIEW("review"),
    BANNER("banner");

    private final String directory;
}