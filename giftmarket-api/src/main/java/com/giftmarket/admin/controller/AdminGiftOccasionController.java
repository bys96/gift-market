package com.giftmarket.admin.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.product.dto.request.GiftOccasionActiveUpdateRequest;
import com.giftmarket.product.dto.request.GiftOccasionCreateRequest;
import com.giftmarket.product.dto.request.GiftOccasionUpdateRequest;
import com.giftmarket.product.dto.response.AdminGiftOccasionResponse;
import com.giftmarket.product.service.GiftOccasionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/gift-occasions")
public class AdminGiftOccasionController {

    private final GiftOccasionService giftOccasionService;

    @GetMapping
    public ApiResponse<List<AdminGiftOccasionResponse>> getAllGiftOccasions() {
        return ApiResponse.success(giftOccasionService.getAllGiftOccasions());
    }

    @PostMapping
    public ApiResponse<AdminGiftOccasionResponse> createGiftOccasion(
            @Valid @RequestBody GiftOccasionCreateRequest request
    ) {
        return ApiResponse.success(giftOccasionService.createGiftOccasion(request));
    }

    @PutMapping("/{giftOccasionId}")
    public ApiResponse<AdminGiftOccasionResponse> updateGiftOccasion(
            @PathVariable Long giftOccasionId,
            @Valid @RequestBody GiftOccasionUpdateRequest request
    ) {
        return ApiResponse.success(
                giftOccasionService.updateGiftOccasion(giftOccasionId, request)
        );
    }

    @PatchMapping("/{giftOccasionId}/active")
    public ApiResponse<AdminGiftOccasionResponse> updateActive(
            @PathVariable Long giftOccasionId,
            @Valid @RequestBody GiftOccasionActiveUpdateRequest request
    ) {
        return ApiResponse.success(
                giftOccasionService.updateActive(giftOccasionId, request)
        );
    }
}
