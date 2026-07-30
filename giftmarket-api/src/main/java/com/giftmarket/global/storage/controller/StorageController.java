package com.giftmarket.global.storage.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.global.storage.dto.PresignedUrlRequest;
import com.giftmarket.global.storage.dto.PresignedUrlResponse;
import com.giftmarket.global.storage.service.StorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;

    @PostMapping("/presigned-url")
    public ApiResponse<PresignedUrlResponse> createPresignedUrl(
            @Valid @RequestBody PresignedUrlRequest request
    ) {
        return ApiResponse.success(
                storageService.createPresignedUrl(request)
        );
    }
}