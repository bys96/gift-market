package com.giftmarket.product.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.product.dto.response.GiftOccasionResponse;
import com.giftmarket.product.service.GiftOccasionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/gift-occasions")
public class GiftOccasionController {

    private final GiftOccasionService giftOccasionService;

    @GetMapping
    public ApiResponse<List<GiftOccasionResponse>> getActiveGiftOccasions() {
        return ApiResponse.success(giftOccasionService.getActiveGiftOccasions());
    }
}
