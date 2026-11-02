package com.giftmarket.product.search;

import com.giftmarket.product.dto.request.ProductSearchCondition;
import com.giftmarket.product.dto.response.ProductPageResponse;
import com.giftmarket.product.dto.response.ProductSummaryResponse;
import com.giftmarket.product.service.ProductService;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Comparator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProductSearchService {
    private final ProductService products;
    private final EmbeddingService embeddings;
    private final NamedParameterJdbcTemplate jdbc;
    private final boolean enabled;
    private final int dimensions;
    private final int maxCandidates;
    private final String model;

    @Autowired
    public ProductSearchService(ProductService products, EmbeddingService embeddings,
            NamedParameterJdbcTemplate jdbc,
            @Value("${search.semantic.enabled:${SEARCH_SEMANTIC_ENABLED:false}}") boolean enabled,
            @Value("${search.gemini.dimensions:${GEMINI_EMBEDDING_DIMENSIONS:768}}") int dimensions,
            @Value("${search.semantic.max-candidates:${SEARCH_MAX_CANDIDATES:100}}") int maxCandidates,
            @Value("${search.gemini.model:${GEMINI_EMBEDDING_MODEL:gemini-embedding-001}}") String model) {
        this.products = products;
        this.embeddings = embeddings;
        this.jdbc = jdbc;
        this.enabled = enabled;
        this.dimensions = Math.clamp(dimensions, 128, 3072);
        this.maxCandidates = Math.clamp(maxCandidates, 1, 100);
        this.model = model;
    }

    ProductSearchService(ProductService products, EmbeddingService embeddings, NamedParameterJdbcTemplate jdbc,
            boolean enabled, int dimensions, int maxCandidates) {
        this(products, embeddings, jdbc, enabled, dimensions, maxCandidates, ProductEmbeddingIndexer.MODEL);
    }

    public ProductPageResponse search(ProductSearchCondition condition) {
        String keyword = condition.normalizedKeyword();
        if (!enabled || keyword == null || keyword.length() > 100) return products.getProducts(condition);
        ProductSearchCondition candidateCondition = new ProductSearchCondition(condition.categoryIds(),
                condition.giftOccasionId(), null, condition.minPrice(), condition.maxPrice(),
                condition.sort(), condition.excludeSoldOut(), 0, maxCandidates);
        ProductPageResponse candidates = products.getProducts(candidateCondition);
        if (candidates.totalElements() == 0 || candidates.totalElements() > maxCandidates
                || candidates.products().size() != candidates.totalElements()) return products.getProducts(condition);
        try {
            List<Long> ids = candidates.products().stream().map(ProductSummaryResponse::getId).toList();
            Map<Long, String> names = candidates.products().stream().collect(Collectors.toMap(
                    ProductSummaryResponse::getId, ProductSummaryResponse::getName));
            Map<Long, float[]> vectors = readVectors(ids, names);
            if (vectors.size() != ids.size()) return products.getProducts(condition);
            var query = embeddings.embed(keyword, EmbeddingService.Task.RETRIEVAL_QUERY);
            if (query.isEmpty() || query.get().length != dimensions) return products.getProducts(condition);
            // Preserve SQL collation, wildcard and popular seller-name matches.
            Set<Long> legacyMatches = products.getProducts(new ProductSearchCondition(condition.categoryIds(),
                            condition.giftOccasionId(), keyword, condition.minPrice(), condition.maxPrice(),
                            condition.sort(), condition.excludeSoldOut(), 0, maxCandidates)).products().stream()
                            .map(ProductSummaryResponse::getId).collect(Collectors.toSet());
            vectors = readVectors(ids, names);
            if (vectors.size() != ids.size()) return products.getProducts(condition);
            List<RankedProduct> ranked = new ArrayList<>();
            for (ProductSummaryResponse product : candidates.products()) {
                int rank = rank(keyword, product.getName(), query.get(), vectors.get(product.getId()));
                if (rank > 0 || legacyMatches.contains(product.getId())) {
                    ranked.add(new RankedProduct(product, rank));
                }
            }
            // An omitted sort is the only request that can opt into relevance without
            // overriding an explicitly selected latest, price, or popular order.
            if (condition.sort() == null) ranked.sort(Comparator.comparingInt(RankedProduct::rank).reversed());
            List<ProductSummaryResponse> matching = ranked.stream().map(RankedProduct::product).toList();
            long offset = (long) condition.normalizedPage() * condition.normalizedSize();
            int start = (int) Math.min(offset, matching.size());
            int end = Math.min(start + condition.normalizedSize(), matching.size());
            int pages = (int) Math.ceil((double) matching.size() / condition.normalizedSize());
            return new ProductPageResponse(matching.subList(start, end), condition.normalizedPage(),
                    condition.normalizedSize(), matching.size(), pages, condition.normalizedPage() == 0,
                    condition.normalizedPage() >= pages - 1);
        } catch (RuntimeException ex) {
            return products.getProducts(condition);
        }
    }

    private Map<Long, float[]> readVectors(List<Long> ids, Map<Long, String> names) {
        Map<Long, float[]> result = new HashMap<>();
        Map<Long, String> baseTexts = new HashMap<>();
        Map<Long, String> hashes = new HashMap<>();
        jdbc.query("""
                select e.product_id, e.embedding, e.source_hash, p.name, p.summary, p.description, c.name as category_name
                from product_embeddings e
                join products p on p.id = e.product_id
                join categories c on c.id = p.category_id
                where e.product_id in (:ids) and e.model = :model and e.dimensions = :dimensions
                  and p.version = e.product_version
                """, Map.of("ids", ids, "model", model, "dimensions", dimensions), rs -> {
            byte[] bytes = rs.getBytes("embedding");
            if (bytes == null || bytes.length != dimensions * Float.BYTES) return;
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            float[] vector = new float[dimensions];
            for (int i = 0; i < dimensions; i++) {
                vector[i] = buffer.getFloat();
                if (!Float.isFinite(vector[i])) return;
            }
            long id = rs.getLong("product_id");
            if (!rs.getString("name").equals(names.get(id))) return;
            result.put(id, vector);
            hashes.put(id, rs.getString("source_hash"));
            baseTexts.put(id, ProductEmbeddingIndexer.baseText(rs.getString("name"), rs.getString("category_name"),
                    rs.getString("summary"), rs.getString("description")));
        });
        Map<Long, List<String>> occasions = new HashMap<>();
        jdbc.query("""
                select pg.product_id, g.name from product_gift_occasions pg
                join gift_occasions g on g.id = pg.gift_occasion_id
                where pg.product_id in (:ids) and g.active = true order by pg.product_id, g.id
                """, Map.of("ids", ids), rs -> {
                    occasions.computeIfAbsent(rs.getLong("product_id"), ignored -> new ArrayList<>())
                            .add(rs.getString("name"));
                });
        result.keySet().removeIf(id -> !ProductEmbeddingIndexer.hash(ProductEmbeddingIndexer.completeText(
                baseTexts.get(id), occasions.getOrDefault(id, List.of()))).equals(hashes.get(id)));
        return result;
    }

    private record RankedProduct(ProductSummaryResponse product, int rank) {}

    static int rank(String query, String name, float[] queryVector, float[] productVector) {
        String rawQuery = query.trim().toLowerCase(Locale.ROOT);
        String rawName = name.trim().toLowerCase(Locale.ROOT);
        if (rawName.equals(rawQuery)) return 4;
        if (rawName.contains(rawQuery)) return 3;
        String compactQuery = normalize(rawQuery), compactName = normalize(rawName);
        if (compactName.contains(compactQuery)
                || (compactName.length() <= 100 && compactQuery.length() >= 3
                && distance(compactQuery, compactName) <= 1)) return 2;
        double dot = 0, left = 0, right = 0;
        for (int i = 0; i < queryVector.length; i++) {
            dot += (double) queryVector[i] * productVector[i];
            left += (double) queryVector[i] * queryVector[i];
            right += (double) productVector[i] * productVector[i];
        }
        return left > 0 && right > 0 && dot / Math.sqrt(left * right) >= 0.72 ? 1 : 0;
    }

    static String normalize(String text) { return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", ""); }
    private static int distance(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 1) return 2;
        int[] previous = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] current = new int[b.length() + 1]; current[0] = i;
            for (int j = 1; j <= b.length(); j++) current[j] = Math.min(Math.min(current[j - 1] + 1,
                    previous[j] + 1), previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            previous = current;
        }
        return previous[b.length()];
    }
}
