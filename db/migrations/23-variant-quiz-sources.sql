-- 이미 테이블이 있는 Neon DB 의 "변형 문제" 원본 표(source_problems)에 확인 문제 연결용 칸 4개를 더하는 SQL 입니다.
-- (docs/VARIANT-SPEC.md 4-1 · 5-1, 이슈 #394, 2026-10-24)
--
-- 무엇이 바뀌나요?
--   source_problems.origin        : 어디서 들어온 원본인지 (MANUAL 직접 등록 · SEED 시드 · QUIZ 확인 문제). 이미 있는 줄은 MANUAL
--   source_problems.subject       : 과목 이름 (예: 공통국어1). 이미 있는 줄은 비어 있음(= 고1 공통수학1)
--   source_problems.standard_code : 성취기준 코드 (수학이 아닌 과목만)
--   source_problems.passage_text  : 문제가 기대는 지문
--
-- 누가 · 언제 실행하나요?
--   변형 문제 표(19-variant-problems.sql, 22 번으로 옮길 예정) 다음에 실행합니다. 그 표가 없으면 이 파일은 에러가 납니다.
--   (번호: develop 에 19-quiz-results · 20-weekly-picks · 21-card-subject 가 있고, 변형 표는 22 로 옮길 예정이라 23 번입니다)
--   Neon 콘솔 → 브랜치 고르기 → SQL Editor 에 이 파일 전체를 붙여 넣고 Run. dev 먼저, main 은 그다음.
--
-- 여러 번 실행해도 되나요?
--   네. "없으면 더하기(if not exists)" 와 "지우고 다시 만들기(drop ... if exists → add)" 뿐입니다. (MigrationSqlTest 가 검사)
--   begin ~ commit 으로 묶여 있어서, 중간에 에러가 나면 하나도 바뀌지 않습니다.

begin;

alter table source_problems add column if not exists origin varchar(10) default 'MANUAL' not null;
alter table source_problems add column if not exists subject varchar(30);
alter table source_problems add column if not exists standard_code varchar(30);
alter table source_problems add column if not exists passage_text varchar(2000);

alter table source_problems drop constraint if exists source_problems_origin_check;
alter table source_problems
   add constraint source_problems_origin_check
   check (origin in ('MANUAL','SEED','QUIZ'));

commit;
