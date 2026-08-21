package com.giftmarket.order.repository;

import com.giftmarket.order.entity.ReturnRequestItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReturnRequestItemRepository
        extends JpaRepository<ReturnRequestItem, Long> {

    @EntityGraph(attributePaths = "orderItem")
    List<ReturnRequestItem> findAllByReturnRequestIdOrderByIdAsc(
            Long returnRequestId
    );

    @EntityGraph(attributePaths = "orderItem")
    List<ReturnRequestItem> findAllByReturnRequestIdInOrderByReturnRequestIdAscOrderItemIdAsc(
            List<Long> returnRequestIds
    );
}