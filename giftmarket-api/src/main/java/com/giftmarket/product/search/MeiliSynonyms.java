package com.giftmarket.product.search;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.core.io.DefaultResourceLoader;

/** Small catalog dictionary; no language model, tokenizer or per-product expansion. */
final class MeiliSynonyms {
    private MeiliSynonyms() { }

    static Map<String, List<String>> load(String location, Map<String, List<String>> overrides) {
        var groups = new Properties();
        try (var input = new DefaultResourceLoader().getResource(location).getInputStream()) {
            byte[] bytes = input.readNBytes(65537);
            if (bytes.length > 65536) throw new IllegalArgumentException("synonyms-file-too-large");
            groups.load(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("synonyms-file-unavailable", ex);
        }
        Map<String, List<String>> result = new TreeMap<>();
        for (String group : groups.stringPropertyNames()) {
            var terms = Arrays.stream(groups.getProperty(group).split(",", -1)).map(MeiliSynonyms::term).distinct().toList();
            if (terms.size() < 2 || terms.size() > 10) throw new IllegalArgumentException("invalid-synonym-group");
            for (String source : terms) {
                var values = new TreeSet<>(result.getOrDefault(source, List.of()));
                terms.stream().filter(value -> !value.equals(source)).forEach(values::add);
                result.put(source, List.copyOf(values));
            }
        }
        // Explicit legacy search.synonyms entries replace individual default entries, including [].
        result.putAll(normalize(overrides));
        return normalize(result);
    }

    static Map<String, List<String>> normalize(Map<String, List<String>> synonyms) {
        Map<String, List<String>> result = new TreeMap<>();
        synonyms.forEach((key, values) -> result.put(term(key), values.stream().map(MeiliSynonyms::term).distinct().sorted().toList()));
        return Collections.unmodifiableMap(result);
    }

    private static String term(String value) {
        String normalized = value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 100) throw new IllegalArgumentException("invalid-synonym-term");
        return normalized;
    }

    static String hash(Map<String, List<String>> synonyms) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(normalize(synonyms).toString().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
