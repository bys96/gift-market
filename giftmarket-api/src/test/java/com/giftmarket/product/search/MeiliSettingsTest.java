package com.giftmarket.product.search;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MeiliSettingsTest {
    MeiliProperties config = new MeiliProperties();
    MeiliClient client = mock(MeiliClient.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    MeiliIndexService index = new MeiliIndexService(jdbc, client, config);

    @BeforeEach void enableIndexingOnly() { config.setIndexingEnabled(true); }

    @Test void appliesAndVerifiesWithoutReadingDatabaseOrClearingExistingDocuments() {
        when(client.synonyms()).thenReturn(Map.of(), config.effectiveSynonyms());
        var result = index.settings(true);
        assertThat(result.status()).isEqualTo("APPLIED");
        assertThat(result.matches()).isTrue();
        assertThat(result.expectedHash()).isEqualTo(result.actualHash());
        verify(client).updateSynonyms(config.effectiveSynonyms());
        verify(client, times(2)).synonyms();
        verifyNoMoreInteractions(client);
        verifyNoInteractions(jdbc);
        assertThat(config.enabled()).isFalse();
    }

    @Test void unchangedSettingsDoNotEnqueueAnotherIndexingTask() {
        when(client.synonyms()).thenReturn(config.effectiveSynonyms());
        assertThat(index.settings(true).status()).isEqualTo("VERIFIED");
        verify(client).synonyms();
        verifyNoMoreInteractions(client);
    }

    @Test void readOnlyVerificationDistinguishesMissingSynonymsFromUnavailableEngine() {
        when(client.synonyms()).thenReturn(Map.of());
        assertThat(index.settings(false).status()).isEqualTo("MISMATCH");
        verify(client, never()).updateSynonyms(anyMap());
        when(client.synonyms()).thenThrow(new IllegalStateException("unavailable"));
        assertThat(index.settings(false).status()).isEqualTo("RETRY");
    }

    @Test void taskCompletionMustBeFollowedByMatchingReadback() {
        when(client.synonyms()).thenReturn(Map.of());
        assertThat(index.settings(true).status()).isEqualTo("MISMATCH");
    }

    @Test void disabledModeDoesNotContactMeilisearch() {
        config.setIndexingEnabled(false);
        assertThat(index.settings(true).status()).isEqualTo("DISABLED");
        verifyNoInteractions(client, jdbc);
    }
}
