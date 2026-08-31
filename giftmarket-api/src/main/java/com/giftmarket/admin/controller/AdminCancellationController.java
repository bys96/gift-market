package com.giftmarket.admin.controller;
import com.giftmarket.admin.dto.response.*;
import com.giftmarket.admin.service.AdminCancellationService;
import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.order.entity.OrderCancellationStatus;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@Validated @RestController @RequestMapping("/api/admin/cancellations") @RequiredArgsConstructor
public class AdminCancellationController {
 private final AdminCancellationService service;
 @GetMapping public ApiResponse<AdminCancellationPageResponse> getAll(@AuthenticationPrincipal Long adminId,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size,@RequestParam(required=false) String keyword,@RequestParam(required=false) OrderCancellationStatus status,@RequestParam(required=false) Boolean requiresSellerApproval){return ApiResponse.success(service.getCancellations(adminId,page,size,keyword,status,requiresSellerApproval));}
 @GetMapping("/{id}") public ApiResponse<AdminCancellationDetailResponse> get(@AuthenticationPrincipal Long adminId,@PathVariable Long id){return ApiResponse.success(service.getCancellation(adminId,id));}
}
