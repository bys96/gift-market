package com.giftmarket.seller.controller;

import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.seller.dto.response.SellerDashboardResponse;
import com.giftmarket.seller.service.SellerDashboardService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class SellerDashboardControllerTest {

    @Test
    void delegatesAuthenticatedSellerDashboardLookup() {
        SellerDashboardService service = mock(SellerDashboardService.class);
        SellerDashboardResponse dashboard = new SellerDashboardResponse(
                "선물 상점",
                new SellerDashboardResponse.ActionRequired(
                        1, 2,
                        new SellerDashboardResponse.ReturnActions(3, 1, 1, 1, 0),
                        new SellerDashboardResponse.ExchangeActions(4, 1, 1, 1, 1)
                ),
                new SellerDashboardResponse.ProductSummary(5, 6),
                List.of()
        );
        given(service.getDashboard(10L)).willReturn(dashboard);
        SellerDashboardController controller = new SellerDashboardController(service);

        ApiResponse<SellerDashboardResponse> response = controller.getDashboard(10L);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isSameAs(dashboard);
    }
}
