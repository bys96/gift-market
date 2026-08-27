package com.giftmarket.review.service;

import com.giftmarket.order.entity.*;
import com.giftmarket.order.repository.ExchangeRequestItemRepository;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductVariant;
import com.giftmarket.review.dto.ReviewUpsertRequest;
import com.giftmarket.review.entity.Review;
import com.giftmarket.review.exception.ReviewException;
import com.giftmarket.review.repository.ReviewImageRepository;
import com.giftmarket.review.repository.ReviewRepository;
import com.giftmarket.global.storage.service.StorageService;
import com.giftmarket.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

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

    private ReviewUpsertRequest request(List<String> keys) { return new ReviewUpsertRequest(10L,5,"좋아요",keys); }
}
