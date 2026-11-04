# MySQL keyword search verification

Gemini embedding services, reindex endpoint, vector scoring and semantic settings have been removed. ProductSearchService delegates directly to the existing ProductService database query. Product API URLs, DTOs, autocomplete, filters, ordering and pagination are unchanged. No frontend, security, deployment or product mutation code was changed.

Keyword matching uses the existing case-insensitive SQL LIKE name query (with the existing popular-sort query semantics). Exact names and partial names work; semantic synonyms and the former semantic-path typo/whitespace ranking do not. There was no independent SQL typo implementation to preserve.

Validation: ProductSearchServiceTest covers delegation of keyword/filter/sort/page conditions without an embedding provider. KeywordSearchIntegrationTest exercises exact/partial/no-result searches, category/price filters, ordering, pages and autocomplete using isolated H2 in MySQL mode. Existing product detail/media, autocomplete and full backend tests provide regression coverage. This is not a production MySQL data test.

No SQL needs to be executed. Production product data and ddl-auto are unchanged. The unused product_embeddings table must remain during this change; it can be removed separately after successful deployment and verification. No automatic schema operation or DROP script is added. No embedding Entity or Repository existed: the removed indexer accessed the table through JDBC.

GEMINI_API_KEY and SEARCH_SEMANTIC_ENABLED/SEARCH_REINDEX_ENABLED and related search.gemini/search.semantic settings are no longer consumed. External deployment secrets/settings were not accessed or changed; obsolete values can be removed separately. Shared Spring HTTP/JDBC and jsoup dependencies remain because other features use them.
