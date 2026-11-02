-- 수동 적용용 참고 DDL. 적용 전 백업 및 운영 환경 검토가 필요하다.
-- 모델/차원별로 별도 벡터를 보관한다. source_hash는 원문 변경을 감지한다.
CREATE TABLE product_embeddings (
    product_id BIGINT NOT NULL,
    model VARCHAR(100) NOT NULL,
    dimensions INT NOT NULL,
    source_hash CHAR(64) NOT NULL,
    product_version BIGINT NOT NULL,
    embedding LONGBLOB NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (product_id, model, dimensions),
    CONSTRAINT fk_product_embeddings_product FOREIGN KEY (product_id) REFERENCES products(id)
);
