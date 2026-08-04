package com.giftmarket.product.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.product.dto.response.CategoryResponse;
import com.giftmarket.product.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ApiResponse<List<CategoryResponse>> getActiveCategories() {
        return ApiResponse.success(
                categoryService.getActiveCategories()
        );
    }
}