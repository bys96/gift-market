package com.giftmarket.product.search;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class GeminiEmbeddingService implements EmbeddingService {
    private final String model;
    private final RestClient client;
    private final String apiKey;
    private final boolean enabled;
    private final int dimensions;
    private final int cacheLimit;
    private final long cacheTtlMillis;
    private final long minIntervalMillis;
    private long lastRequest;
    private final ReentrantLock requestLock = new ReentrantLock();
    private final Map<String, CacheEntry> cache = new LinkedHashMap<>(16, 0.75f, true);
    private record CacheEntry(float[] vector, long expiresAt) {}
    record Part(String text) {}
    record Content(List<Part> parts) {}
    record Request(String model, Content content, String taskType, int outputDimensionality) {}
    record Vector(List<Float> values) {}
    record Response(Vector embedding) {}

    @Autowired
    public GeminiEmbeddingService(
            @Value("${search.semantic.enabled:${SEARCH_SEMANTIC_ENABLED:false}}") boolean enabled,
            @Value("${search.gemini.api-key:${GEMINI_API_KEY:}}") String apiKey,
            @Value("${search.gemini.model:${GEMINI_EMBEDDING_MODEL:gemini-embedding-001}}") String model,
            @Value("${search.gemini.dimensions:${GEMINI_EMBEDDING_DIMENSIONS:768}}") int dimensions,
            @Value("${search.gemini.connect-timeout-ms:${GEMINI_CONNECT_TIMEOUT_MS:1000}}") int connectTimeout,
            @Value("${search.gemini.read-timeout-ms:${GEMINI_READ_TIMEOUT_MS:2000}}") int readTimeout,
            @Value("${search.gemini.cache-max-entries:${GEMINI_QUERY_CACHE_MAX_ENTRIES:128}}") int cacheLimit,
            @Value("${search.gemini.cache-ttl-seconds:${GEMINI_QUERY_CACHE_TTL_SECONDS:300}}") long cacheTtlSeconds,
            @Value("${search.gemini.min-interval-ms:${GEMINI_MIN_INTERVAL_MS:1000}}") long minIntervalMillis) {
        this(enabled, apiKey, model, dimensions, cacheLimit, cacheTtlSeconds, minIntervalMillis,
                createClient(connectTimeout, readTimeout));
    }

    GeminiEmbeddingService(boolean enabled, String apiKey, int dimensions, int cacheLimit,
            long cacheTtlSeconds, long minIntervalMillis, RestClient client) {
        this(enabled, apiKey, "gemini-embedding-001", dimensions, cacheLimit, cacheTtlSeconds, minIntervalMillis, client);
    }

    GeminiEmbeddingService(boolean enabled, String apiKey, String model, int dimensions, int cacheLimit,
            long cacheTtlSeconds, long minIntervalMillis, RestClient client) {
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.model = model;
        this.dimensions = Math.clamp(dimensions, 128, 3072);
        this.cacheLimit = Math.clamp(cacheLimit, 0, 1024);
        this.cacheTtlMillis = Math.clamp(cacheTtlSeconds, 0, 3600) * 1000;
        this.minIntervalMillis = Math.max(100, minIntervalMillis);
        this.client = client;
    }

    private static RestClient createClient(int connectTimeout, int readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.clamp(connectTimeout, 100, 10000));
        factory.setReadTimeout(Math.clamp(readTimeout, 100, 10000));
        return RestClient.builder().baseUrl("https://generativelanguage.googleapis.com")
                .requestFactory(factory).build();
    }

    @Override
    public Optional<float[]> embed(String text, Task task) {
        if (!enabled || apiKey == null || apiKey.isBlank() || text == null || text.isBlank()
                || task == null || text.length() > 8000 || model == null || !model.matches("[a-zA-Z0-9._-]{1,100}")
                || TransactionSynchronizationManager.isActualTransactionActive()) return Optional.empty();
        if (!requestLock.tryLock()) return Optional.empty();
        try {
            return requestEmbedding(text, task);
        } finally {
            requestLock.unlock();
        }
    }

    private Optional<float[]> requestEmbedding(String text, Task task) {
        long now = System.currentTimeMillis();
        CacheEntry entry = task == Task.RETRIEVAL_QUERY ? cache.get(text) : null;
        if (entry != null && entry.expiresAt() > now) return Optional.of(entry.vector().clone());
        if (entry != null) cache.remove(text);
        if (now - lastRequest < minIntervalMillis) return Optional.empty();
        lastRequest = now;
        try {
            Response response = client.post().uri("/v1beta/models/" + model + ":embedContent")
                    .header("x-goog-api-key", apiKey).contentType(MediaType.APPLICATION_JSON)
                    .body(new Request("models/" + model, new Content(List.of(new Part(text))),
                            task.name(), dimensions))
                    .retrieve().body(Response.class);
            if (response == null || response.embedding() == null || response.embedding().values() == null
                    || response.embedding().values().size() != dimensions) return Optional.empty();
            float[] vector = new float[dimensions];
            for (int i = 0; i < dimensions; i++) {
                Float value = response.embedding().values().get(i);
                if (value == null || !Float.isFinite(value)) return Optional.empty();
                vector[i] = value;
            }
            if (task == Task.RETRIEVAL_QUERY && cacheLimit > 0 && cacheTtlMillis > 0) {
                cache.put(text, new CacheEntry(vector.clone(), now + cacheTtlMillis));
                while (cache.size() > cacheLimit) cache.remove(cache.keySet().iterator().next());
            }
            return Optional.of(vector);
        } catch (RestClientException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
