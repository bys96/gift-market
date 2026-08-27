package com.giftmarket.review.service;

import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.global.storage.service.StorageService;
import com.giftmarket.order.entity.ExchangeRequestItem;
import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.order.repository.ExchangeRequestItemRepository;
import com.giftmarket.order.repository.OrderItemRepository;
import com.giftmarket.review.dto.*;
import com.giftmarket.review.entity.*;
import com.giftmarket.review.exception.ReviewException;
import com.giftmarket.review.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository imageRepository;
    private final OrderItemRepository orderItemRepository;
    private final ExchangeRequestItemRepository exchangeItemRepository;
    private final StorageService storageService;

    @Transactional
    public ReviewResponse create(Long userId, ReviewUpsertRequest request) {
        requireUser(userId);
        OrderItem item = orderItemRepository.findByIdForReviewUpdate(request.orderItemId()).orElseThrow(this::notFound);
        if (!item.getOrder().getUser().getId().equals(userId)) throw notFound();
        if (item.getConfirmedQuantity() <= 0) throw new ReviewException("구매확정된 상품만 리뷰를 작성할 수 있습니다.");
        List<String> keys = validateKeys(userId, request.imageObjectKeys());
        Optional<Review> existing = reviewRepository.findByOrderItemId(item.getId());
        if (existing.isPresent() && existing.get().getDeletedAt() == null) throw new ReviewException("이미 리뷰를 작성한 주문 상품입니다.");
        Target target = resolveTarget(item);
        Review review;
        if (existing.isPresent()) {
            review = existing.get();
            review.restore(target.product(), target.variant(), target.name(), target.option(), target.price(), request.rating(), request.content());
            imageRepository.deleteAllByReviewId(review.getId());
            imageRepository.flush();
        } else {
            review = reviewRepository.save(Review.create(item.getOrder().getUser(), item, target.product(), target.variant(),
                    target.name(), target.option(), target.price(), request.rating(), request.content()));
        }
        saveImages(review, keys);
        return response(review, keys, userId);
    }

    @Transactional
    public ReviewResponse update(Long userId, Long reviewId, ReviewUpdateRequest request) {
        Review review = owned(userId, reviewId);
        List<String> keys = validateKeys(userId, request.imageObjectKeys());
        review.update(request.rating(), request.content());
        imageRepository.deleteAllByReviewId(reviewId);
        imageRepository.flush();
        saveImages(review, keys);
        return response(review, keys, userId);
    }

    @Transactional
    public void delete(Long userId, Long reviewId) { owned(userId, reviewId).delete(LocalDateTime.now()); }

    @Transactional(readOnly = true)
    public ReviewEditResponse getMine(Long userId, Long reviewId) {
        Review review = owned(userId, reviewId);
        List<String> keys = imageRepository.findAllByReviewIdInOrderByReviewIdAscSortOrderAsc(List.of(reviewId)).stream().map(ReviewImage::getObjectKey).toList();
        return new ReviewEditResponse(response(review, keys, userId), keys);
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> getReviewIds(Long userId, List<Long> orderItemIds) {
        requireUser(userId);
        if (orderItemIds == null || orderItemIds.isEmpty()) return Map.of();
        if (orderItemIds.size() > 100) throw new ReviewException("주문 상품은 한 번에 100개까지 조회할 수 있습니다.");
        List<OrderItem> items = orderItemRepository.findAllById(orderItemIds);
        if (items.size() != new HashSet<>(orderItemIds).size()
                || items.stream().anyMatch(item -> !item.getOrder().getUser().getId().equals(userId))) throw notFound();
        return reviewRepository.findActiveIdsByOrderItemIds(orderItemIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    @Transactional(readOnly = true)
    public ReviewEligibilityResponse getEligibility(Long userId, Long orderItemId) {
        requireUser(userId);
        OrderItem item = orderItemRepository.findById(orderItemId).orElseThrow(this::notFound);
        if (!item.getOrder().getUser().getId().equals(userId)) throw notFound();
        Review review = reviewRepository.findByOrderItemId(orderItemId).filter(r -> r.getDeletedAt() == null).orElse(null);
        Target target = resolveTarget(item);
        return new ReviewEligibilityResponse(orderItemId, review == null ? null : review.getId(),
                item.getConfirmedQuantity() > 0 && review == null, target.name(), target.option());
    }

    @Transactional(readOnly = true)
    public ReviewPageResponse getProductReviews(Long productId, Long userId, int page, int size) {
        if (page < 0 || size < 1 || size > 50) throw new ReviewException("페이지 요청 값을 확인해주세요.");
        var result = reviewRepository.findAllByProductIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(productId, PageRequest.of(page, size));
        List<Long> ids = result.getContent().stream().map(Review::getId).toList();
        Map<Long, List<String>> keys = ids.isEmpty() ? Map.of() : imageRepository.findAllByReviewIdInOrderByReviewIdAscSortOrderAsc(ids).stream()
                .collect(Collectors.groupingBy(i -> i.getReview().getId(), LinkedHashMap::new, Collectors.mapping(ReviewImage::getObjectKey, Collectors.toList())));
        var summary = reviewRepository.summarize(productId);
        return new ReviewPageResponse(result.getContent().stream().map(r -> response(r, keys.getOrDefault(r.getId(), List.of()), userId)).toList(),
                page, size, result.getTotalElements(), result.getTotalPages(), round(summary.getAverageRating()), summary.getReviewCount());
    }

    private Target resolveTarget(OrderItem item) {
        return exchangeItemRepository.findLatestCompletedByOrderItemId(item.getId())
                .map(e -> new Target(e.getTargetProduct(), e.getTargetVariant(), e.getTargetProductName(), e.getTargetOptionSnapshot(), e.getTargetUnitPrice()))
                .orElseGet(() -> new Target(item.getProduct(), item.getVariant(), item.getProductName(), item.getOptionSnapshot(), item.getUnitPrice()));
    }

    private List<String> validateKeys(Long userId, List<String> input) {
        List<String> keys = input == null ? List.of() : input.stream().map(String::trim).toList();
        if (keys.size() > 5) throw new ReviewException("리뷰 이미지는 최대 5장까지 첨부할 수 있습니다.");
        if (keys.stream().anyMatch(String::isBlank) || new HashSet<>(keys).size() != keys.size()) throw new ReviewException("중복되거나 올바르지 않은 이미지가 있습니다.");
        String prefix = "reviews/" + userId + "/";
        if (keys.stream().anyMatch(key -> !key.startsWith(prefix) || key.length() <= prefix.length())) throw new ReviewException("본인이 업로드한 리뷰 이미지만 사용할 수 있습니다.");
        return keys;
    }

    private void saveImages(Review review, List<String> keys) {
        for (int i = 0; i < keys.size(); i++) imageRepository.save(ReviewImage.create(review, keys.get(i), i));
    }

    private ReviewResponse response(Review review, List<String> keys, Long userId) {
        return new ReviewResponse(review.getId(), review.getRating(), review.getContent(), mask(review.getUser().getName()),
                review.getProductNameSnapshot(), review.getOptionSnapshot(), review.getUnitPriceSnapshot(),
                keys.stream().map(storageService::createReadUrl).toList(), review.getCreatedAt(), review.getUpdatedAt(),
                userId != null && review.getUser().getId().equals(userId));
    }
    private Review owned(Long userId, Long id) { requireUser(userId); Review r = reviewRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound); if (!r.getUser().getId().equals(userId)) throw notFound(); return r; }
    private void requireUser(Long id) { if (id == null) throw new AuthenticationException("인증이 필요합니다."); }
    private ReviewException notFound() { return new ReviewException("리뷰 정보를 찾을 수 없습니다."); }
    static String mask(String name) { if (name == null || name.isBlank()) return "구매자"; String n=name.trim(); if(n.length()==1)return n+"*"; if(n.length()==2)return n.charAt(0)+"*"; return n.charAt(0)+"*"+n.charAt(n.length()-1); }
    private double round(Double value) { return Math.round((value == null ? 0 : value) * 10.0) / 10.0; }
    private record Target(com.giftmarket.product.entity.Product product, com.giftmarket.product.entity.ProductVariant variant, String name, String option, long price) {}
}
