-- =====================================================================
-- 발표용 가상 데이터 더 넣기 (2026-10-27). seed-6m.sql 로 넣은 줄은 그대로 두고, 빠진 것만 더합니다.
--   1. seed-6m.sql 을 넣은 다음 날부터 어제까지의 기록 (카드 결과 · 세션 · 체크인 · 신호 · 관리 단계 · 통화 · 메시지)
--   2. 그 뒤에 가입한 사람(가상 카드가 없는 사람)에게는 6개월치 전체 (카드 · 목표 · 고정 일정 · 시험 · 회고까지)
--   3. 가상 카드와 그 카드로 잰 가상 세션에 과목을 붙입니다. (문제 풀이 리포트의 "이 과목 공부 시간")
--   4. 문제 풀이 결과(quiz_results): 최근 8주, 카드를 끝까지 한 가상 공부 세션 넷 중 셋에 3문제씩
--   5. 이번 주에 고른 것(weekly_picks): 최근 8주 + 이번 주
--   6. 주간 목표 · 주간 회고: 새로 끝난 주
-- 여러 번 실행해도 두 벌이 되지 않습니다. 더한 줄도 seed_6m_rows 에 적어서 seed-6m-remove.sql 로 같이 지워집니다.
-- =====================================================================

begin;

create table if not exists seed_6m_rows (tbl varchar(40) not null, row_id bigint not null);

drop table if exists seed_6m_daysum;
drop table if exists seed_6m_plan;
drop table if exists seed_6m_days;
drop table if exists seed_6m_new_users;
drop table if exists seed_6m_today;

-- 0) 오늘(새벽 4시 기준) · 이번에 새로 넣는 세션 · 체크인을 고르는 기준 번호 · 표마다 원래 몇 줄이었는지
create table seed_6m_today as
select ((now() at time zone 'Asia/Seoul') - interval '4 hours')::date as today,
       (select coalesce(max(id), 0) from study_sessions) as last_session_id,
       (select coalesce(max(id), 0) from checkins) as last_checkin_id;

drop table if exists seed_6m_before;
create table seed_6m_before as select tbl, count(*) as before_rows from seed_6m_rows group by tbl;

-- 가상 데이터를 한 번도 받지 않은 사람 (seed-6m.sql 을 넣은 뒤에 가입한 사람)
-- 가상 카드는 없지만 가상 목표가 있는 사람은 "모든 기록 지우기" 로 스스로 지운 사람이라 다시 넣지 않습니다.
create table seed_6m_new_users as
select u.id as user_id
from users u
where not exists (select 1 from cards c
                  where c.user_id = u.id and c.id in (select row_id from seed_6m_rows where tbl = 'cards'))
  and not exists (select 1 from goals g
                  where g.user_id = u.id and g.id in (select row_id from seed_6m_rows where tbl = 'goals'));

-- 1) 과목 국어 · 영어 · 수학 (없을 때만)
with ins as (
  insert into subjects (user_id, color, name)
  select u.id, v.color, v.name
  from users u cross join (values ('국어', 'RED'), ('영어', 'GREEN'), ('수학', 'BLUE')) as v(name, color)
  on conflict (user_id, name) do nothing
  returning id
)
insert into seed_6m_rows (tbl, row_id) select 'subjects', id from ins;

-- 2) 새 가입자에게 카드 7장 (seed-6m.sql 과 같은 카드 · 6개월 전부터)
with spec(title, repeat_days, start_time, minutes, card_type, priority) as (
  values
    ('아침 영단어 20개',     '0,1,2,3,4,5,6', time '07:10', 20,  'SELF',    'FLEXIBLE'),
    ('수학 문제집 2단원씩', '1,2,3,4,5',     time '16:40', 60,  'SELF',    'ABSOLUTE'),
    ('국어 비문학 3지문',   '2,4',           time '19:30', 50,  'SELF',    'FLEXIBLE'),
    ('밤 수학 오답 정리',   '1,3,5',         time '22:00', 40,  'SELF',    'FLEXIBLE'),
    ('탐구 인강 1강',       '2,4',           time '21:30', 40,  'LECTURE', 'FLEXIBLE'),
    ('주말 수학 모의고사',  '6',             time '10:00', 100, 'SELF',    'ABSOLUTE'),
    ('주간 오답 복습',      '0',             time '15:00', 60,  'SELF',    'FLEXIBLE')
),
ins as (
  insert into cards (card_date, duration_minutes, start_time, created_at, user_id, priority, type, repeat_days, title)
  select t.today - 182, s.minutes, s.start_time, ((t.today - 182) + time '09:00') at time zone 'Asia/Seoul',
         n.user_id, s.priority, s.card_type, s.repeat_days, s.title
  from seed_6m_new_users n cross join spec s cross join seed_6m_today t
  returning id
)
insert into seed_6m_rows (tbl, row_id) select 'cards', id from ins;

insert into card_checklist_items (position, card_id, item_key, text)
select 0, c.id, 'seed-1',
       case c.title
         when '아침 영단어 20개' then '단어 20개 외우고 10개 셀프 테스트'
         when '수학 문제집 2단원씩' then '유형 문제 2단원 풀고 채점'
         when '국어 비문학 3지문' then '지문 3개 풀고 구조도 그리기'
         when '밤 수학 오답 정리' then '오늘 틀린 문제 다시 풀고 오답노트'
         when '탐구 인강 1강' then '인강 1강 듣고 요약 3줄'
         when '주말 수학 모의고사' then '실전처럼 100분 풀고 채점'
         else '이번 주 오답 다시 풀기'
       end
from cards c
where c.id in (select row_id from seed_6m_rows where tbl = 'cards')
  and not exists (select 1 from card_checklist_items i where i.card_id = c.id);

