package com.giftmarket.product.search;

import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Bounded HTTP calls; never logs request bodies, credentials or provider error messages. */
@Component
public class MeiliClient {
    private final MeiliProperties config;
    private final RestClient http;
    record Hit(long id) {}
    record SearchResult(List<Hit> hits, Integer totalHits) {}
    record Task(Long taskUid, String status) {}
    static class CapacityExceeded extends RuntimeException { }

    public MeiliClient(MeiliProperties config) {
        this.config = config;
        var timeout = java.time.Duration.ofMillis(config.getTimeoutMs());
        var factory = new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        http = RestClient.builder().baseUrl(config.getUrl()).requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + config.getApiKey()).build();
    }

    private String path() {
        if (!config.getIndex().matches("[a-zA-Z0-9_-]{1,100}")) throw new IllegalStateException("invalid-index");
        return "/indexes/" + config.getIndex();
    }

    public List<Long> search(String query) {
        var response = http.post().uri(path() + "/search").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("q", query.trim().replaceAll("\\s+", " "), "page", 1,
                        "hitsPerPage", config.getMaxMatches() + 1, "attributesToRetrieve", List.of("id"),
                        "matchingStrategy", "all"))
                .retrieve().body(SearchResult.class);
        if (response != null && response.totalHits() != null && response.totalHits() > config.getMaxMatches())
            throw new CapacityExceeded();
        if (response == null || response.hits() == null || response.totalHits() == null
                || response.hits().size() != response.totalHits()) throw new IllegalStateException("incomplete-hits");
        List<Long> ids = response.hits().stream().map(Hit::id).toList();
        if (ids.stream().distinct().count() != ids.size()) throw new IllegalStateException("duplicate-hits");
        return ids;
    }

    public void configure() {
        // Settings creates an absent index. Explicit primaryKey on upsert prevents inference ambiguity.
        await(http.patch().uri(path() + "/settings").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("searchableAttributes", List.of("name", "nameCompact", "category", "brand", "occasions", "summary", "description"),
                        "displayedAttributes", List.of("id"),
                        "pagination", Map.of("maxTotalHits", config.getMaxMatches() + 1),
                        "typoTolerance", Map.of("enabled", true, "minWordSizeForTypos", Map.of("oneTypo", 2, "twoTypos", 6)),
                        "synonyms", config.getSynonyms()))
                .retrieve().body(Task.class));
    }

    public void upsert(List<Map<String, Object>> documents) {
        if (documents.isEmpty()) return;
        await(http.post().uri(path() + "/documents?primaryKey=id").contentType(MediaType.APPLICATION_JSON)
                .body(documents).retrieve().body(Task.class));
    }

    public void delete(List<Long> ids) {
        if (ids.isEmpty()) return;
        await(http.post().uri(path() + "/documents/delete-batch").contentType(MediaType.APPLICATION_JSON)
                .body(ids).retrieve().body(Task.class));
    }

    public void clear() { await(http.delete().uri(path() + "/documents").retrieve().body(Task.class)); }

    private void await(Task task) {
        if (task == null || task.taskUid() == null) throw new IllegalStateException("missing-task");
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (System.nanoTime() < deadline) {
            var current = http.get().uri("/tasks/" + task.taskUid()).retrieve().body(Task.class);
            if (current != null && "succeeded".equals(current.status())) return;
            if (current != null && ("failed".equals(current.status()) || "canceled".equals(current.status())))
                throw new IllegalStateException("index-task-failed");
            try { Thread.sleep(100); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException("interrupted"); }
        }
        throw new IllegalStateException("index-task-pending");
    }
}
