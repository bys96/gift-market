package com.giftmarket.review.repository;

import com.giftmarket.review.entity.ReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReviewImageRepository extends JpaRepository<ReviewImage, Long> {
    List<ReviewImage> findAllByReviewIdInOrderByReviewIdAscSortOrderAsc(Collection<Long> reviewIds);
    void deleteAllByReviewId(Long reviewId);
}
