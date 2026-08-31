package com.giftmarket.admin.controller;

import com.giftmarket.admin.dto.response.AdminDashboardResponse;
import com.giftmarket.admin.service.AdminDashboardService;
import com.giftmarket.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping
    public ApiResponse<AdminDashboardResponse> getDashboard(
            @AuthenticationPrincipal Long adminUserId
    ) {
        return ApiResponse.success(adminDashboardService.getDashboard(adminUserId));
    }
}
