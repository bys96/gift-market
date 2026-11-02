package com.giftmarket.product.search;

import java.net.SocketTimeoutException;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeminiEmbeddingServiceTest {
    @Test
    void refusesExternalCallInsideTransaction() {
        var builder = RestClient.builder().baseUrl("https://example.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new GeminiEmbeddingService(true, "test", 128, 2, 60, 100, builder.build());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThat(client.embed("query", EmbeddingService.Task.RETRIEVAL_QUERY)).isEmpty();
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        server.verify();
    }

    @Test
    void cachesQueryAndReturnsIndependentVectors() {
        var builder = RestClient.builder().baseUrl("https://example.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        var values = String.join(",", Collections.nCopies(128, "0.5"));
        server.expect(once(), requestTo("https://example.test/v1beta/models/gemini-embedding-001:embedContent"))
                .andRespond(withSuccess("{\"embedding\":{\"values\":[" + values + "]}}", MediaType.APPLICATION_JSON));
        var client = new GeminiEmbeddingService(true, "test", 128, 2, 60, 100, builder.build());
        float[] first = client.embed("query", EmbeddingService.Task.RETRIEVAL_QUERY).orElseThrow();
        first[0] = 99;
        assertThat(client.embed("query", EmbeddingService.Task.RETRIEVAL_QUERY).orElseThrow()[0]).isEqualTo(0.5f);
        server.verify();
    }

    @Test
    void disabledOrMissingKeyMakesNoRequest() {
        var builder = RestClient.builder().baseUrl("https://example.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = builder.build();
        assertThat(new GeminiEmbeddingService(false, "test", 128, 2, 60, 100, client)
                .embed("query", EmbeddingService.Task.RETRIEVAL_QUERY)).isEmpty();
        assertThat(new GeminiEmbeddingService(true, "", 128, 2, 60, 100, client)
                .embed("query", EmbeddingService.Task.RETRIEVAL_QUERY)).isEmpty();
        server.verify();
    }

    @Test
    void rateLimitAndTimeoutReturnEmpty() {
        var builder = RestClient.builder().baseUrl("https://example.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://example.test/v1beta/models/gemini-embedding-001:embedContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("RETRIEVAL_QUERY")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        var client = new GeminiEmbeddingService(true, "test", 128, 2, 60, 100, builder.build());
        assertThat(client.embed("query", EmbeddingService.Task.RETRIEVAL_QUERY)).isEmpty();
        server.verify();

        var timeoutBuilder = RestClient.builder().baseUrl("https://example.test");
        var timeoutServer = MockRestServiceServer.bindTo(timeoutBuilder).build();
        timeoutServer.expect(requestTo("https://example.test/v1beta/models/gemini-embedding-001:embedContent"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("RETRIEVAL_DOCUMENT")))
                .andRespond(request -> { throw new SocketTimeoutException(); });
        var timeoutClient = new GeminiEmbeddingService(true, "test", 128, 2, 60, 100, timeoutBuilder.build());
        assertThat(timeoutClient.embed("document", EmbeddingService.Task.RETRIEVAL_DOCUMENT)).isEmpty();
        timeoutServer.verify();
    }
}
