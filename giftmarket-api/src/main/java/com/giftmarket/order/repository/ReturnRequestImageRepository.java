package com.giftmarket.order.repository;

import com.giftmarket.order.entity.ReturnRequestImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReturnRequestImageRepository extends JpaRepository<ReturnRequestImage, Long> {
    List<ReturnRequestImage> findAllByReturnRequestIdOrderBySortOrderAsc(Long returnRequestId);
    List<ReturnRequestImage> findAllByReturnRequestIdInOrderByReturnRequestIdAscSortOrderAsc(
            List<Long> returnRequestIds
    );
}
