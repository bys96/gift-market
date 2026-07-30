package com.giftmarket.global.storage.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StorageType {

    PROFILE("profile"),
    PRODUCT("product"),
    REVIEW("review"),
    BANNER("banner");

    private final String directory;
}