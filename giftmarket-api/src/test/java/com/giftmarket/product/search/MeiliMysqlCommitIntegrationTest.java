package com.giftmarket.product.search;

import org.springframework.test.context.TestPropertySource;

/** The same after-commit/rollback guarantees apply while searches still use MySQL. */
@TestPropertySource(properties = {"search.engine=mysql", "search.indexing-enabled=true"})
class MeiliMysqlCommitIntegrationTest extends MeiliCommitIntegrationTest {
}
