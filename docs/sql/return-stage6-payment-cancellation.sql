-- Return 6: PaymentCancellation과 ReturnRequest 연결
-- 개발 DB 수동 적용용. 운영 반영 전 versioned migration으로 전환한다.

ALTER TABLE payment_cancellations
    ADD COLUMN return_request_id BIGINT NULL;

ALTER TABLE payment_cancellations
    ADD CONSTRAINT uk_payment_cancellations_return_request UNIQUE (return_request_id),
    ADD CONSTRAINT fk_payment_cancellations_return_request
        FOREIGN KEY (return_request_id) REFERENCES return_requests (id);
