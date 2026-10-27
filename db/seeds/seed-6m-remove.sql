-- 발표용 가상 데이터(seed-6m.sql 로 넣은 것)를 모두 지웁니다. 사람이 직접 만든 기록은 남습니다.
-- 단, 가상 카드로 잰 세션 · 미루기 표시는 같이 지워집니다.
begin;

-- 가상 세션 · 가상 카드로 잰 세션에 딸린 줄
delete from session_pauses where study_session_id in (select id from study_sessions where id in (select row_id from seed_6m_rows where tbl = 'study_sessions') or card_id in (select row_id from seed_6m_rows where tbl = 'cards'));
delete from session_good_reasons where study_session_id in (select id from study_sessions where id in (select row_id from seed_6m_rows where tbl = 'study_sessions') or card_id in (select row_id from seed_6m_rows where tbl = 'cards'));
delete from session_checked_items where study_session_id in (select id from study_sessions where id in (select row_id from seed_6m_rows where tbl = 'study_sessions') or card_id in (select row_id from seed_6m_rows where tbl = 'cards'));
delete from session_memos where study_session_id in (select id from study_sessions where id in (select row_id from seed_6m_rows where tbl = 'study_sessions') or card_id in (select row_id from seed_6m_rows where tbl = 'cards'));
delete from study_sessions where id in (select row_id from seed_6m_rows where tbl = 'study_sessions') or card_id in (select row_id from seed_6m_rows where tbl = 'cards');
delete from card_day_marks where id in (select row_id from seed_6m_rows where tbl = 'card_day_marks') or card_id in (select row_id from seed_6m_rows where tbl = 'cards');
delete from experiment_cards where card_id in (select row_id from seed_6m_rows where tbl = 'cards');
delete from card_checklist_items where card_id in (select row_id from seed_6m_rows where tbl = 'cards');
delete from card_expected_fail_causes where card_id in (select row_id from seed_6m_rows where tbl = 'cards');
update daily_care_stages set forced_card_id = null, forced_card_date = null where forced_card_id in (select row_id from seed_6m_rows where tbl = 'cards');
delete from cards where id in (select row_id from seed_6m_rows where tbl = 'cards');
delete from checkin_emotions where checkin_id in (select row_id from seed_6m_rows where tbl = 'checkins');
delete from checkins where id in (select row_id from seed_6m_rows where tbl = 'checkins');
delete from daily_signals where id in (select row_id from seed_6m_rows where tbl = 'daily_signals');
delete from daily_care_stages where id in (select row_id from seed_6m_rows where tbl = 'daily_care_stages');
delete from call_records where id in (select row_id from seed_6m_rows where tbl = 'call_records');
delete from goal_target_snapshots where goal_id in (select row_id from seed_6m_rows where tbl = 'goals');
delete from goals where id in (select row_id from seed_6m_rows where tbl = 'goals');
delete from reviews where id in (select row_id from seed_6m_rows where tbl = 'reviews');
delete from exam_scores where id in (select row_id from seed_6m_rows where tbl = 'exam_scores');
delete from exams where id in (select row_id from seed_6m_rows where tbl = 'exams');
-- 문제 풀이 결과 · 이번 주에 고른 것 (seed-6m-topup.sql 이 넣은 것)
delete from quiz_results where id in (select row_id from seed_6m_rows where tbl = 'quiz_results');
delete from weekly_picks where id in (select row_id from seed_6m_rows where tbl = 'weekly_picks');
delete from subjects where id in (select row_id from seed_6m_rows where tbl = 'subjects') and not exists (select 1 from study_sessions s where s.subject_id = subjects.id) and not exists (select 1 from study_plans sp where sp.subject_id = subjects.id) and not exists (select 1 from exam_scores e where e.subject_id = subjects.id) and not exists (select 1 from quiz_results q where q.subject_id = subjects.id) and not exists (select 1 from cards c where c.subject_id = subjects.id);
delete from weekly_goals where id in (select row_id from seed_6m_rows where tbl = 'weekly_goals');
delete from messages where id in (select row_id from seed_6m_rows where tbl = 'messages');
delete from fixed_blocks where id in (select row_id from seed_6m_rows where tbl = 'fixed_blocks');
delete from seed_6m_rows;

drop table if exists seed_6m_rows;
drop table if exists seed_6m_daysum;
drop table if exists seed_6m_plan;
drop table if exists seed_6m_days;
-- seed-6m-topup.sql 의 계산용 표
drop table if exists seed_6m_weeks;
drop table if exists seed_6m_new_users;
drop table if exists seed_6m_today;
drop table if exists seed_6m_before;
commit;
