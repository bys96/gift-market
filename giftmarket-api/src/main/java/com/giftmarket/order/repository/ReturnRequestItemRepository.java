package com.giftmarket.order.repository;

import com.giftmarket.order.entity.ReturnRequestItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    @Query("select ri.returnRequest.id as returnId,min(ri.orderItem.productName) as representativeProductName,count(ri.id) as productTypeCount,sum(ri.quantity) as requestedQuantity from ReturnRequestItem ri where ri.returnRequest.id in :ids group by ri.returnRequest.id")
    List<AdminReturnItemSummaryProjection> summarizeAdminReturns(@Param("ids") List<Long> ids);
    @EntityGraph(attributePaths={"orderItem","orderItem.product"}) List<ReturnRequestItem> findAdminByReturnRequestIdOrderByIdAsc(Long returnRequestId);
}
