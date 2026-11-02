package com.giftmarket.product.search;

import java.util.Optional;

public interface EmbeddingService {
    enum Task { RETRIEVAL_DOCUMENT, RETRIEVAL_QUERY }

    Optional<float[]> embed(String text, Task task);
}
