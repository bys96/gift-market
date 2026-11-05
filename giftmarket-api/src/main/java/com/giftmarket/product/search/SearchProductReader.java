package com.giftmarket.product.search;

import com.giftmarket.product.dto.request.ProductSearchCondition;
import com.giftmarket.product.dto.response.*;
import com.giftmarket.product.entity.*;
import com.giftmarket.product.repository.*;
import com.giftmarket.product.service.GiftOccasionService;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies all DB constraints to the COMPLETE match set before counting, sorting or slicing. */
@Service
public class SearchProductReader {
    private final ProductRepository products;
    private final GiftOccasionService occasions;
    private final NamedParameterJdbcTemplate jdbc;
    public SearchProductReader(ProductRepository products, GiftOccasionService occasions, NamedParameterJdbcTemplate jdbc) {
        this.products = products; this.occasions = occasions; this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public ProductPageResponse read(List<Long> ids, ProductSearchCondition condition, boolean suggestions) {
        List<Product> found = new ArrayList<>();
        if (!ids.isEmpty()) {
            Specification<Product> spec = Specification.where(ProductSpecifications.notDeleted())
                    .and(ProductSpecifications.notAdminHidden()).and(ProductSpecifications.activeSeller())
                    .and(ProductSpecifications.statusIn(condition.normalizedExcludeSoldOut()
                            ? List.of(ProductStatus.ON_SALE) : List.of(ProductStatus.ON_SALE, ProductStatus.SOLD_OUT)))
                    .and(ProductSpecifications.categoryIdIn(condition.normalizedCategoryIds()))
                    .and(ProductSpecifications.giftOccasionId(condition.normalizedGiftOccasionId()))
                    .and(ProductSpecifications.priceAtLeast(condition.minPrice()))
                    .and(ProductSpecifications.priceAtMost(condition.maxPrice()))
                    .and((root, query, cb) -> root.get("id").in(ids));
            if (suggestions) spec = spec.and((root, query, cb) -> cb.greaterThan(root.get("stockQuantity"), 0));
            Sort sort = switch (condition.normalizedSort()) {
                case PRICE_ASC -> Sort.by("price").and(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
                case PRICE_DESC -> Sort.by(Sort.Direction.DESC, "price", "createdAt", "id");
                default -> Sort.by(Sort.Direction.DESC, "createdAt", "id");
            };
            found.addAll(products.findAll(spec, sort));
            if (condition.sort() == ProductSort.POPULAR) {
                List<Long> order = jdbc.queryForList("""
                        select p.id from products p
                        left join (select oi.product_id, sum(oi.quantity-oi.canceled_quantity-oi.returned_quantity) sales_quantity
                          from order_items oi join orders o on o.id=oi.order_id join seller_orders so on so.id=oi.seller_order_id
                          where o.status='PAID' and so.status<>'CANCELLED' group by oi.product_id) sales on sales.product_id=p.id
                        left join (select product_id,count(*) wishlist_count from wishlist_items group by product_id) wishes on wishes.product_id=p.id
                        where p.id in (:ids)
                        order by coalesce(sales.sales_quantity,0) desc,
                          case when coalesce(sales.sales_quantity,0)=0 then coalesce(wishes.wishlist_count,0) else 0 end desc,
                          p.created_at desc,p.id desc
                        """, Map.of("ids", ids), Long.class);
                Map<Long, Integer> positions = positions(order);
                found.sort(Comparator.comparingInt(p -> positions.get(p.getId())));
            } else if (condition.sort() == null) {
                Map<Long, Integer> positions = positions(ids);
                String query = MeiliIndexService.compact(condition.normalizedKeyword()).toLowerCase(Locale.ROOT);
                found.sort(Comparator.<Product>comparingInt(p -> {
                    String name = MeiliIndexService.compact(p.getName()).toLowerCase(Locale.ROOT);
                    return name.equals(query) ? 0 : name.contains(query) ? 1 : 2;
                }).thenComparingInt(p -> positions.get(p.getId())));
            }
        }
        int total = found.size();
        int start = (int) Math.min((long) condition.normalizedPage() * condition.normalizedSize(), total);
        var page = found.subList(start, Math.min(start + condition.normalizedSize(), total));
        var gifts = occasions.getActiveProductGiftOccasions(page.stream().map(Product::getId).toList());
        var rows = page.stream().map(p -> ProductSummaryResponse.from(p, gifts.getOrDefault(p.getId(), List.of()))).toList();
        int pages = (int) Math.ceil((double) total / condition.normalizedSize());
        return new ProductPageResponse(rows, condition.normalizedPage(), condition.normalizedSize(), total, pages,
                condition.normalizedPage() == 0, condition.normalizedPage() >= pages - 1);
    }
    private static Map<Long, Integer> positions(List<Long> ids) {
        Map<Long, Integer> map = new HashMap<>();
        for (int i=0; i<ids.size(); i++) map.put(ids.get(i), i);
        return map;
    }
}
