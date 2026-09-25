-- SUPER_ADMIN 및 관리자 권한 변경 이력 운영 적용용 DDL.
-- 운영환경에서는 Backend 배포 전에 적용하고, 대상 DB의 백업과 users.role 현재 정의를 확인한다.
-- 이 파일은 자동 migration이 아니며 이미 반영된 스키마에 중복 적용하지 않는다.

-- users.role이 VARCHAR 계열이면 길이 20으로 SUPER_ADMIN을 이미 저장할 수 있으므로 ALTER하지 않는다.
-- Hibernate MySQL Dialect가 생성한 ENUM인 환경에서만 허용 값을 확장한다.
SELECT COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'users'
  AND COLUMN_NAME = 'role';

SET @users_role_data_type = (
    SELECT DATA_TYPE
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'role'
);

SET @users_role_alter = IF(
    @users_role_data_type = 'enum',
    'ALTER TABLE users MODIFY COLUMN role ENUM(''USER'', ''SELLER'', ''ADMIN'', ''SUPER_ADMIN'') NOT NULL',
    'SELECT ''users.role is not ENUM; role ALTER skipped'' AS migration_message'
);

PREPARE users_role_statement FROM @users_role_alter;
EXECUTE users_role_statement;
DEALLOCATE PREPARE users_role_statement;

CREATE TABLE admin_role_change_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operator_user_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    previous_role VARCHAR(20) NOT NULL,
    changed_role VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_admin_role_logs_operator
        FOREIGN KEY (operator_user_id) REFERENCES users (id),
    CONSTRAINT fk_admin_role_logs_target
        FOREIGN KEY (target_user_id) REFERENCES users (id),
    INDEX idx_admin_role_logs_operator_created_at (operator_user_id, created_at),
    INDEX idx_admin_role_logs_target_created_at (target_user_id, created_at)
);

-- SUPER_ADMIN 지정은 애플리케이션 API가 아니라 운영 절차로만 수행한다.
-- 실제 대상 ID를 확인한 뒤 별도 승인된 운영 SQL로 users.role을 SUPER_ADMIN으로 변경한다.
