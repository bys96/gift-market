package com.giftmarket.admin.controller;

import com.giftmarket.admin.dto.response.AdministratorResponse;
import com.giftmarket.admin.service.AdministratorService;
import com.giftmarket.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/administrators")
@RequiredArgsConstructor
public class AdministratorController {

    private final AdministratorService administratorService;

    @GetMapping
    public ApiResponse<List<AdministratorResponse>> getAdministrators(
            @AuthenticationPrincipal Long operatorUserId
    ) {
        return ApiResponse.success(administratorService.getAdministrators(operatorUserId));
    }

    @PatchMapping("/{userId}/grant")
    public ApiResponse<AdministratorResponse> grantAdministrator(
            @AuthenticationPrincipal Long operatorUserId,
            @PathVariable Long userId
    ) {
        return ApiResponse.success(
                administratorService.grantAdministrator(operatorUserId, userId)
        );
    }

    @PatchMapping("/{userId}/revoke")
    public ApiResponse<AdministratorResponse> revokeAdministrator(
            @AuthenticationPrincipal Long operatorUserId,
            @PathVariable Long userId
    ) {
        return ApiResponse.success(
                administratorService.revokeAdministrator(operatorUserId, userId)
        );
    }
}
