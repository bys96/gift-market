-- 회원탈퇴 기능 배포 전 users 스키마 선 적용 SQL
-- production ddl-auto=validate 환경에서는 이 SQL을 먼저 실행한 뒤 Backend를 배포한다.
ALTER TABLE users
    ADD COLUMN withdrawn_at DATETIME(6) NULL;
