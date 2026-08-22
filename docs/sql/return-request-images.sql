CREATE TABLE return_request_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    return_request_id BIGINT NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_return_request_images_request
        FOREIGN KEY (return_request_id) REFERENCES return_requests (id),
    CONSTRAINT uk_return_request_images_request_object
        UNIQUE (return_request_id, object_key),
    INDEX idx_return_request_images_request_sort (return_request_id, sort_order)
);

-- 기존 ReturnRequest는 backfill이 필요하지 않으며 조회 시 images=[]로 응답한다.
