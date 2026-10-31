-- 이미 테이블이 있는 Neon DB 에 "내 학년 · 반"(accounts.school_grade · school_class_no) 칸을 더하는 SQL 입니다. (2026-10-31)
--
-- 왜?
--   반은 FE 기기(localStorage)에만 저장해서, 새 기기에서 로그인하거나 다시 로그인하면 홈 시간표가 안 보였습니다.
--   (설정에서 반을 한 번 더 저장해야 보였음) 이제 서버에도 저장해서 로그인하면 FE 가 받아 갑니다.
--   이미 있는 계정은 모두 비어 있는(null) 채로 시작합니다. FE 가 기기에 남은 반을 한 번 올려 채웁니다.
--
-- 누가 · 언제 실행하나요?
--   accounts 표가 있는 Neon 브랜치에 실행합니다. 다른 번호와는 상관없어서 순서가 바뀌어도 됩니다.
--   (23 · 24 번은 다른 브랜치(feat/variant-*)가 쓰고 있어서 26 번입니다)
--   Neon 콘솔 → 브랜치 고르기 → SQL Editor 에 이 파일 전체를 붙여 넣고 Run.
--   dev 브랜치는 지금 바로, main 브랜치는 이 칸이 들어간 develop → main 배포 **전에** 실행합니다.
--   (이 칸이 없으면 accounts 를 읽는 로그인 · 내 정보 · 학교 정보 API 가 500 입니다)
--
-- 여러 번 실행해도 되나요?
--   네. 칸은 "없으면 더하기(if not exists)" 라서 다시 실행해도 에러가 나지 않습니다. (MigrationSqlTest 가 검사)
--   지우는 데이터는 없습니다.

begin;

alter table accounts add column if not exists school_grade integer;
alter table accounts add column if not exists school_class_no integer;

commit;
