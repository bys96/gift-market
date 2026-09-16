package com.giftmarket.settlement.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.settlement.dto.response.SellerSettlementDetailResponse;
import com.giftmarket.settlement.dto.response.SellerSettlementPageResponse;
import com.giftmarket.settlement.dto.response.SellerSettlementSummaryResponse;
import com.giftmarket.settlement.entity.SettlementStatus;
import com.giftmarket.settlement.service.SellerSettlementQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seller/settlements")
public class SellerSettlementController {

    private final SellerSettlementQueryService queryService;

    @GetMapping
    public ApiResponse<SellerSettlementPageResponse> getSettlements(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) SettlementStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(queryService.getSettlements(userId, status, page, size));
    }

    @GetMapping("/summary")
    public ApiResponse<SellerSettlementSummaryResponse> getSummary(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(queryService.getSummary(userId));
    }

    @GetMapping("/{settlementId}")
    public ApiResponse<SellerSettlementDetailResponse> getSettlement(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long settlementId
    ) {
        return ApiResponse.success(queryService.getSettlement(userId, settlementId));
    }
}
