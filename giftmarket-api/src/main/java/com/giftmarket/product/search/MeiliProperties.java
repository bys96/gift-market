package com.giftmarket.product.search;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "search")
public class MeiliProperties {
    private String engine = "mysql";
    private String url = "http://meilisearch:7700";
    private String apiKey = "";
    private String index = "giftmarket_products";
    private int timeoutMs = 800;
    private int maxMatches = 1000;
    private Map<String, List<String>> synonyms = new LinkedHashMap<>();
    public boolean enabled() { return "meilisearch".equals(engine); }
    public String getEngine() { return engine; }
    public void setEngine(String value) { engine = value; }
    public String getUrl() { return url; }
    public void setUrl(String value) { url = value; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String value) { apiKey = value; }
    public String getIndex() { return index; }
    public void setIndex(String value) { index = value; }
    public int getTimeoutMs() { return Math.clamp(timeoutMs, 100, 3000); }
    public void setTimeoutMs(int value) { timeoutMs = value; }
    public int getMaxMatches() { return Math.clamp(maxMatches, 1, 2000); }
    public void setMaxMatches(int value) { maxMatches = value; }
    public Map<String, List<String>> getSynonyms() { return synonyms; }
    public void setSynonyms(Map<String, List<String>> value) { synonyms = value; }
}
