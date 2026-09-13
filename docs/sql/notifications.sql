-- Notification domain reference DDL.
-- Local development uses Hibernate ddl-auto:update. Do not run this file against a schema
-- where Hibernate has already created the same table.
-- Production uses ddl-auto:validate, so apply this DDL before deploying Notification code.

CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    context VARCHAR(20) NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(100) NOT NULL,
    message VARCHAR(500) NOT NULL,
    target_url VARCHAR(500) NULL,
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_notifications_user_context_created_at (
        user_id,
        context,
        created_at
    ),
    INDEX idx_notifications_user_context_read_at (
        user_id,
        context,
        read_at
    )
);
