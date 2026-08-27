package com.giftmarket.review.service;

import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.ExchangeRequestItemRepository;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductVariant;
import com.giftmarket.review.dto.ReviewUpdateRequest;
import com.giftmarket.review.dto.ReviewUpsertRequest;
import com.giftmarket.review.entity.Review;
import com.giftmarket.review.entity.ReviewImage;
import com.giftmarket.review.exception.ReviewException;
import com.giftmarket.review.repository.ReviewImageRepository;
import com.giftmarket.review.repository.ReviewRepository;
import com.giftmarket.review.repository.ReviewSummaryProjection;
import com.giftmarket.global.storage.service.StorageService;
import com.giftmarket.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {
    @Mock ReviewRepository reviews; @Mock ReviewImageRepository images; @Mock OrderItemRepository items;
    @Mock ExchangeRequestItemRepository exchanges; @Mock StorageService storage;
    @InjectMocks ReviewService service;
    @Mock OrderItem item; @Mock Order order; @Mock User user; @Mock Product product; @Mock ProductVariant variant;

    @BeforeEach void setUp() {
        lenient().when(items.findByIdForReviewUpdate(10L)).thenReturn(Optional.of(item));
        lenient().when(item.getId()).thenReturn(10L); lenient().when(item.getOrder()).thenReturn(order);
        lenient().when(order.getUser()).thenReturn(user); lenient().when(user.getId()).thenReturn(1L);
        lenient().when(user.getName()).thenReturn("홍길동"); lenient().when(item.getConfirmedQuantity()).thenReturn(1);
        lenient().when(item.getProduct()).thenReturn(product); lenient().when(item.getVariant()).thenReturn(variant);
        lenient().when(item.getProductName()).thenReturn("원 상품"); lenient().when(item.getOptionSnapshot()).thenReturn("색상: 파랑");
        lenient().when(item.getUnitPrice()).thenReturn(1000L); lenient().when(exchanges.findLatestCompletedByOrderItemId(10L)).thenReturn(Optional.empty());
        lenient().when(reviews.findByOrderItemId(10L)).thenReturn(Optional.empty());
        lenient().when(reviews.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test void confirmedPurchaseCreatesReviewFromOriginalSnapshot() {
        service.create(1L, request(List.of()));
        ArgumentCaptor<Review> captor=ArgumentCaptor.forClass(Review.class); verify(reviews).save(captor.capture());
        assertThat(captor.getValue().getProductNameSnapshot()).isEqualTo("원 상품");
        assertThat(captor.getValue().getVariant()).isSameAs(variant);
    }

    @Test void latestCompletedExchangeTargetIsUsed() {
        ExchangeRequestItem exchange=mock(ExchangeRequestItem.class); Product target=mock(Product.class); ProductVariant targetVariant=mock(ProductVariant.class);
        when(exchange.getTargetProduct()).thenReturn(target); when(exchange.getTargetVariant()).thenReturn(targetVariant);
        when(exchange.getTargetProductName()).thenReturn("교환 상품"); when(exchange.getTargetOptionSnapshot()).thenReturn("색상: 빨강"); when(exchange.getTargetUnitPrice()).thenReturn(1000L);
        when(exchanges.findLatestCompletedByOrderItemId(10L)).thenReturn(Optional.of(exchange));
        service.create(1L,request(List.of())); ArgumentCaptor<Review> captor=ArgumentCaptor.forClass(Review.class); verify(reviews).save(captor.capture());
        assertThat(captor.getValue().getProduct()).isSameAs(target); assertThat(captor.getValue().getVariant()).isSameAs(targetVariant);
    }

    @Test void unconfirmedItemIsRejected() { when(item.getConfirmedQuantity()).thenReturn(0); assertThatThrownBy(()->service.create(1L,request(List.of()))).isInstanceOf(ReviewException.class); }
    @Test void anotherBuyerIsHiddenAsNotFound() { assertThatThrownBy(()->service.create(2L,request(List.of()))).isInstanceOf(ReviewException.class).hasMessageContaining("찾을 수 없습니다"); }
    @Test void activeDuplicateIsRejected() { when(reviews.findByOrderItemId(10L)).thenReturn(Optional.of(mock(Review.class))); assertThatThrownBy(()->service.create(1L,request(List.of()))).isInstanceOf(ReviewException.class).hasMessageContaining("이미"); }
    @Test void sixImagesAreRejectedByServiceToo() { assertThatThrownBy(()->service.create(1L,request(List.of("reviews/1/1","reviews/1/2","reviews/1/3","reviews/1/4","reviews/1/5","reviews/1/6")))).isInstanceOf(ReviewException.class); }
    @Test void duplicateImageKeysAreRejected() { assertThatThrownBy(()->service.create(1L,request(List.of("reviews/1/a","reviews/1/a")))).isInstanceOf(ReviewException.class); }
    @Test void anotherUsersImagePrefixIsRejected() { assertThatThrownBy(()->service.create(1L,request(List.of("reviews/2/a")))).isInstanceOf(ReviewException.class); }
    @Test void fiveImagesAreStoredWithoutNPlusOneRead() { service.create(1L,request(List.of("reviews/1/1","reviews/1/2","reviews/1/3","reviews/1/4","reviews/1/5"))); verify(images,times(5)).save(any()); }

    @Test void zeroImagesCreateWithoutImageRows() {
        service.create(1L, request(List.of()));
        verify(images, never()).save(any());
    }

    @Test void oneImageIsStoredAndReturnedAsReadUrl() {
        when(storage.createReadUrl("reviews/1/a.jpg")).thenReturn("https://read/a");
        var response = service.create(1L, request(List.of("reviews/1/a.jpg")));
        verify(images).save(any(ReviewImage.class));
        assertThat(response.images()).containsExactly("https://read/a");
    }

    @Test void deletedReviewIsRestoredWithoutNewRowAndImagesAreReplaced() {
        Review deleted = mock(Review.class);
        when(deleted.getDeletedAt()).thenReturn(java.time.LocalDateTime.now());
        when(deleted.getId()).thenReturn(77L);
        when(deleted.getUser()).thenReturn(user);
        when(deleted.getRating()).thenReturn(5);
        when(deleted.getContent()).thenReturn("재작성");
        when(deleted.getProductNameSnapshot()).thenReturn("원 상품");
        when(deleted.getUnitPriceSnapshot()).thenReturn(1000L);
        when(reviews.findByOrderItemId(10L)).thenReturn(Optional.of(deleted));

        service.create(1L, request(List.of()));

        verify(deleted).restore(product, variant, "원 상품", "색상: 파랑", 1000L, 5, "좋아요");
        verify(reviews, never()).save(any());
        verify(images).deleteAllByReviewId(77L);
        verify(images).flush();
    }

    @Test void ownerCanUpdateRatingContentAndImages() {
        Review review = activeReview(88L, user);
        when(reviews.findByIdAndDeletedAtIsNull(88L)).thenReturn(Optional.of(review));
        when(storage.createReadUrl("reviews/1/new.jpg")).thenReturn("https://read/new");

        var response = service.update(1L, 88L,
                new ReviewUpdateRequest(3, "수정 후기", List.of("reviews/1/new.jpg")));

        verify(review).update(3, "수정 후기");
        verify(images).deleteAllByReviewId(88L);
        verify(images).flush();
        verify(images).save(any(ReviewImage.class));
        assertThat(response.images()).containsExactly("https://read/new");
    }

    @Test void anotherBuyerCannotUpdateOrDelete() {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(2L);
        Review review = activeReview(88L, owner);
        when(reviews.findByIdAndDeletedAtIsNull(88L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.update(1L, 88L,
                new ReviewUpdateRequest(3, "수정", List.of())))
                .isInstanceOf(ReviewException.class);
        assertThatThrownBy(() -> service.delete(1L, 88L))
                .isInstanceOf(ReviewException.class);
        verify(review, never()).update(anyInt(), anyString());
        verify(review, never()).delete(any());
    }

    @Test void ownerDeleteUsesSoftDelete() {
        Review review = activeReview(88L, user);
        when(reviews.findByIdAndDeletedAtIsNull(88L)).thenReturn(Optional.of(review));

        service.delete(1L, 88L);

        verify(review).delete(any(java.time.LocalDateTime.class));
        verify(reviews, never()).delete(any());
        verify(images, never()).deleteAllByReviewId(anyLong());
    }

    @Test void productPageUsesActiveRepositoryResultAndAggregateSummary() {
        Review review = activeReview(88L, user);
        ReviewImage image = mock(ReviewImage.class);
        ReviewSummaryProjection summary = mock(ReviewSummaryProjection.class);
        when(review.getRating()).thenReturn(4);
        when(review.getContent()).thenReturn("후기");
        when(review.getProductNameSnapshot()).thenReturn("상품");
        when(review.getUnitPriceSnapshot()).thenReturn(1000L);
        when(reviews.findAllByProductIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                3L, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(review), PageRequest.of(0, 10), 1));
        when(image.getReview()).thenReturn(review);
        when(image.getObjectKey()).thenReturn("reviews/1/a.jpg");
        when(images.findAllByReviewIdInOrderByReviewIdAscSortOrderAsc(List.of(88L)))
                .thenReturn(List.of(image));
        when(reviews.summarize(3L)).thenReturn(summary);
        when(summary.getReviewCount()).thenReturn(2L);
        when(summary.getAverageRating()).thenReturn(4.45);
        when(storage.createReadUrl("reviews/1/a.jpg")).thenReturn("https://read/a");

        var page = service.getProductReviews(3L, 1L, 0, 10);

        assertThat(page.reviewCount()).isEqualTo(2);
        assertThat(page.averageRating()).isEqualTo(4.5);
        assertThat(page.reviews()).hasSize(1);
        assertThat(page.reviews().getFirst().images()).containsExactly("https://read/a");
    }

    @Test void emptyProductPageReturnsZeroSummaryWithoutImageQuery() {
        ReviewSummaryProjection summary = mock(ReviewSummaryProjection.class);
        when(reviews.findAllByProductIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                3L, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        when(reviews.summarize(3L)).thenReturn(summary);
        when(summary.getReviewCount()).thenReturn(0L);
        when(summary.getAverageRating()).thenReturn(0.0);

        var page = service.getProductReviews(3L, null, 0, 10);

        assertThat(page.reviewCount()).isZero();
        assertThat(page.averageRating()).isZero();
        verify(images, never()).findAllByReviewIdInOrderByReviewIdAscSortOrderAsc(any());
    }

    private Review activeReview(long id, User owner) {
        Review review = mock(Review.class);
        lenient().when(review.getId()).thenReturn(id);
        lenient().when(review.getUser()).thenReturn(owner);
        lenient().when(review.getProductNameSnapshot()).thenReturn("상품");
        lenient().when(review.getUnitPriceSnapshot()).thenReturn(1000L);
        return review;
    }

    private ReviewUpsertRequest request(List<String> keys) { return new ReviewUpsertRequest(10L,5,"좋아요",keys); }
}
