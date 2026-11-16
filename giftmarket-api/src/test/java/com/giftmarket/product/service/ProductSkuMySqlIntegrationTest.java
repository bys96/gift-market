package com.giftmarket.product.service;

import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Tag("mysql-container")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProductSkuMySqlIntegrationTest extends ProductStockIntegrationTest {
    @org.junit.jupiter.api.Test
    void reconciliationSqlFindsPartialMappingsWithoutChangingInventory() throws Exception {
        Long id = registerMultiGroup(2);
        var variant = variantRepository.findAllByProductIdOrderByIdAsc(id).getFirst();
        var mappings = mappingRepository.findAllByVariantIdIn(java.util.List.of(variant.getId()));
        String sql = java.nio.file.Files.readString(java.nio.file.Path.of("../docs/sql/product-sku-reconciliation.sql"));
        String query = sql.substring(sql.indexOf("WITH expected"), sql.indexOf(";", sql.indexOf("WITH expected")));
        org.assertj.core.api.Assertions.assertThat(entityManager.createNativeQuery(query).getResultList()).isEmpty();
        mappingRepository.delete(mappings.getFirst());
        entityManager.flush();
        java.util.List<?> rows = entityManager.createNativeQuery(query).getResultList();
        org.assertj.core.api.Assertions.assertThat(rows).hasSize(1);
        Object[] row = (Object[]) rows.getFirst();
        org.assertj.core.api.Assertions.assertThat(((Number) row[0]).longValue()).isEqualTo(variant.getId());
        org.assertj.core.api.Assertions.assertThat(variant.getStockQuantity()).isEqualTo(7);
    }

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }
}
