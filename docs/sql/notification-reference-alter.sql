-- Existing production notifications table migration.
-- Apply before deploying code that maps Notification.referenceType/referenceId
-- when Hibernate runs with ddl-auto=validate.

ALTER TABLE notifications
    ADD COLUMN reference_type VARCHAR(50) NULL,
    ADD COLUMN reference_id BIGINT NULL;

CREATE INDEX idx_notifications_reference_unread
    ON notifications (
        context,
        type,
        reference_type,
        reference_id,
        read_at
    );
