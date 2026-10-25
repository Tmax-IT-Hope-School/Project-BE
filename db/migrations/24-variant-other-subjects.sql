-- 이미 테이블이 있는 Neon DB 의 변형 문항 표(variants)에 "정답을 무엇으로 확인했는지" 칸을 더하는 SQL 입니다.
-- (docs/VARIANT-SPEC.md 4-1 · 6-4, 이슈 #394, 2026-10-25)
--
-- 무엇이 바뀌나요?
--   variants.verification_method : SYMJA(수학, 검증 코드를 계산) · AI_SOLVE(수학이 아닌 과목, AI 독립 풀이). 이미 있는 줄은 SYMJA
--   수학이 아닌 과목의 변형은 검증 코드가 없어서 verification_code 에 빈 글자('')를 넣습니다. (칸 모양은 그대로)
--
-- 누가 · 언제 실행하나요?
--   변형 문제 표(22-variant-problems.sql) 다음에 실행합니다. 그 표가 없으면 이 파일은 에러가 납니다.
--   23-variant-quiz-sources.sql 과는 서로 상관없어서 순서가 바뀌어도 됩니다.
--   Neon 콘솔 → 브랜치 고르기 → SQL Editor 에 이 파일 전체를 붙여 넣고 Run. dev 먼저, main 은 그다음.
--
-- 여러 번 실행해도 되나요?
--   네. "없으면 더하기(if not exists)" 와 "지우고 다시 만들기(drop ... if exists → add)" 뿐입니다. (MigrationSqlTest 가 검사)
--   begin ~ commit 으로 묶여 있어서, 중간에 에러가 나면 하나도 바뀌지 않습니다.

begin;

alter table variants add column if not exists verification_method varchar(10) default 'SYMJA' not null;

alter table variants drop constraint if exists variants_verification_method_check;
alter table variants
   add constraint variants_verification_method_check
   check (verification_method in ('SYMJA','AI_SOLVE'));

commit;
