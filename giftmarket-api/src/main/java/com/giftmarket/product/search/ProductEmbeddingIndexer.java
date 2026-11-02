package com.giftmarket.product.search;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicitly invoked, bounded indexing; provider calls occur outside database transactions. */
@Service
public class ProductEmbeddingIndexer {
    static final String MODEL = "gemini-embedding-001";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final EmbeddingService embeddings;
    private final boolean enabled;
    private final int dimensions;
    private final String model;
    private final AtomicBoolean running = new AtomicBoolean();

    public record Result(long nextCursor, int indexed, int unchanged, Long retryProductId, String status) {}
    record Source(long id, String text, String hash, long version) {}

    @Autowired
    public ProductEmbeddingIndexer(JdbcTemplate jdbc, TransactionTemplate transactions,
            EmbeddingService embeddings,
            @Value("${search.reindex.enabled:${SEARCH_REINDEX_ENABLED:false}}") boolean enabled,
            @Value("${search.gemini.dimensions:${GEMINI_EMBEDDING_DIMENSIONS:768}}") int dimensions,
            @Value("${search.gemini.model:${GEMINI_EMBEDDING_MODEL:gemini-embedding-001}}") String model) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.embeddings = embeddings;
        this.enabled = enabled;
        this.dimensions = Math.clamp(dimensions, 128, 3072);
        this.model = model;
    }

    ProductEmbeddingIndexer(JdbcTemplate jdbc, TransactionTemplate transactions, EmbeddingService embeddings,
            boolean enabled, int dimensions) { this(jdbc, transactions, embeddings, enabled, dimensions, MODEL); }

    public Result reindex(long afterId, int requestedLimit) {
        if (!enabled) return new Result(afterId, 0, 0, null, "DISABLED");
        if (!running.compareAndSet(false, true)) return new Result(afterId, 0, 0, null, "BUSY");
        try {
            int limit = Math.clamp(requestedLimit, 1, 10);
            List<Long> ids = jdbc.queryForList("""
                    select p.id from products p join sellers s on s.id = p.seller_id
                    where p.id > ? and p.deleted_at is null and p.admin_hidden = false
                      and p.status in ('ON_SALE', 'SOLD_OUT') and s.status = 'ACTIVE'
                    order by p.id limit ?
                    """, Long.class, Math.max(0, afterId), limit);
            long cursor = Math.max(0, afterId);
            int indexed = 0, unchanged = 0;
            for (Long id : ids) {
                Source source = source(id, false);
                if (source == null) { cursor = id; continue; }
                Integer existing = jdbc.queryForObject("""
                        select count(*) from product_embeddings
                        where product_id = ? and model = ? and dimensions = ? and source_hash = ?
                        """, Integer.class, id, model, dimensions, source.hash());
                if (existing != null && existing > 0) {
                    Boolean refreshed = transactions.execute(status -> {
                        Source current = source(id, true);
                        if (current == null || !current.hash().equals(source.hash())) return false;
                        return jdbc.update("""
                                update product_embeddings set updated_at = current_timestamp(6), product_version = ?
                                where product_id = ? and model = ? and dimensions = ? and source_hash = ?
                                """, current.version(), id, model, dimensions, source.hash()) == 1;
                    });
                    if (!Boolean.TRUE.equals(refreshed)) return new Result(cursor, indexed, unchanged, id, "RETRY");
                    unchanged++; cursor = id; continue;
                }
                Optional<float[]> vector;
                try {
                    vector = embeddings.embed(source.text(), EmbeddingService.Task.RETRIEVAL_DOCUMENT);
                } catch (RuntimeException ex) {
                    return new Result(cursor, indexed, unchanged, id, "RETRY");
                }
                if (vector.isEmpty() || vector.get().length != dimensions) {
                    return new Result(cursor, indexed, unchanged, id, "RETRY");
                }
                byte[] bytes = encode(vector.get());
                Boolean saved = transactions.execute(status -> {
                    Source current = source(id, true);
                    if (current == null || !current.hash().equals(source.hash())) return false;
                    jdbc.update("""
                            insert into product_embeddings (product_id, model, dimensions, source_hash, product_version, embedding, updated_at)
                            values (?, ?, ?, ?, ?, ?, current_timestamp(6))
                            on duplicate key update source_hash = values(source_hash),
                                product_version = values(product_version), embedding = values(embedding), updated_at = values(updated_at)
                            """, id, model, dimensions, source.hash(), current.version(), bytes);
                    return true;
                });
                if (!Boolean.TRUE.equals(saved)) return new Result(cursor, indexed, unchanged, id, "RETRY");
                indexed++;
                cursor = id;
            }
            return new Result(cursor, indexed, unchanged, null, ids.size() < limit ? "COMPLETE" : "MORE");
        } finally {
            running.set(false);
        }
    }

    private Source source(long id, boolean lock) {
        List<Source> rows = jdbc.query("""
                select p.name, p.summary, p.description, p.version, c.name as category_name
                from products p join categories c on c.id = p.category_id
                join sellers s on s.id = p.seller_id
                where p.id = ? and p.deleted_at is null and p.admin_hidden = false
                  and p.status in ('ON_SALE', 'SOLD_OUT') and s.status = 'ACTIVE'
                """ + (lock ? " for update" : ""),
                (rs, n) -> new Source(id, baseText(rs.getString("name"), rs.getString("category_name"),
                        rs.getString("summary"), rs.getString("description")), "", rs.getLong("version")), id);
        if (rows.isEmpty()) return null;
        List<String> occasions = jdbc.queryForList("""
                select g.name from product_gift_occasions pg join gift_occasions g on g.id = pg.gift_occasion_id
                where pg.product_id = ? and g.active = true order by g.id
                """, String.class, id);
        String text = completeText(rows.getFirst().text(), occasions);
        return new Source(id, text, hash(text), rows.getFirst().version());
    }

    private static String safe(String value) { return value == null ? "" : value; }
    static String baseText(String name, String category, String summary, String description) {
        return String.join(" ", safe(name), safe(category), safe(summary), descriptionText(description));
    }
    static String completeText(String base, List<String> occasions) {
        String text = (base + " " + String.join(" ", occasions)).trim();
        return text.length() > 6000 ? text.substring(0, 6000) : text;
    }
    private static String descriptionText(String html) {
        String text = Jsoup.parse(safe(html)).text();
        return text.length() > 4000 ? text.substring(0, 4000) : text;
    }
    static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    static byte[] encode(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES);
        for (float value : vector) buffer.putFloat(value);
        return buffer.array();
    }
}
