package com.giftmarket.auth.dto;

public record TokenReissueResult(
        String accessToken,
        String refreshToken
) {
}