package com.giftmarket.address.controller;

import com.giftmarket.address.dto.request.AddressRequest;
import com.giftmarket.address.dto.response.AddressResponse;
import com.giftmarket.address.service.AddressService;
import com.giftmarket.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/addresses")
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public ApiResponse<List<AddressResponse>> getMyAddresses(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(
                addressService.getMyAddresses(
                        userId
                )
        );
    }

    @PostMapping
    public ApiResponse<AddressResponse> createAddress(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AddressRequest request
    ) {
        return ApiResponse.success(
                addressService.createAddress(
                        userId,
                        request
                )
        );
    }

    @PutMapping("/{addressId}")
    public ApiResponse<AddressResponse> updateAddress(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long addressId,
            @Valid @RequestBody AddressRequest request
    ) {
        return ApiResponse.success(
                addressService.updateAddress(
                        userId,
                        addressId,
                        request
                )
        );
    }

    @PatchMapping("/{addressId}/default")
    public ApiResponse<AddressResponse> setDefaultAddress(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long addressId
    ) {
        return ApiResponse.success(
                addressService.setDefaultAddress(
                        userId,
                        addressId
                )
        );
    }

    @DeleteMapping("/{addressId}")
    public ApiResponse<Void> deleteAddress(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long addressId
    ) {
        addressService.deleteAddress(
                userId,
                addressId
        );

        return ApiResponse.success(null);
    }
}