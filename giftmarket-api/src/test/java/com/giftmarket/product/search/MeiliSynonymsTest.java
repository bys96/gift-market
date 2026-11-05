package com.giftmarket.product.search;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class MeiliSynonymsTest {
    @TempDir Path directory;

    @Test void defaultsAreReciprocalAndDoNotMergeDifferentProducts() {
        var synonyms = new MeiliProperties().effectiveSynonyms();
        assertThat(synonyms.get("이불")).contains("침구");
        assertThat(synonyms.get("블랭킷")).containsExactly("담요");
        assertThat(synonyms.get("침구")).doesNotContain("담요", "베개", "매트리스");
        assertThat(synonyms.get("머그")).doesNotContain("텀블러");
        synonyms.forEach((term, values) -> values.forEach(value -> assertThat(synonyms.get(value)).contains(term)));
    }

    @Test void externalDictionaryReloadsWithoutRestartAndLegacyOverridesArePreserved() throws Exception {
        Path file = directory.resolve("synonyms.properties");
        Files.writeString(file, "blanket=담요,블랭킷\n");
        var config = new MeiliProperties(); config.setSynonymsLocation(file.toUri().toString());
        assertThat(config.effectiveSynonyms()).containsKey("담요").doesNotContainKey("수건");
        Files.writeString(file, "towel=수건,타월\n");
        assertThat(config.effectiveSynonyms()).containsKey("수건").doesNotContainKey("담요");
        config.setSynonyms(Map.of("수건", List.of()));
        assertThat(config.effectiveSynonyms().get("수건")).isEmpty();
    }

    @Test void invalidOrMissingDictionaryIsNotSilentlyAppliedAsEmpty() throws Exception {
        Path file = directory.resolve("synonyms.properties");
        Files.writeString(file, "bad=이불,\n");
        assertThatThrownBy(() -> MeiliSynonyms.load(file.toUri().toString(), Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MeiliSynonyms.load("classpath:missing-synonyms.properties", Map.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void verificationIgnoresTermOrderCaseAndWhitespace() {
        assertThat(MeiliSynonyms.hash(Map.of(" BODY  WASH ", List.of("바디워시", "바디 워시"))))
                .isEqualTo(MeiliSynonyms.hash(Map.of("body wash", List.of("바디 워시", "바디워시"))));
    }
}
