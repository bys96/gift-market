package com.giftmarket.global.response;

import lombok.Builder;

@Builder
public record ApiResponse<T>(
        boolean success,
        String message,
        T data
) {

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message("SUCCESS")
                .data(data)
                .build();
    }

    public static ApiResponse<?> fail(String message) {
        return ApiResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}