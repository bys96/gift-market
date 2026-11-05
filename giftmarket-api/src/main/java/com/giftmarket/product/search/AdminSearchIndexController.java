package com.giftmarket.product.search;

import com.giftmarket.global.response.ApiResponse;
import org.springframework.web.bind.annotation.*;

/** Covered by existing /api/admin/** ADMIN/SUPER_ADMIN matcher. */
@RestController
@RequestMapping("/api/admin/product-search")
public class AdminSearchIndexController {
    private final MeiliIndexService index;
    public AdminSearchIndexController(MeiliIndexService index) { this.index = index; }
    @PostMapping("/reindex")
    public ApiResponse<MeiliIndexService.Result> reindex(@RequestParam(defaultValue = "false") boolean restart) {
        return ApiResponse.success(index.reindex(restart));
    }
}
