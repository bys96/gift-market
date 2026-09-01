package com.giftmarket.order.repository;

import com.giftmarket.order.entity.ExchangeRequestItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExchangeRequestItemRepository extends JpaRepository<ExchangeRequestItem, Long> {
    @Query("""
            select eri from ExchangeRequestItem eri
            join fetch eri.exchangeRequest er
            join fetch eri.targetProduct
            left join fetch eri.targetVariant
            where eri.orderItem.id = :orderItemId and er.status = com.giftmarket.order.entity.ExchangeRequestStatus.COMPLETED
            order by er.completedAt desc, er.id desc
            limit 1
            """)
    Optional<ExchangeRequestItem> findLatestCompletedByOrderItemId(@Param("orderItemId") Long orderItemId);
    @EntityGraph(attributePaths = {"orderItem", "targetProduct", "targetVariant"})
    List<ExchangeRequestItem> findAllByExchangeRequestIdOrderByOrderItemIdAsc(Long exchangeRequestId);

    @EntityGraph(attributePaths = {"orderItem", "targetProduct", "targetVariant"})
    List<ExchangeRequestItem> findAllByExchangeRequestIdInOrderByExchangeRequestIdAscOrderItemIdAsc(
            List<Long> exchangeRequestIds
    );
    @Query("select ei.exchangeRequest.id as exchangeId,min(ei.orderItem.productName) as representativeProductName,count(ei.id) as productTypeCount,sum(ei.quantity) as requestedQuantity from ExchangeRequestItem ei where ei.exchangeRequest.id in :ids group by ei.exchangeRequest.id") List<AdminExchangeItemSummaryProjection> summarizeAdminExchanges(@Param("ids")List<Long>ids);
    @EntityGraph(attributePaths={"orderItem","orderItem.product","orderItem.variant","targetProduct","targetVariant"}) List<ExchangeRequestItem> findAdminByExchangeRequestIdOrderByIdAsc(Long exchangeRequestId);
}
