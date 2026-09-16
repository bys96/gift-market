package com.giftmarket.settlement.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.settlement.dto.request.AdminSettlementGenerateRequest;
import com.giftmarket.settlement.dto.request.AdminSettlementHoldRequest;
import com.giftmarket.settlement.dto.response.AdminSettlementDetailResponse;
import com.giftmarket.settlement.dto.response.AdminSettlementGenerateResponse;
import com.giftmarket.settlement.dto.response.AdminSettlementPageResponse;
import com.giftmarket.settlement.dto.response.AdminSettlementResponse;
import com.giftmarket.settlement.entity.SettlementStatus;
import com.giftmarket.settlement.service.AdminSettlementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/settlements")
public class AdminSettlementController {

    private final AdminSettlementService service;

    @GetMapping
    public ApiResponse<AdminSettlementPageResponse> getSettlements(
            @AuthenticationPrincipal Long adminUserId,
            @RequestParam(required = false) @Positive Long sellerId,
            @RequestParam(required = false) SettlementStatus status,
            @RequestParam(required = false) LocalDateTime periodStart,
            @RequestParam(required = false) LocalDateTime periodEnd,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(service.getSettlements(
                adminUserId, sellerId, status, periodStart, periodEnd, page, size
        ));
    }

    @GetMapping("/{settlementId}")
    public ApiResponse<AdminSettlementDetailResponse> getSettlement(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long settlementId
    ) {
        return ApiResponse.success(service.getSettlement(adminUserId, settlementId));
    }

    @PostMapping("/generate")
    public ApiResponse<AdminSettlementGenerateResponse> generate(
            @AuthenticationPrincipal Long adminUserId,
            @Valid @RequestBody AdminSettlementGenerateRequest request
    ) {
        return ApiResponse.success(service.generate(adminUserId, request));
    }

    @PostMapping("/{settlementId}/hold")
    public ApiResponse<AdminSettlementResponse> hold(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long settlementId,
            @Valid @RequestBody AdminSettlementHoldRequest request
    ) {
        return ApiResponse.success(service.hold(adminUserId, settlementId, request.reason()));
    }

    @PostMapping("/{settlementId}/release")
    public ApiResponse<AdminSettlementResponse> release(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long settlementId
    ) {
        return ApiResponse.success(service.release(adminUserId, settlementId));
    }

    @PostMapping("/{settlementId}/confirm")
    public ApiResponse<AdminSettlementResponse> confirm(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long settlementId
    ) {
        return ApiResponse.success(service.confirm(adminUserId, settlementId));
    }
}