-- 3) 가상 카드에 과목 붙이기 (영단어 → 영어, 비문학 → 국어, 탐구 인강 → 없음, 나머지 → 수학)
update cards c
set subject_id = sub.id
from subjects sub
where c.id in (select row_id from seed_6m_rows where tbl = 'cards')
  and c.subject_id is null
  and sub.user_id = c.user_id
  and sub.name = case c.title
                   when '아침 영단어 20개' then '영어'
                   when '국어 비문학 3지문' then '국어'
                   when '탐구 인강 1강' then null
                   else '수학'
                 end;

-- 4) 사람 · 날짜 표: 가상 기록이 끝난 다음 날부터 어제까지 (새 가입자는 6개월 전부터)
--    끝난 날 = 가상 신호 · 관리 단계 · 미루기/실패 · 세션 중 가장 늦은 날
create table seed_6m_days as
with st as (
  select u.id as user_id,
         (select min(c.card_date) from cards c
          where c.user_id = u.id and c.id in (select row_id from seed_6m_rows where tbl = 'cards')) as start_day,
         greatest(
           (select max(s.signal_date) from daily_signals s
            where s.user_id = u.id and s.id in (select row_id from seed_6m_rows where tbl = 'daily_signals')),
           (select max(s.stage_date) from daily_care_stages s
            where s.user_id = u.id and s.id in (select row_id from seed_6m_rows where tbl = 'daily_care_stages')),
           (select max(m.mark_date) from card_day_marks m
            where m.user_id = u.id and m.id in (select row_id from seed_6m_rows where tbl = 'card_day_marks')),
           (select max(((x.started_at at time zone 'Asia/Seoul') - interval '4 hours')::date) from study_sessions x
            where x.user_id = u.id and x.id in (select row_id from seed_6m_rows where tbl = 'study_sessions'))
         ) as done_through
  from users u
)
select st.user_id, d::date as day,
       (d::date - st.start_day) as day_index,
       extract(dow from d)::int as dow,
       (('x' || substr(md5((st.user_id || '|chrono')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.5 as morning_person,
       (('x' || substr(md5((st.user_id || '|phone')::text), 1, 8))::bit(32)::bigint / 4294967296.0) * 0.15 as phone_weak,
       (('x' || substr(md5((st.user_id || '|' || d::date || '|sick')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.03 as sick
from st
cross join seed_6m_today t
cross join generate_series(coalesce(st.done_through + 1, st.start_day), t.today - 1, interval '1 day') d
where st.start_day is not null;

-- 5) 카드 하루하루의 결과 (seed-6m.sql 과 같은 규칙). 사람이 그날 그 카드로 이미 남긴 기록 · 표시가 있으면 건드리지 않습니다.
create table seed_6m_plan as
with seed_cards as (
  select c.* from cards c where c.id in (select row_id from seed_6m_rows where tbl = 'cards')
),
base as (
  select d.user_id, d.day, d.day_index, d.dow, d.sick, d.morning_person, d.phone_weak,
         c.id as card_id, c.title, c.start_time, c.duration_minutes,
         case
           when c.title in ('아침 영단어 20개', '주말 수학 모의고사') then 'morning'
           when c.title in ('수학 문제집 2단원씩', '주간 오답 복습') then 'afternoon'
           when c.title = '국어 비문학 3지문' then 'evening'
           else 'night'
         end as slot,
         (('x' || substr(md5((d.user_id || '|' || c.title || '|' || d.day)::text), 1, 8))::bit(32)::bigint / 4294967296.0) as u1,
         (('x' || substr(md5((d.user_id || '|' || c.title || '|' || d.day || '|b')::text), 1, 8))::bit(32)::bigint / 4294967296.0) as u2,
         (('x' || substr(md5((d.user_id || '|' || c.title || '|' || d.day || '|c')::text), 1, 8))::bit(32)::bigint / 4294967296.0) as u3
  from seed_6m_days d
  join seed_cards c on c.user_id = d.user_id
   and position(d.dow::text in c.repeat_days) > 0
  where not exists (select 1 from study_sessions x
                    where x.card_id = c.id
                      and ((x.started_at at time zone 'Asia/Seoul') - interval '4 hours')::date = d.day)
    and not exists (select 1 from card_day_marks m where m.card_id = c.id and m.mark_date = d.day)
),
prob as (
  select b.*,
         least(0.92, greatest(0.25,
           case b.slot
             when 'morning'   then case when b.morning_person then 0.78 else 0.58 end
             when 'afternoon' then 0.70
             when 'evening'   then case when b.morning_person then 0.58 else 0.74 end
             else                  case when b.morning_person then 0.50 else 0.68 end
           end
           + 0.15 * least(b.day_index, 181) / 181.0
           - case when b.slot = 'night' then b.phone_weak else 0 end
           - case when b.dow in (0, 6) and b.slot <> 'morning' then 0.08 else 0 end
         )) as p_done
  from base b
)
select p.*,
       case
         when p.sick then 'failed'
         when p.u1 < p.p_done * 0.82 then 'done'
         when p.u1 < p.p_done then 'short'
         when p.u1 < p.p_done + 0.10 then 'postponed'
         else 'failed'
       end as outcome,
       case
         when p.sick then 'SICK'
         when p.slot = 'night' and p.u2 < 0.45 + p.phone_weak then 'PHONE'
         when p.u2 < 0.30 then 'PHONE'
         when p.u2 < 0.55 then 'TIRED'
         when p.u2 < 0.70 then 'PROCRASTINATED'
         when p.u2 < 0.82 then 'TOO_MUCH'
         when p.u2 < 0.93 then 'EVENT'
         else 'UNWILLING'
       end as cause
from prob p;

-- 6) 공부 세션 (끝까지 함 · 일찍 끝냄)
with ins as (
  insert into study_sessions (end_reported, ended_early, pause_count, target_minutes, card_id, ended_at,
                              paused_ms, started_at, user_id, kind, with_what, mode, shortfall_reason)
  select true,
         p.outcome = 'short',
         x.pauses,
         p.duration_minutes,
         p.card_id,
         x.started + make_interval(mins => x.worked) + make_interval(secs => x.pauses * 150),
         x.pauses * 150000,
         x.started,
         p.user_id,
         'STUDY',
         case when p.u3 < 0.35 then 'MUSIC' else 'NONE' end,
         'NORMAL',
         case when p.outcome = 'short' then
           case when p.cause = 'PHONE' then 'PHONE'
                when p.cause = 'TIRED' then 'TIRED'
                when p.cause = 'TOO_MUCH' then 'TOO_MUCH'
                when p.u3 < 0.5 then 'STUCK'
                else 'MIND_WANDER' end
         end
  from seed_6m_plan p
  cross join lateral (
    select
      ((p.day + p.start_time) at time zone 'Asia/Seoul')
        + make_interval(mins => case when p.outcome = 'done' then -3 + floor(p.u2 * 11)::int
                                     else 12 + floor(p.u2 * 25)::int end) as started,
      case when p.outcome = 'done' then round(p.duration_minutes * (0.95 + p.u3 * 0.15))::int
           else round(p.duration_minutes * (0.40 + p.u3 * 0.40))::int end as worked,
      floor(p.u3 * 3.2)::int as pauses
  ) x
  where p.outcome in ('done', 'short')
  returning id
)
insert into seed_6m_rows (tbl, row_id) select 'study_sessions', id from ins;

-- 7) 노는 세션 (저녁, 사흘에 하루쯤)
with ins as (
  insert into study_sessions (end_reported, ended_early, pause_count, target_minutes, ended_at, paused_ms,
                              started_at, user_id, kind, play_tag, mode)
  select true, false, 0, x.minutes,
         x.started + make_interval(mins => x.minutes), 0, x.started, d.user_id, 'PLAY',
         case when (('x' || substr(md5((d.user_id || '|' || d.day || '|tag')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.35 then 'YOUTUBE'
              when (('x' || substr(md5((d.user_id || '|' || d.day || '|tag')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.60 then 'GAME'
              when (('x' || substr(md5((d.user_id || '|' || d.day || '|tag')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.80 then 'SNS'
              else 'WEBTOON' end,
         'NORMAL'
  from seed_6m_days d
  cross join lateral (
    select ((d.day + time '20:40') at time zone 'Asia/Seoul')
             + make_interval(mins => floor((('x' || substr(md5((d.user_id || '|' || d.day || '|pt')::text), 1, 8))::bit(32)::bigint / 4294967296.0) * 60)::int) as started,
           20 + floor((('x' || substr(md5((d.user_id || '|' || d.day || '|pm')::text), 1, 8))::bit(32)::bigint / 4294967296.0) * 70)::int as minutes
  ) x
  where (('x' || substr(md5((d.user_id || '|' || d.day || '|play')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.35
  returning id
)
insert into seed_6m_rows (tbl, row_id) select 'study_sessions', id from ins;

-- 8) 미루기 · 실패 표시
with ins as (
  insert into card_day_marks (crisis_detected, mark_date, new_date, new_start_time, card_id, created_at, user_id, mark, cause)
  select false, p.day,
         case when p.outcome = 'postponed' then p.day + 1 end,
         case when p.outcome = 'postponed' then p.start_time end,
         p.card_id,
         ((p.day + p.start_time) at time zone 'Asia/Seoul') + interval '40 minutes',
         p.user_id,
         case when p.outcome = 'postponed' then 'POSTPONED' else 'FAILED' end,
         case when p.outcome = 'failed' then p.cause end
  from seed_6m_plan p
  where p.outcome in ('postponed', 'failed')
  returning id
)
insert into seed_6m_rows (tbl, row_id) select 'card_day_marks', id from ins;

-- 9) 하루 정리 (사람 · 날짜마다 한 줄): 한 개수 · 놓친 개수 · 주원인
create table seed_6m_daysum as
select d.user_id, d.day, d.day_index, d.sick,
       count(p.card_id) filter (where p.outcome in ('done', 'short')) as did,
       count(p.card_id) filter (where p.outcome = 'failed') as failed,
       count(p.card_id) as planned,
       coalesce(sum(p.duration_minutes), 0) as planned_minutes,
       mode() within group (order by p.cause) filter (where p.outcome = 'failed') as top_cause
from seed_6m_days d
left join seed_6m_plan p on p.user_id = d.user_id and p.day = d.day
group by d.user_id, d.day, d.day_index, d.sick;

-- 10) 체크인 (열흘에 아홉 날쯤). 이미 있는 날짜는 건드리지 않습니다.
with src as (
  select t.user_id, t.day, t.sick,
         y.failed as y_failed, y.top_cause as y_cause, t.planned_minutes,
         least(5, greatest(1, case when t.sick then 1 else 3 end
                              + case when coalesce(y.failed, 0) = 0 then 1 else -1 end
                              + floor((('x' || substr(md5((t.user_id || '|' || t.day || '|cond')::text), 1, 8))::bit(32)::bigint / 4294967296.0) * 2)::int)) as condition_,
         least(5, greatest(1, 2 + floor((('x' || substr(md5((t.user_id || '|' || t.day || '|mood')::text), 1, 8))::bit(32)::bigint / 4294967296.0) * 3)::int
                              + case when coalesce(y.failed, 0) >= 2 then -1 else 0 end)) as mood_
  from seed_6m_daysum t
  left join seed_6m_daysum y on y.user_id = t.user_id and y.day = t.day - 1
  where (('x' || substr(md5((t.user_id || '|' || t.day || '|ci')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.90
    and not exists (select 1 from checkins k where k.user_id = t.user_id and k.check_in_date = t.day)
),
ins as (
  insert into checkins (check_in_date, condition, expected_minutes, mood, user_id, source, miss_reason)
  select s.day, s.condition_, (round(s.planned_minutes * 0.8 / 10.0) * 10)::int, s.mood_, s.user_id,
         case when (('x' || substr(md5((s.user_id || '|' || s.day || '|src')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.6 then 'MANUAL' else 'QUICK' end,
         case when coalesce(s.y_failed, 0) > 0 then s.y_cause end
  from src s
  returning id
)
insert into seed_6m_rows (tbl, row_id) select 'checkins', id from ins;

-- 체크인 감정 (이번에 넣은 체크인만)
insert into checkin_emotions (checkin_id, emotion)
select k.id,
       case when k.mood <= 2 then (array['TIRED', 'ANXIOUS', 'STUCK', 'ANNOYED'])[1 + floor((('x' || substr(md5((k.id || '|e1')::text), 1, 8))::bit(32)::bigint / 4294967296.0) * 4)::int]
            when k.mood = 3 then (array['CALM', 'TIRED', 'STUCK'])[1 + floor((('x' || substr(md5((k.id || '|e1')::text), 1, 8))::bit(32)::bigint / 4294967296.0) * 3)::int]
            else (array['MOTIVATED', 'CALM'])[1 + floor((('x' || substr(md5((k.id || '|e1')::text), 1, 8))::bit(32)::bigint / 4294967296.0) * 2)::int] end
from checkins k
where k.id in (select row_id from seed_6m_rows where tbl = 'checkins')
  and k.id > (select last_checkin_id from seed_6m_today);

-- 11) 수면 · 걸음 신호
with ins as (
  insert into daily_signals (signal_date, sleep_minutes, sns_over_thirty_minutes, steps, slept_at, user_id, woke_at)
  select t.day, x.sleep, coalesce(t.top_cause = 'PHONE', false) or (('x' || substr(md5((t.user_id || '|' || t.day || '|sns')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.25,
         3000 + floor((('x' || substr(md5((t.user_id || '|' || t.day || '|steps')::text), 1, 8))::bit(32)::bigint / 4294967296.0) * 9000)::int,
         ((t.day - 1 + time '23:30') at time zone 'Asia/Seoul') + make_interval(mins => floor((('x' || substr(md5((t.user_id || '|' || t.day || '|bed')::text), 1, 8))::bit(32)::bigint / 4294967296.0) * 90)::int),
         t.user_id,
         ((t.day - 1 + time '23:30') at time zone 'Asia/Seoul') + make_interval(mins => floor((('x' || substr(md5((t.user_id || '|' || t.day || '|bed')::text), 1, 8))::bit(32)::bigint / 4294967296.0) * 90)::int + x.sleep)
  from seed_6m_daysum t
  cross join lateral (
    select 330 + floor((('x' || substr(md5((t.user_id || '|' || t.day || '|sleep')::text), 1, 8))::bit(32)::bigint / 4294967296.0) * 120)::int
               - case when t.failed >= 2 then 40 else 0 end as sleep
  ) x
  where not exists (select 1 from daily_signals s where s.user_id = t.user_id and s.signal_date = t.day)
  returning id
)
insert into seed_6m_rows (tbl, row_id) select 'daily_signals', id from ins;

-- 12) 관리 단계 (어제 결과로 정한 것. 0 ~ 2 단계만)
with ins as (
  insert into daily_care_stages (stage, stage_date, suggest_shrink, decided_at, user_id, rule, reason)
  select x.stage, t.day, x.rule in ('HOLD_UNAVOIDABLE', 'HOLD_OVERLOAD'),
         (t.day + time '04:10') at time zone 'Asia/Seoul', t.user_id, x.rule, x.reason
  from seed_6m_daysum t
  left join seed_6m_daysum y on y.user_id = t.user_id and y.day = t.day - 1
  cross join lateral (
    select
      case when coalesce(y.sick, false) then 0
           when coalesce(y.failed, 0) >= 2 then 2
           else 1 end as stage,
      case when coalesce(y.sick, false) then 'RECOVERY'
           when coalesce(y.failed, 0) >= 2 and y.top_cause in ('SICK', 'EVENT') then 'HOLD_UNAVOIDABLE'
           when coalesce(y.failed, 0) >= 2 and y.top_cause in ('TIRED', 'TOO_MUCH') then 'HOLD_OVERLOAD'
           when coalesce(y.failed, 0) >= 2 then 'UP'
           else 'TO_BASE' end as rule,
      case when coalesce(y.sick, false) then '어제 아파서 쉬었어요. 오늘은 회복 모드로 가볍게 가요.'
           when coalesce(y.failed, 0) >= 2 and y.top_cause in ('SICK', 'EVENT') then '어제 어쩔 수 없는 일로 카드를 놓쳤어요. 단계는 그대로 두고 계획을 줄여 볼까요?'
           when coalesce(y.failed, 0) >= 2 and y.top_cause in ('TIRED', 'TOO_MUCH') then '어제 피곤하거나 계획이 많아서 놓쳤어요. 단계는 그대로 두고 계획을 줄여 볼까요?'
           when coalesce(y.failed, 0) >= 2 then '어제 카드를 두 번 넘게 놓쳤어요. 오늘은 한 번 더 챙길게요.'
           else '어제 계획대로 했어요. 기준 단계로 돌아가요.' end as reason
  ) x
  where t.day_index > 0
    and not exists (select 1 from daily_care_stages s where s.user_id = t.user_id and s.stage_date = t.day)
  returning id
)
insert into seed_6m_rows (tbl, row_id) select 'daily_care_stages', id from ins;

-- 13) 통화 기록 (화 · 금 밤, 열 번에 일곱 번쯤). 그날 통화가 이미 있으면 넣지 않습니다.
with ins as (
  insert into call_records (call_date, crisis_detected, promise_kept, finished_at, started_at, user_id, channel,
                            miss_reason, promise_if, promise_then, summary)
  select t.day, false,
         case when t.failed = 0 then true else (('x' || substr(md5((t.user_id || '|' || t.day || '|kept')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.4 end,
         ((t.day + time '21:40') at time zone 'Asia/Seoul') + interval '3 minutes',
         (t.day + time '21:40') at time zone 'Asia/Seoul',
         t.user_id,
         case when (('x' || substr(md5((t.user_id || '|' || t.day || '|ch')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.7 then 'VOICE' else 'CHAT' end,
         case when t.failed > 0 then t.top_cause end,
         case when t.top_cause = 'PHONE' then '공부를 시작하면' when t.top_cause = 'TIRED' then '피곤한 날에는' else '카드 시간이 되면' end,
         case when t.top_cause = 'PHONE' then '폰을 다른 방에 둔다' when t.top_cause = 'TIRED' then '20분만 하고 쉰다' else '5분 안에 시작한다' end,
         '오늘 카드 ' || t.planned || '개 중 ' || t.did || '개를 했어요.'
           || case when t.failed > 0 then
                ' 놓친 이유는 ' || case t.top_cause when 'PHONE' then '폰' when 'TIRED' then '피곤함' when 'PROCRASTINATED' then '미루기'
                                                    when 'TOO_MUCH' then '계획 과다' when 'EVENT' then '갑자기 생긴 일' when 'SICK' then '아픔'
                                                    else '하기 싫음' end || '이었어요.'
              else ' 계획대로 다 했어요.' end
  from seed_6m_daysum t
  where extract(dow from t.day) in (2, 5)
    and (('x' || substr(md5((t.user_id || '|' || t.day || '|call')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.7
    and not exists (select 1 from call_records r where r.user_id = t.user_id and r.call_date = t.day)
  returning id
)
insert into seed_6m_rows (tbl, row_id) select 'call_records', id from ins;

-- 14) 세션 세부 (이번에 넣은 공부 세션만): 일시정지 · 체크리스트 체크 · 잘된 이유 · 메모
insert into session_pauses (position, ended_at, started_at, study_session_id, tag)
select g.pos,
       s.started_at + make_interval(secs => round(w.work_secs * (g.pos + 1) / (s.pause_count + 1) + g.pos * 150)::int) + interval '150 seconds',
       s.started_at + make_interval(secs => round(w.work_secs * (g.pos + 1) / (s.pause_count + 1) + g.pos * 150)::int),
       s.id,
       case when (('x' || substr(md5((s.id || '|' || g.pos || '|tag')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.30 then 'PHONE'
            when (('x' || substr(md5((s.id || '|' || g.pos || '|tag')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.50 then 'TOILET'
            when (('x' || substr(md5((s.id || '|' || g.pos || '|tag')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.65 then 'SNACK'
            when (('x' || substr(md5((s.id || '|' || g.pos || '|tag')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.80 then 'MIND_WANDER'
            when (('x' || substr(md5((s.id || '|' || g.pos || '|tag')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.90 then 'STUCK'
            when (('x' || substr(md5((s.id || '|' || g.pos || '|tag')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.95 then 'EXTERNAL'
            else 'OTHER' end
from study_sessions s
cross join lateral (select extract(epoch from (s.ended_at - s.started_at)) - s.pause_count * 150 as work_secs) w
cross join lateral generate_series(0, s.pause_count - 1) as g(pos)
where s.id in (select row_id from seed_6m_rows where tbl = 'study_sessions')
  and s.id > (select last_session_id from seed_6m_today)
  and s.kind = 'STUDY' and s.pause_count > 0;

insert into session_checked_items (checked_at, study_session_id, item_key)
select s.ended_at - interval '1 minute', s.id, 'seed-1'
from study_sessions s
where s.id in (select row_id from seed_6m_rows where tbl = 'study_sessions')
  and s.id > (select last_session_id from seed_6m_today)
  and s.kind = 'STUDY'
  and (s.ended_early = false or (('x' || substr(md5((s.id || '|chk')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.4);

insert into session_good_reasons (study_session_id, good_reason)
select s.id,
       case when (('x' || substr(md5((s.id || '|good')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.35 then 'FOCUS'
            when (('x' || substr(md5((s.id || '|good')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.55 then 'GOOD_PLAN'
            when (('x' || substr(md5((s.id || '|good')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.72 then 'CONDITION'
            when (('x' || substr(md5((s.id || '|good')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.86 then 'ENVIRONMENT'
            when (('x' || substr(md5((s.id || '|good')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.95 then 'HELPED'
            else 'LUCK' end
from study_sessions s
where s.id in (select row_id from seed_6m_rows where tbl = 'study_sessions')
  and s.id > (select last_session_id from seed_6m_today)
  and s.kind = 'STUDY' and s.ended_early = false and s.pause_count <= 1
  and (('x' || substr(md5((s.id || '|gr')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.45;

insert into session_memos (position, study_session_id, written_at, text)
select 0, s.id, s.ended_at,
       (array['오답 3개는 내일 다시 보기', '집중 잘 됨. 폰은 가방에 넣어 둠', '어려운 유형 표시해 둠', '시간이 모자라서 마지막 문제 못 풂',
              '단어 10개 중 8개 맞음', '개념 다시 봐야 할 부분 있음', '음악 없이 하니까 더 잘 됨', '중간에 졸려서 물 마시고 옴'])[1 + floor((('x' || substr(md5((s.id || '|memo')::text), 1, 8))::bit(32)::bigint / 4294967296.0) * 8)::int]
from study_sessions s
where s.id in (select row_id from seed_6m_rows where tbl = 'study_sessions')
  and s.id > (select last_session_id from seed_6m_today)
  and s.kind = 'STUDY' and (('x' || substr(md5((s.id || '|m')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.12;

-- 15) AI 메시지 (아침 7:50, 사흘에 하루쯤 · 전날 결과에 맞는 말 · 읽음 처리)
with ins as (
  insert into messages (read_at, sent_at, user_id, text, kind)
  select x.sent + interval '12 minutes', x.sent, t.user_id, x.text, x.kind
  from seed_6m_daysum t
  join seed_6m_daysum y on y.user_id = t.user_id and y.day = t.day - 1
  cross join lateral (
    select (t.day + time '07:50') at time zone 'Asia/Seoul' as sent,
           case when y.sick then 'RECOVERY'
                when y.failed = 0 and y.planned > 0 then 'CARROT'
                else 'NUDGE' end as kind,
           case when y.sick then '어제는 쉬어 가는 날이었어. 오늘은 아침 영단어 하나만 해도 충분해.'
                when y.failed >= 2 and y.top_cause = 'PHONE' then '어제 카드 ' || y.failed || '개를 폰 때문에 놓쳤어. 오늘은 시작할 때 폰을 다른 방에 둬 보자.'
                when y.failed >= 2 then '어제 카드 ' || y.failed || '개를 놓쳤어. 오늘은 제일 중요한 카드 하나부터 시작해 보자.'
                when y.failed = 0 and y.planned > 0 then '어제 계획한 카드 ' || y.planned || '개를 다 했어! 이 흐름 그대로 가 보자.'
                else '어제 ' || y.did || '개는 해냈어. 오늘은 놓친 시간대를 한 번 더 챙겨 볼게.' end as text
  ) x
  where (('x' || substr(md5((t.user_id || '|' || t.day || '|msg')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.35
  returning id
)
insert into seed_6m_rows (tbl, row_id) select 'messages', id from ins;

-- 16) 새 가입자만: 목표 2개 · 고정 일정 · 시험 3번 · 점수 · 시험 회고 (seed-6m.sql 과 같은 내용)
with g(title, kind, d_offset, total_amount, amount_unit, current_score, benefit, cost, obstacle, plan) as (
  values
    ('11월 학평 수학 3등급', 'SCORE', 25, null::int, null::varchar, '4등급', '원하는 학과에 지원할 수 있어요', '주말 오후를 모의고사에 써야 해요', '학원 끝난 밤에 폰을 오래 봐요', '밤 오답은 폰을 다른 방에 두고 한다'),
    ('기말 전 영단어장 1회독', 'COMPLETION', 40, 300, '쪽', null::varchar, '영어 지문이 덜 막혀요', '아침 20분을 일찍 일어나야 해요', '늦게 자면 아침을 건너뛰어요', '자기 전에 단어장을 책상 위에 펴 둔다')
),
ins as (
  insert into goals (d_day, total_amount, amount_unit, created_at, user_id, kind, status, current_score, title, benefit, cost, obstacle, plan)
  select t.today + g.d_offset, g.total_amount, g.amount_unit,
         ((t.today - 182) + time '09:00') at time zone 'Asia/Seoul',
         n.user_id, g.kind, 'ACTIVE', g.current_score, g.title, g.benefit, g.cost, g.obstacle, g.plan
  from seed_6m_new_users n cross join g cross join seed_6m_today t
  returning id
)
insert into seed_6m_rows (tbl, row_id) select 'goals', id from ins;

with b(title, repeat_days, start_time, end_time) as (
  values ('학교', '1,2,3,4,5', time '08:20', time '16:20'),
         ('수학 학원', '1,3,5', time '18:00', time '21:30')
),
ins as (
  insert into fixed_blocks (end_time, start_time, created_at, user_id, repeat_days, title)
  select b.end_time, b.start_time, ((t.today - 182) + time '09:00') at time zone 'Asia/Seoul',
         n.user_id, b.repeat_days, b.title
  from seed_6m_new_users n cross join b cross join seed_6m_today t
  returning id
)
insert into seed_6m_rows (tbl, row_id) select 'fixed_blocks', id from ins;

with e(name, type, exam_date) as (
  values ('6월 모의고사', 'MOCK', date '2026-06-04'),
         ('1학기 기말고사', 'SCHOOL', date '2026-07-07'),
         ('9월 모의고사', 'MOCK', date '2026-09-03')
),
ins as (
  insert into exams (exam_date, user_id, type, name)
  select e.exam_date, n.user_id, e.type, e.name from seed_6m_new_users n cross join e
  returning id
)
insert into seed_6m_rows (tbl, row_id) select 'exams', id from ins;

with sc as (
  select x.id as exam_id, x.user_id, sub.id as subject_id, sub.name as subject_name,
         case x.name when '6월 모의고사' then 0 when '1학기 기말고사' then 4 else 8 end as step
  from exams x
  join subjects sub on sub.user_id = x.user_id and sub.name in ('국어', '영어', '수학')
  where x.id in (select row_id from seed_6m_rows where tbl = 'exams')
    and x.user_id in (select user_id from seed_6m_new_users)
    and not exists (select 1 from exam_scores es where es.exam_id = x.id)
),
pts as (
  select sc.*,
         least(100, greatest(0, 55 + floor((('x' || substr(md5((sc.user_id || '|' || sc.subject_name || '|base')::text), 1, 8))::bit(32)::bigint / 4294967296.0) * 20)::int + sc.step
                               + floor((('x' || substr(md5((sc.exam_id || '|' || sc.subject_name || '|noise')::text), 1, 8))::bit(32)::bigint / 4294967296.0) * 11)::int - 5)) as actual
  from sc
),
ins as (
  insert into exam_scores (actual_score, expected_score, exam_id, subject_id, user_id)
  select p.actual, least(100, p.actual + 3 + floor((('x' || substr(md5((p.exam_id || '|' || p.subject_name || '|exp')::text), 1, 8))::bit(32)::bigint / 4294967296.0) * 8)::int),
         p.exam_id, p.subject_id, p.user_id
  from pts p
  returning id
)
insert into seed_6m_rows (tbl, row_id) select 'exam_scores', id from ins;

with ins as (
  insert into reviews (exam_id, saved_at, user_id, type, answer1, answer2, answer3)
  select x.id, ((x.exam_date + 2) + time '21:00') at time zone 'Asia/Seoul', x.user_id, 'EXAM',
         '수학은 계획보다 오답 정리를 덜 했다',
         '시험 전 주에 밤 카드를 자주 놓쳤다. 폰 때문이 제일 컸다',
         '다음 시험 전 2주는 밤 오답을 폰 없이 하기'
  from exams x
  where x.id in (select row_id from seed_6m_rows where tbl = 'exams')
    and x.user_id in (select user_id from seed_6m_new_users)
    and not exists (select 1 from reviews r where r.user_id = x.user_id and r.exam_id = x.id)
  returning id
)
insert into seed_6m_rows (tbl, row_id) select 'reviews', id from ins;

-- 17) 주간 목표 · 주간 회고 (새로 끝난 주). 실행률은 가상 카드의 실제 줄(세션 = 함, 미루기 · 실패 = 놓침)로 다시 셉니다.
drop table if exists seed_6m_weeks;
create table seed_6m_weeks as
with cd as (
  select x.user_id, ((x.started_at at time zone 'Asia/Seoul') - interval '4 hours')::date as day, true as did, null::varchar as cause
  from study_sessions x
  where x.kind = 'STUDY' and x.card_id in (select row_id from seed_6m_rows where tbl = 'cards')
  union all
  select m.user_id, m.mark_date, false, m.cause
  from card_day_marks m
  where m.card_id in (select row_id from seed_6m_rows where tbl = 'cards')
),
wk as (
  select cd.user_id, date_trunc('week', cd.day)::date as week_start,
         round(100.0 * count(*) filter (where cd.did) / count(*))::int as rate,
         mode() within group (order by cd.cause) filter (where not cd.did and cd.cause is not null) as top_cause
  from cd group by 1, 2
)
select w.*, lag(w.rate) over (partition by w.user_id order by w.week_start) as prev_rate
from wk w;

with ins as (
  insert into weekly_goals (baseline, result_value, target, week_start, created_at, judged_at, user_id, status, kind)
  select coalesce(w.prev_rate, 60), w.rate, least(95, coalesce(w.prev_rate, 60) + 5), w.week_start,
         (w.week_start + time '09:00') at time zone 'Asia/Seoul',
         ((w.week_start + 7) + time '05:00') at time zone 'Asia/Seoul',
         w.user_id,
         case when w.rate >= least(95, coalesce(w.prev_rate, 60) + 5) then 'ACHIEVED' else 'MISSED' end,
         'COMPLETION'
  from seed_6m_weeks w cross join seed_6m_today t
  where w.week_start >= t.today - 182
    and w.week_start + 6 <= t.today - 1
    and not exists (select 1 from weekly_goals g where g.user_id = w.user_id and g.week_start = w.week_start)
  returning id
)
insert into seed_6m_rows (tbl, row_id) select 'weekly_goals', id from ins;

with ins as (
  insert into reviews (week_start, saved_at, user_id, type, answer1, answer2, answer3)
  select w.week_start, ((w.week_start + 6) + time '21:30') at time zone 'Asia/Seoul', w.user_id, 'WEEKLY',
         '이번 주 실행률 ' || w.rate || '%',
         case w.top_cause when 'PHONE' then '폰 때문에 밤 카드를 놓친 날이 많았다'
                          when 'TIRED' then '피곤해서 미룬 날이 있었다'
                          when 'PROCRASTINATED' then '시작을 미루다가 시간이 지나갔다'
                          when 'TOO_MUCH' then '계획을 너무 많이 잡았다'
                          else '큰 문제 없이 지나갔다' end,
         case when w.rate >= 75 then '이대로 유지하기' else '밤 카드 시작 전에 폰을 다른 방에 두기' end
  from seed_6m_weeks w cross join seed_6m_today t
  where w.week_start >= t.today - 182
    and w.week_start + 6 <= t.today - 1
    and (('x' || substr(md5((w.user_id || '|' || w.week_start || '|wr')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.5
    and not exists (select 1 from reviews r where r.user_id = w.user_id and r.week_start = w.week_start)
  returning id
)
insert into seed_6m_rows (tbl, row_id) select 'reviews', id from ins;

-- 18) 가상 공부 세션에 과목 붙이기 (그 카드의 과목. 사람이 잰 세션은 건드리지 않습니다)
update study_sessions s
set subject_id = c.subject_id
from cards c
where s.card_id = c.id
  and s.id in (select row_id from seed_6m_rows where tbl = 'study_sessions')
  and s.subject_id is null
  and c.subject_id is not null;

-- 19) 문제 풀이 결과: 최근 8주(56일), 카드를 끝까지 한 가상 공부 세션 넷 중 셋을 끝낸 뒤 3문제
--     일찍 끝낸 세션(ended_early)에는 붙이지 않습니다. 확인 문제는 공부를 끝낸 카드로 내기 때문입니다.
--     단원마다 처음 실력과 늘어나는 정도가 다릅니다. 삼각함수는 끝까지 60% 아래라 "취약 단원" 으로 보입니다.
with units(unit, skill_start, skill_gain, sec_lo, sec_hi) as (
  values ('영단어', 0.60, 0.20, 8, 25),
         ('비문학', 0.60, 0.15, 40, 90),
         ('수열', 0.65, 0.20, 40, 110),
         ('삼각함수', 0.30, 0.30, 60, 150),
         ('함수', 0.55, 0.20, 50, 120)
),
src as (
  select s.id as session_id, s.user_id, s.subject_id, c.title,
         ((s.started_at at time zone 'Asia/Seoul') - interval '4 hours')::date as day,
         (('x' || substr(md5((s.id || '|quiz')::text), 1, 8))::bit(32)::bigint / 4294967296.0) as u_take,
         (('x' || substr(md5((s.id || '|unit')::text), 1, 8))::bit(32)::bigint / 4294967296.0) as u_unit,
         (('x' || substr(md5((s.id || '|right')::text), 1, 8))::bit(32)::bigint / 4294967296.0) as u_right
  from study_sessions s
  join cards c on c.id = s.card_id
  where s.id in (select row_id from seed_6m_rows where tbl = 'study_sessions')
    and s.kind = 'STUDY' and s.subject_id is not null
    and s.ended_early = false
),
picked as (
  select src.*,
         case when src.title = '아침 영단어 20개' then '영단어'
              when src.title = '국어 비문학 3지문' then '비문학'
              when src.u_unit < 0.40 then '수열'
              when src.u_unit < 0.75 then '삼각함수'
              else '함수' end as unit
  from src cross join seed_6m_today t
  where src.day >= t.today - 56 and src.day < t.today and src.u_take < 0.75
),
scored as (
  select p.*, u.sec_lo, u.sec_hi,
         least(3, greatest(0, round(3 * (u.skill_start + u.skill_gain * (p.day - (t.today - 56)) / 55.0
                                         + ((('x' || substr(md5((p.user_id || '|' || p.unit || '|skill')::text), 1, 8))::bit(32)::bigint / 4294967296.0) - 0.5) * 0.2)
                                    + p.u_right - 0.5)::int)) as correct
  from picked p join units u on u.unit = p.unit cross join seed_6m_today t
),
ins as (
  insert into quiz_results (correct_count, quiz_date, total_count, subject_id, user_id, unit, question_seconds)
  select s.correct, s.day, 3, s.subject_id, s.user_id, s.unit,
         (select string_agg((s.sec_lo + floor((('x' || substr(md5((s.session_id || '|q' || g)::text), 1, 8))::bit(32)::bigint / 4294967296.0) * (s.sec_hi - s.sec_lo)))::int::text, ',' order by g)
          from generate_series(1, 3) as g)
  from scored s
  where not exists (select 1 from quiz_results q
                    where q.user_id = s.user_id and q.quiz_date = s.day and q.subject_id = s.subject_id and q.unit = s.unit)
  returning id
)
insert into seed_6m_rows (tbl, row_id) select 'quiz_results', id from ins;

-- 20) 이번 주에 고른 것: 최근 8주 + 이번 주. 여덟 주에 일곱 주쯤 골랐고, 고른 시각은 전 주 일요일 밤입니다.
--     (FE 의 주간 리포트 [다음 주에 하나만 해 볼까요?] 보기와 같은 글)
with opts(n, title, hint) as (
  values (0, '폰을 다른 방에 두기', '손이 안 닿으면 덜 멈춰요'),
         (1, '시작할 때 폰 뒤집어 두기', '알림이 눈에 덜 띄어요'),
         (2, '책상에 앉으면 타이머부터 켜기', '시작이 제일 어려워요'),
         (3, '가장 쉬운 카드부터 하기', '첫 카드를 끝내면 다음이 쉬워요'),
         (4, '지금 하던 대로 1주 더', '잘 되는 건 그대로 두는 게 좋아요'),
         (5, '카드 하나를 10분 늘려 보기', '조금씩 늘리면 부담이 덜해요')
),
weeks as (
  select u.id as user_id, w::date as week_start
  from users u
  cross join seed_6m_today t
  cross join generate_series(date_trunc('week', t.today - 56), date_trunc('week', t.today), interval '7 days') w
  where exists (select 1 from cards c where c.user_id = u.id and c.id in (select row_id from seed_6m_rows where tbl = 'cards'))
    and (('x' || substr(md5((u.id || '|' || w::date || '|pick')::text), 1, 8))::bit(32)::bigint / 4294967296.0) < 0.87
),
ins as (
  insert into weekly_picks (skip, week_start, created_at, decided_at, updated_at, user_id, hint, title)
  select false, w.week_start, x.at, x.at, x.at, w.user_id, o.hint, o.title
  from weeks w
  join opts o on o.n = floor((('x' || substr(md5((w.user_id || '|' || w.week_start || '|opt')::text), 1, 8))::bit(32)::bigint / 4294967296.0) * 6)::int
  cross join lateral (select ((w.week_start - 1) + time '21:30') at time zone 'Asia/Seoul' as at) x
  on conflict (user_id, week_start) do nothing
  returning id
)
insert into seed_6m_rows (tbl, row_id) select 'weekly_picks', id from ins;

-- 계산용 표 정리
drop table seed_6m_weeks;
drop table seed_6m_daysum;
drop table seed_6m_plan;
drop table seed_6m_days;
drop table seed_6m_new_users;
drop table seed_6m_today;

commit;

-- 결과: 이번에 표마다 몇 줄 더했는지
select r.tbl, count(*) - coalesce(max(b.before_rows), 0) as rows_added
from seed_6m_rows r left join seed_6m_before b on b.tbl = r.tbl
group by r.tbl order by r.tbl;

drop table seed_6m_before;
