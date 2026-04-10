-- postgres-team 컨테이너 첫 기동(빈 볼륨) 시에만 실행. psql -f 로 실행되므로 CRLF 이슈가 없다.
-- 기본값은 .env.example 과 동일. 비밀번호를 바꾸면 볼륨 초기화 후 재기동하거나 ALTER ROLE 로 맞춘다.
CREATE USER team_app WITH PASSWORD 'team_app';
CREATE DATABASE team_db OWNER team_app;
