-- 회원탈퇴 기능의 users 스키마 수동 적용 SQL.
-- production ddl-auto=validate 환경에서는 코드 배포보다 먼저 적용한다.
ALTER TABLE users
    ADD COLUMN withdrawn_at DATETIME(6) NULL;
