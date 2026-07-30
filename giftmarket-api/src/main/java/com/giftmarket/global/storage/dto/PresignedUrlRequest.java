package com.giftmarket.global.storage.dto;

import com.giftmarket.global.storage.type.StorageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PresignedUrlRequest(

        @NotNull(message = "저장 유형은 필수입니다.")
        StorageType type,

        @NotBlank(message = "파일명은 필수입니다.")
        String fileName,

        @NotBlank(message = "콘텐츠 타입은 필수입니다.")
        String contentType
) {
}