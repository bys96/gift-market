-- 운영 환경의 Hibernate ddl-auto=validate에 맞춰 코드 배포 전에 수동 적용한다.
ALTER TABLE users
    ADD COLUMN last_login_at DATETIME(6) NULL;
