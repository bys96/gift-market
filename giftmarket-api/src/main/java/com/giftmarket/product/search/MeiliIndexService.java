package com.giftmarket.product.search;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import org.jsoup.Jsoup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Single writer per API instance. Database reads and HTTP calls never share a transaction. */
@Service
public class MeiliIndexService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MeiliIndexService.class);
    private final JdbcTemplate jdbc;
    private final MeiliClient client;
    private final MeiliProperties config;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicLong revision = new AtomicLong();
    private final java.util.concurrent.atomic.AtomicBoolean rebuild = new java.util.concurrent.atomic.AtomicBoolean();
    private final Set<Long> deleted = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile boolean ready;
    private volatile long completedRevision = -1;
    private boolean configured;
    private boolean needsClear = true;
    private long cursor;
    private long scanRevision;
    private long completedAt;
    private final Map<Long, String> previous = new HashMap<>();
    public record Result(long nextCursor, String status, int processed) {}

    public MeiliIndexService(JdbcTemplate jdbc, MeiliClient client, MeiliProperties config) {
        this.jdbc = jdbc; this.client = client; this.config = config;
    }
    public boolean ready() { return config.enabled() && ready && completedRevision == revision.get(); }
    public long revision() { return revision.get(); }
    public void changed() { revision.incrementAndGet(); ready = false; }
    public void rebuild() { rebuild.set(true); changed(); }
    public void deleted(long id) { deleted.add(id); changed(); }

    /** Restartable whole-catalog scan also catches bulk SQL/relationship changes and missed events. */
    public void synchronize() {
        if (!config.enabled()) return;
        if (ready && System.currentTimeMillis() - completedAt < 60000) return;
        reindex(false);
    }

    public Result reindex(boolean restart) {
        if (!config.enabled()) return new Result(0, "DISABLED", 0);
        if (!lock.tryLock()) return new Result(cursor, "BUSY", 0);
        try {
            ready = false;
            if (restart || rebuild.getAndSet(false)) { cursor = 0; configured = false; needsClear = true; previous.clear(); }
            if (cursor == 0) scanRevision = revision.get();
            if (!configured) { client.configure(); configured = true; }
            if (needsClear) { client.clear(); needsClear = false; }
            var deletions = new ArrayList<>(deleted);
            client.delete(deletions);
            deletions.forEach(deleted::remove);
            List<Long> ids = jdbc.queryForList("select id from products where id > ? order by id limit 50", Long.class, cursor);
            List<Map<String, Object>> documents = new ArrayList<>();
            List<Long> removed = new ArrayList<>();
            Map<Long, String> updates = new HashMap<>();
            for (Long id : ids) {
                Map<String, Object> document = document(id);
                if (document == null) removed.add(id);
                else if (!fingerprint(document).equals(previous.get(id))) { documents.add(document); updates.put(id, fingerprint(document)); }
            }
            client.upsert(documents);
            client.delete(removed);
            // Cache is only an optimization; cap memory and retry unchanged IDs after process restart.
            if (previous.size() > 10000) previous.clear();
            previous.putAll(updates);
            removed.forEach(previous::remove);
            if (!ids.isEmpty()) cursor = ids.getLast();
            if (ids.size() < 50) {
                cursor = 0;
                ready = scanRevision == revision.get();
                completedRevision = scanRevision;
                completedAt = System.currentTimeMillis();
                return new Result(0, ready ? "COMPLETE" : "MORE", ids.size());
            }
            return new Result(cursor, "MORE", ids.size());
        } catch (RuntimeException ex) {
            configured = false;
            log.warn("Search indexing failed cursor={} type={}", cursor, ex.getClass().getSimpleName());
            return new Result(cursor, "RETRY", 0);
        } finally { lock.unlock(); }
    }

    private Map<String, Object> document(long id) {
        var rows = jdbc.query("""
                select p.id, p.name, p.brand_name, p.summary, p.description, c.name category_name
                from products p join categories c on c.id=p.category_id join sellers s on s.id=p.seller_id
                where p.id=? and p.deleted_at is null and p.admin_hidden=false
                  and p.status in ('ON_SALE','SOLD_OUT') and s.status='ACTIVE'
                """, (rs, n) -> {
                    Map<String, Object> doc = new LinkedHashMap<>();
                    doc.put("id", id); doc.put("name", text(rs.getString("name"), 200));
                    doc.put("nameCompact", compact(rs.getString("name")));
                    doc.put("brand", text(rs.getString("brand_name"), 100));
                    doc.put("summary", text(rs.getString("summary"), 500));
                    doc.put("description", text(Jsoup.parse(text(rs.getString("description"), 200000)).text(), 12000));
                    doc.put("category", text(rs.getString("category_name"), 200));
                    return doc;
                }, id);
        if (rows.isEmpty()) return null;
        var doc = rows.getFirst();
        doc.put("occasions", jdbc.queryForList("""
                select g.name from product_gift_occasions pg join gift_occasions g on g.id=pg.gift_occasion_id
                where pg.product_id=? and g.active=true order by g.id
                """, String.class, id));
        return doc;
    }
    static String compact(String value) { return value == null ? "" : value.replaceAll("\\s+", ""); }
    private static String fingerprint(Map<String, Object> document) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(document.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static String text(String value, int limit) { return value == null ? "" : value.substring(0, Math.min(value.length(), limit)); }
}
