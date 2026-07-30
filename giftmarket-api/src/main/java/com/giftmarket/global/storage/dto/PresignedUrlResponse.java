package com.giftmarket.global.storage.dto;

public record PresignedUrlResponse(
        String uploadUrl,
        String objectKey
) {
}