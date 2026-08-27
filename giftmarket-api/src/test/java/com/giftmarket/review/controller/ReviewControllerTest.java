package com.giftmarket.review.controller;

import com.giftmarket.review.dto.*;
import com.giftmarket.review.service.ReviewService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReviewControllerTest {
    @Test void forwardsAuthenticatedBuyerIdForMutationsAndOwnershipQueries() {
        ReviewService service = mock(ReviewService.class);
        ReviewController controller = new ReviewController(service);
        ReviewResponse response = mock(ReviewResponse.class);
        ReviewEditResponse edit = mock(ReviewEditResponse.class);
        ReviewEligibilityResponse eligibility = mock(ReviewEligibilityResponse.class);
        ReviewUpsertRequest create = new ReviewUpsertRequest(10L, 5, "후기", List.of());
        ReviewUpdateRequest update = new ReviewUpdateRequest(4, "수정", List.of());
        when(service.create(1L, create)).thenReturn(response);
        when(service.update(1L, 20L, update)).thenReturn(response);
        when(service.getMine(1L, 20L)).thenReturn(edit);
        when(service.getReviewIds(1L, List.of(10L))).thenReturn(Map.of(10L, 20L));
        when(service.getEligibility(1L, 10L)).thenReturn(eligibility);

        assertThat(controller.create(1L, create).data()).isSameAs(response);
        assertThat(controller.update(1L, 20L, update).data()).isSameAs(response);
        assertThat(controller.getMine(1L, 20L).data()).isSameAs(edit);
        assertThat(controller.reviewIds(1L, List.of(10L)).data()).containsEntry(10L, 20L);
        assertThat(controller.eligibility(1L, 10L).data()).isSameAs(eligibility);
        assertThat(controller.delete(1L, 20L).success()).isTrue();
        verify(service).delete(1L, 20L);
    }

    @Test void publicProductListAllowsAnonymousPrincipal() {
        ReviewService service = mock(ReviewService.class);
        ReviewController controller = new ReviewController(service);
        ReviewPageResponse page = mock(ReviewPageResponse.class);
        when(service.getProductReviews(3L, null, 0, 10)).thenReturn(page);

        assertThat(controller.list(null, 3L, 0, 10).data()).isSameAs(page);
    }
}
