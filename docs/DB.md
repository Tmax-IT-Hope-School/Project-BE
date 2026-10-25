# DB 다이어그램

우리 DB 에 어떤 테이블이 있고 서로 어떻게 이어지는지 **그림 한 장**으로 보는 문서입니다.

- 테이블은 각 기능을 맡은 사람이 **직접 설계**합니다.
- 테이블(엔티티)을 만들거나 고칠 때마다 Claude Code 에서 **`/db`** 를 치면, 지금 엔티티를 읽어 아래 그림을 새로 그리고 브라우저로도 열어 줍니다.
- GitHub 에서 이 문서를 열면 그림으로 보입니다.

## 지금 테이블

<!-- DB-DIAGRAM:START — /db 명령이 이 사이를 새로 씁니다 -->

```mermaid
erDiagram
    users ||--o{ lock_events : "user_id"
    lock_events {
        bigint id PK "해제 기록 번호"
        bigint user_id FK "기록의 주인"
        timestamptz occurred_at "해제 시각"
        varchar app_name "앱 이름"
        varchar reason "한 줄 이유"
    }
    users ||--o{ routines : "user_id"
    subjects ||--o{ routines : "subject_id"
    users ||--o{ routine_applications : "user_id"
    routines ||--o{ study_plans : "routine_id"
    routines ||--o{ routine_applications : "routine_id"
    routines {
        bigint id PK "루틴 번호"
        bigint user_id FK "주인"
        varchar weekdays "반복 요일"
        varchar kind "공부 또는 놀이"
        bigint subject_id FK "과목"
        varchar content "할 일"
        int target_minutes "목표 분"
        time start_time "시작 시각"
    }
    routine_applications {
        bigint id PK "적용 기록 번호"
        bigint user_id FK "주인"
        bigint routine_id FK "원본 루틴 번호 (삭제 후에도 번호 유지)"
        date plan_date "한 번 적용한 날짜"
    }
    users ||--o{ subjects : "user_id"
    users ||--o{ study_plans : "user_id"
    users ||--o{ study_sessions : "user_id"
    users ||--o{ accounts : "user_id"
    users ||--o{ login_tokens : "user_id"
    users ||--o{ messages : "user_id"
    users ||--o{ call_records : "user_id"
    users ||--o{ thinking_tests : "user_id"
    users ||--o{ push_subscriptions : "user_id"
    users ||--o{ consent_records : "user_id"
    users ||--o{ onboarding_answers : "user_id"
    users ||--o| initial_plans : "user_id"
    users ||--o{ daily_care_stages : "user_id"
    users ||--o{ daily_signals : "user_id"
    thinking_tests ||--o{ thinking_test_scores : "thinking_test_id"
    thinking_tests ||--o{ thinking_test_weaknesses : "thinking_test_id"
    users ||--o{ psych_tests : "user_id"
    users ||--o{ fixed_blocks : "user_id"
    users ||--o{ goals : "user_id"
    goals ||--o{ goal_target_snapshots : "goal_id"
    goals ||--o{ cards : "goal_id"
    users ||--o{ weekly_goals : "user_id"
    users ||--o{ weekly_picks : "user_id"
    goals |o--o{ exams : "goal_id"
    psych_tests ||--o{ psych_test_answers : "psych_test_id"
    onboarding_answers ||--o{ onboarding_answer_failure_causes : "onboarding_answer_id"
    onboarding_answers ||--o{ onboarding_answer_subject_levels : "onboarding_answer_id"
    subjects ||--o{ study_plans : "subject_id"
    subjects ||--o{ study_sessions : "subject_id"
    subjects ||--o{ cards : "subject_id"
    subjects ||--o{ onboarding_answer_subject_levels : "subject_id"
    users ||--o{ exams : "user_id"
    users ||--o{ experiments : "user_id"
    experiments ||--o{ experiment_cards : "experiment_id"
    cards ||--o{ experiment_cards : "card_id"
    users ||--o{ exam_scores : "user_id"
    exams ||--o{ exam_scores : "exam_id"
    subjects ||--o{ exam_scores : "subject_id"
    users ||--o{ reviews : "user_id"
    exams ||--o{ reviews : "exam_id"
    users ||--o| care_settings : "user_id"
    users ||--o{ focus_lock_apps : "user_id"
    users ||--o{ setting_changes : "user_id"
    users ||--o{ gpt_usages : "user_id"
    call_records ||--o| gpt_usages : "call_record_id"
    users ||--o{ insights : "user_id"
    insights ||--o{ insight_evidence_dates : "insight_id"
    users ||--o{ checkins : "user_id"
    users ||--o{ cards : "user_id"
    cards ||--o{ card_checklist_items : "card_id"
    cards ||--o{ card_expected_fail_causes : "card_id"
    cards ||--o{ study_sessions : "card_id"
    study_sessions ||--o{ session_pauses : "study_session_id"
    study_sessions ||--o{ session_checked_items : "study_session_id"
    study_sessions ||--o{ session_good_reasons : "study_session_id"
    study_sessions ||--o{ session_memos : "study_session_id"
    users ||--o{ card_day_marks : "user_id"
    cards ||--o{ card_day_marks : "card_id"
    users ||--o{ rest_days : "user_id"
    users ||--o{ quiz_results : "user_id"
    subjects ||--o{ quiz_results : "subject_id"
    checkins ||--o{ checkin_emotions : "checkin_id"
    users ||--o{ diaries : "user_id"
    diaries ||--o{ diary_emotions : "diary_id"
    diaries ||--o{ diary_causes : "diary_id"
    diaries ||--o{ diary_keywords : "diary_id"
    users ||--o{ calendar_alarms : "user_id"
    users ||--o{ answer_checks : "user_id"
    users ||--o{ answer_check_items : "user_id"
    answer_checks ||--o{ answer_check_items : "answer_check_id"
    users ||--o{ reflection_prescriptions : "user_id"
    users ||--o{ risk_forecasts : "user_id"
    risk_forecasts ||--o{ risk_forecast_reasons : "risk_forecast_id"

    users {
        bigint id PK "사용자를 구분하는 고유 번호"
        varchar nickname "화면에 보이는 이름 (실명 아님, 50자까지)"
        timestamptz created_at "가입한 시각"
        timestamptz onboarded_at "온보딩을 마친 시각 (안 마쳤으면 비어 있음)"
    }
    subjects {
        bigint id PK "과목을 구분하는 고유 번호"
        bigint user_id FK "이 과목의 주인"
        varchar name "과목 이름 (30자까지, 같은 사용자 안에서 중복 불가)"
        varchar color "과목 태그 색 (RED, ORANGE, GREEN, BLUE, PURPLE 중 하나)"
    }
    study_plans {
        bigint id PK "계획을 구분하는 고유 번호"
        bigint user_id FK "이 계획의 주인"
        bigint subject_id FK "어떤 과목의 계획인지 (노는 계획이면 비어 있음)"
        date plan_date "계획한 날짜"
        varchar content "할 일 내용 (200자까지)"
        int target_minutes "목표 공부 시간(분)"
        boolean done "다 했으면 true, 아직이면 false"
        time start_time "시작할 시각, 몇 시 몇 분 (안 정했으면 비어 있음)"
        varchar kind "공부 계획인지 노는 계획인지 (STUDY, PLAY 중 하나)"
        bigint routine_id FK "어떤 루틴이 만든 계획인지 (직접 만들었으면 비어 있음)"
    }
    study_sessions {
        bigint id PK "기록을 구분하는 고유 번호"
        bigint user_id FK "이 기록의 주인"
        bigint subject_id FK "어떤 과목을 공부했는지 (노는 기록, 과목 없는 카드로 켠 기록이면 비어 있음. 카드로 켜고 과목을 안 보내면 카드의 과목, #404)"
        timestamptz started_at "공부를 시작한 시각"
        timestamptz ended_at "공부를 끝낸 시각"
        int pause_count "일시정지를 누른 횟수"
        bigint paused_ms "일시정지해 있던 시간의 합, 밀리초 (없으면 0, 순공에서 뺌)"
        bigint plan_id "어느 계획을 하려고 켠 타이머인지 (계획 없이 켰으면 비어 있음)"
        int target_minutes "시작할 때 약속한 목표 시간(분) (목표 없이 켰으면 비어 있음)"
        varchar stop_reason "목표 전에 그만둔 이유 (TIRED, EVENT, PHONE, TOO_MUCH, UNWILLING, SICK, PROCRASTINATED, ETC 중 하나, 안 골랐으면 비어 있음)"
        varchar kind "공부 기록인지 노는 기록인지 (STUDY, PLAY 중 하나)"
        bigint card_id FK "어느 카드의 세션인지 (옛 기록이면 비어 있음, #224)"
        varchar mode "잰 방법 (NORMAL 타이머, OFFLINE 오프라인, SELF_REPORT 사후 입력 중 하나, 옛 기록은 비어 있음 = NORMAL)"
        boolean ended_early "예정 종료 전에 끝내기를 눌렀으면 true (옛 기록은 비어 있음)"
        boolean end_reported "종료 팝업에 답했으면 true (팝업을 닫았거나 옛 기록이면 비어 있음)"
        varchar shortfall_reason "체크리스트를 다 못 한 이유 (TOO_MUCH, STUCK, PHONE, MIND_WANDER, TIRED, EXTERNAL, ETC 중 하나), 비어도 됨"
        varchar with_what "같이 한 것 (MUSIC, VIDEO, NONE 중 하나), 비어도 됨"
        varchar proof_link "산출물 증빙 링크 (300자까지), 비어도 됨"
        varchar proof_summary "산출물 증빙 2~3줄 요약 (300자까지), 비어도 됨"
        boolean proof_required "관리 4단계라서 증빙이 꼭 필요했으면 true (옛 기록은 비어 있음 = false, #234)"
        varchar play_tag "노는 기록일 때 무엇을 했는지 (YOUTUBE, GAME, SNS, WEBTOON, REST, ETC 중 하나)"
        bigint over_rest_ms "노는 기록일 때 놀기가 끝난 뒤 더 쉰 시간(밀리초)"
    }
    session_pauses {
        bigint study_session_id FK "어느 기록의 일시정지인지"
        int position "몇 번째 일시정지인지 (0 부터)"
        timestamptz started_at "일시정지를 누른 시각"
        timestamptz ended_at "다시 시작한 시각 (멈춘 채 끝냈으면 기록이 끝난 시각)"
        varchar tag "이유 (TOILET, SNACK, PHONE, MIND_WANDER, STUCK, EXTERNAL, OTHER 중 하나)"
        varchar note "기타를 골랐을 때 쓴 글 (100자까지), 비어도 됨"
    }
    session_checked_items {
        bigint study_session_id FK "어느 기록에서 체크했는지"
        varchar item_key "체크한 카드 체크리스트 항목의 id (40자까지)"
        timestamptz checked_at "체크한 시각"
    }
    session_good_reasons {
        bigint study_session_id FK "어느 기록의 잘 된 이유인지"
        varchar good_reason "잘 된 이유 (FOCUS, GOOD_PLAN, CONDITION, ENVIRONMENT, HELPED, LUCK 중 하나)"
    }
    session_memos {
        bigint study_session_id FK "어느 기록에서 쓴 메모인지"
        int position "몇 번째 메모인지 (0 부터)"
        timestamptz written_at "쓴 시각"
        varchar text "딴생각 주차장 한 줄 (100자까지)"
    }
    accounts {
        bigint id PK "계정을 구분하는 고유 번호"
        bigint user_id FK "이 계정의 주인 (사용자 한 명에 계정은 하나)"
        varchar email "로그인할 때 쓰는 이메일 (소문자로 저장, 중복 불가, 100자까지)"
        varchar password_hash "비밀번호를 되돌릴 수 없게 바꾼 값 (진짜 비밀번호는 저장하지 않음, 구글로 가입했으면 비어 있음)"
        varchar google_subject "구글로 가입했으면 구글이 정해 준 고유 번호 (중복 불가, 이메일로 가입했으면 비어 있음)"
        varchar name "실명 (20자까지, 비어 있을 수 있음. 실명은 받지 않는 방향이라 FE 가 입력을 없애면 이 칸을 지움)"
        varchar school_code "고른 고등학교의 번호 (안 골랐으면 비어 있음)"
        varchar school_name "고른 고등학교의 이름 (안 골랐으면 비어 있음)"
        boolean diary_analysis_agreed "일기·감정 기록을 분석에 써도 된다고 했으면 true (선택 동의)"
        timestamptz agreed_at "이용약관과 개인정보 수집·이용에 동의한 시각 (필수 동의)"
    }
    login_tokens {
        bigint id PK "토큰을 구분하는 고유 번호"
        bigint user_id FK "이 토큰의 주인"
        varchar token_hash "토큰의 해시 (토큰 원문은 저장하지 않음, 중복 불가)"
        timestamptz expires_at "이 시각이 지나면 쓸 수 없음 (로그인하고 30일 뒤)"
        timestamptz created_at "토큰을 내준 시각 (= 로그인한 시각)"
    }
    messages {
        bigint id PK "메시지를 구분하는 고유 번호 (나중에 보낸 것일수록 큼)"
        bigint user_id FK "이 메시지를 받은 사람"
        varchar kind "메시지 종류 (CARROT, NUDGE, STICK, EVENT, RECOVERY, CALL 중 하나)"
        varchar text "메시지 내용 (500자까지)"
        timestamptz sent_at "메시지를 보낸 시각"
        timestamptz read_at "처음 읽은 시각 (아직 안 읽었으면 비어 있음)"
    }
    call_records {
        bigint id PK "통화를 구분하는 고유 번호"
        bigint user_id FK "이 통화를 한 사람"
        date call_date "통화한 날 (새벽 4시에 날짜가 바뀜)"
        timestamptz started_at "통화를 시작한 시각 (임시 키를 받은 시각)"
        varchar channel "음성 통화(VOICE)인지 상담 채팅(CHAT)인지 (이 칸이 생기기 전 기록은 VOICE)"
        varchar asked_bias "이번 통화에서 확인하려던 생각 습관 (PLANNING_FALLACY, FUTURE_SELF, WHAT_THE_HELL, EMOTIONAL_REASONING, EXTERNAL_BLAME, ALL_OR_NOTHING, MORAL_LICENSING, OVERGENERALIZING 중 하나, 없으면 비어 있음)"
        varchar asked_question_key "고른 기본 질문의 이름 (예: PLANNING_FALLACY-1)"
        timestamptz finished_at "통화를 마무리한 시각 (마무리 전이면 비어 있음)"
        varchar summary "통화 요약 2~3문장 (300자까지, 대화 원문은 저장하지 않음)"
        varchar found_bias "대답에서 보인 생각 습관 (asked_bias 와 같은 값 중 하나, 없으면 비어 있음)"
        varchar found_evidence "그 습관이 보인 근거 한 줄 (100자까지)"
        varchar actual_question "AI 가 실제로 한 생각 습관 질문 (200자까지)"
        varchar miss_reason "어제 계획을 못 채운 이유 (TIRED, EVENT, PHONE, TOO_MUCH, UNWILLING, SICK, PROCRASTINATED, ETC 중 하나)"
        varchar promise_if "오늘의 약속 '만약 ~하면' (50자까지)"
        varchar promise_then "오늘의 약속 '~한다' (50자까지)"
        boolean promise_kept "지난 약속을 지켰다고 했는지 (이야기하지 않았으면 비어 있음)"
        boolean crisis_detected "위기 표현이 감지됐는지 (감지되면 내용 칸은 비워 둠)"
    }
    thinking_tests {
        bigint id PK "테스트 결과를 구분하는 고유 번호 (나중에 한 것일수록 큼)"
        bigint user_id FK "테스트를 한 사람"
        timestamptz taken_at "테스트를 마친 시각"
        int test_version "테스트 모양 번호 (2 = 리커트 7점 척도)"
    }
    thinking_test_scores {
        bigint thinking_test_id FK "어느 테스트의 점수인지"
        varchar habit "생각 습관 이름 (PLANNING_FALLACY 등 8개 중 하나)"
        double average "그 습관의 평균 (1 ~ 7, 역문항은 뒤집어 센 값)"
    }
    push_subscriptions {
        bigint id PK "구독을 구분하는 고유 번호"
        bigint user_id FK "알림을 받을 사람"
        varchar endpoint "푸시를 보낼 브라우저 회사의 주소 (500자까지, 중복 불가, 기기마다 하나)"
        varchar p256dh "알림 내용을 잠그는 브라우저의 공개 열쇠 (200자까지)"
        varchar auth "알림 내용을 잠그는 비밀 값 (100자까지)"
        timestamptz subscribed_at "구독을 저장한(다시 저장한) 시각"
    }
    thinking_test_weaknesses {
        bigint thinking_test_id FK "어느 테스트의 약점인지"
        varchar weakness "내가 꼽은 약점 칩 (GAME, PHONE, VIDEO, SLEEP, LAZY, DELAY, FOCUS, FRIENDS, TIRED, MOOD, PLANNING 중 하나)"
    }
    goals {
        bigint id PK "목표를 구분하는 고유 번호 (카드의 goal_id 가 가리킴)"
        bigint user_id FK "목표의 주인"
        varchar kind "유형 (SCORE 점수형, COMPLETION 완주형)"
        varchar title "W 소망 - 목표 한 줄 (40자까지)"
        date d_day "D-day"
        varchar current_score "점수형의 지금 점수 · 등급 (20자까지, 완주형이면 비어 있음)"
        int total_amount "완주형의 전체 분량 (점수형이면 비어 있음)"
        varchar amount_unit "완주형의 분량 단위 (5자까지)"
        varchar benefit "O 결과 - 이루면 좋은 점"
        varchar cost "O 결과 - 안 하면 생길 일"
        varchar obstacle "O 방해물 - 내 안의 방해물 1개"
        varchar plan "P 계획 - 방해물이 생겼을 때의 대처"
        varchar status "상태 (ACTIVE 진행, DONE 완료, GIVEN_UP 포기)"
        varchar result "D-day 뒤에 적은 결과"
        varchar given_up_reason "포기한 이유"
        timestamptz created_at "목표를 만든 시각"
    }
    weekly_goals {
        bigint id PK "이번 주 목표 한 줄을 구분하는 고유 번호"
        bigint user_id FK "목표의 주인 (한 사람의 한 주에 한 줄)"
        date week_start "어느 주인지 (그 주의 월요일)"
        varchar kind "종류 (COMPLETION 완주율, START_DELAY 시작 지연, PLAN_RATE 계획 채우기, KEEP 지난주만큼)"
        int target "목표 숫자 (시작 지연이면 분, 나머지는 %)"
        int baseline "제안할 때 본 지난주 숫자"
        varchar status "판정 결과 (ACHIEVED 달성, MISSED 미달, UNKNOWN 알 수 없음). 판정 전이면 비어 있음"
        int result_value "판정할 때 본 그 주의 숫자"
        timestamptz judged_at "판정한 시각"
        timestamptz created_at "목표를 정한(덮어쓴) 시각"
    }
    weekly_picks {
        bigint id PK "줄을 구분하는 고유 번호"
        bigint user_id FK "이 줄의 주인 (한 사람의 한 주에 한 줄)"
        date week_start "해 보는 주 (그 주의 월요일)"
        varchar title "해 볼 것 한 줄 (60자까지)"
        varchar hint "왜 하는지 짧게 (60자까지)"
        boolean skip "이번 주는 넘어가기를 골랐으면 true"
        timestamptz decided_at "학생이 고른 시각 (FE 가 보낸 값, 없으면 저장한 시각)"
        timestamptz created_at "이 줄을 처음 만든 시각"
        timestamptz updated_at "마지막으로 덮어쓴 시각"
    }
    goal_target_snapshots {
        bigint goal_id FK "어느 목표의 이력인지"
        int position "몇 번째로 고쳤는지 (0부터)"
        varchar title "고치기 전의 목표 한 줄"
        date d_day "고치기 전의 D-day"
        varchar current_score "고치기 전의 지금 점수"
        int total_amount "고치기 전의 전체 분량"
        varchar amount_unit "고치기 전의 단위"
        timestamptz changed_at "이 값에서 바꾼 시각"
        varchar reason "왜 바꿨는지"
    }
    fixed_blocks {
        bigint id PK "고정 일정을 구분하는 고유 번호"
        bigint user_id FK "일정의 주인"
        varchar title "무슨 일정인지 (학교, 수학 학원 등 20자까지)"
        varchar repeat_days "반복 요일을 쉼표로 이은 글자 (0 일요일 ~ 6 토요일, 예 1,2,3,4,5)"
        time start_time "시작 시각"
        time end_time "끝 시각 (하루 시작 새벽 4시 기준으로 시작보다 뒤)"
        timestamptz created_at "일정을 만든 시각"
    }
    psych_tests {
        bigint id PK "심리검사 결과를 구분하는 고유 번호 (나중에 한 것일수록 큼)"
        bigint user_id FK "검사를 한 사람"
        timestamptz taken_at "검사를 마친 시각"
    }
    psych_test_answers {
        bigint psych_test_id FK "어느 검사의 답인지"
        varchar item "문장 코드 (S1 ~ S5, A1 ~ A5, P1 ~ P5, R1 ~ R5, F1 ~ F5, E1 ~ E5, C1 ~ C5 중 하나)"
        int answer "그 문장의 답 (1 전혀 아니다 ~ 7 매우 그렇다)"
    }
    onboarding_answers {
        bigint id PK "설문을 구분하는 고유 번호"
        bigint user_id FK "누구의 설문인지"
        timestamptz answered_at "답한 시각 (가장 처음 줄과 가장 최근 줄을 이것으로 구분)"
        varchar grade "학년 (HIGH_1, HIGH_2, HIGH_3, REPEATER, UNIVERSITY 중 하나)"
        varchar target_exam "목표로 하는 시험 이름 (30자까지, 선택 — 새 온보딩은 묻지 않음)"
        date target_exam_date "목표 시험 날짜 (D-day 계산용, 선택)"
        varchar target_university "목표 대학 (30자까지, 비어 있을 수 있음)"
        varchar target_score "목표 점수 (30자까지, 비어 있을 수 있음)"
        time wake_up_time "평소 기상 시각"
        time sleep_time "평소 취침 시각"
        time school_end_time "학교 끝나는 시각"
        time academy_end_time "학원 끝나는 시각"
        varchar study_place "주로 공부하는 장소 (HOME, SCHOOL, ACADEMY, LIBRARY, CAFE, OTHER 중 하나)"
        varchar chronotype "[자기 인식] 아침형/저녁형 (MORNING, NIGHT 중 하나)"
        int max_focus_minutes "[자기 인식] 한 번에 집중 가능한 시간(분) (30, 50, 90, 120 중 하나)"
        varchar biggest_distraction "[자기 인식] 가장 큰 방해 요소 (PHONE, SLEEPY, WANDERING_MIND, PEOPLE, NOISE, OTHER 중 하나)"
        int daily_available_minutes "[자기 인식] 하루 공부 가능 시간(분) (60, 120, 180, 240, 300 중 하나)"
        varchar motivation_reason "공부하는 이유 (50자까지)"
        varchar goal_reward "목표를 이루면 하고 싶은 것 (50자까지)"
    }
    onboarding_answer_failure_causes {
        bigint onboarding_answer_id FK "어느 설문의 답인지"
        varchar cause "계획이 무너졌던 때 (AFTER_EXAM, WEEKEND, LATE_ACADEMY, PHONE, TOO_MUCH_PLAN 중 하나)"
    }
    onboarding_answer_subject_levels {
        bigint onboarding_answer_id FK "어느 설문의 답인지"
        bigint subject_id FK "어떤 과목인지"
        int grade "최근 등급 (1 ~ 9, 점수로 적었으면 비어 있음)"
        int score "최근 점수 (0 ~ 100, 등급으로 적었으면 비어 있음)"
        int confidence "이 과목에 대한 자신감 (1 ~ 5)"
    }
    initial_plans {
        bigint id PK "처음 계획을 구분하는 고유 번호"
        bigint user_id FK "누구의 처음 계획인지 (한 사람에 한 줄, 중복 불가)"
        date created_date "처음 계획을 세운(다시 세운) 날짜"
        int daily_minutes "하루에 공부하기로 한 시간(분) (60, 120, 180, 240, 300 중 하나)"
        int days_per_week "일주일에 며칠 하기로 했는지 (3, 5, 6, 7 중 하나)"
    }
    daily_care_stages {
        bigint id PK "줄을 구분하는 고유 번호"
        bigint user_id FK "누구의 단계인지"
        date stage_date "어느 날의 단계인지 (같은 사람 안에서 중복 불가)"
        integer stage "관리 단계 0~4"
        varchar rule "어느 규칙으로 정했는지 (SAFETY, RECOVERY, UP ...)"
        boolean suggest_shrink "계획을 줄여 보자고 할지"
        varchar reason "왜 이 단계인지 한 줄"
        timestamptz decided_at "판정한 시각"
        bigint forced_card_id "4단계라서 그날 하루만 절대 카드로 보는 카드 번호, 없거나 풀렸으면 비어 있음 (#234)"
        date forced_card_date "그 카드를 절대로 보는 날짜, 비어도 됨"
        timestamptz released_at "4단계를 푼 시각 (증빙 있는 실행 1회 → 3단계), 안 풀렸으면 비어 있음"
    }
    daily_signals {
        bigint id PK "줄을 구분하는 고유 번호"
        bigint user_id FK "이 데이터의 주인"
        date signal_date "어느 날의 데이터인지 (같은 사람 안에서 날짜 중복 불가)"
        int sleep_minutes "실제로 잔 시간(분), 모르면 비어 있음"
        timestamptz slept_at "잠든 시각, 모르면 비어 있음"
        timestamptz woke_at "일어난 시각, 모르면 비어 있음"
        int steps "하루 걸음 수, 모르면 비어 있음"
        boolean sns_over_thirty_minutes "SNS 를 30분 넘게 썼는지, 모르면 비어 있음"
    }
    exams {
        bigint id PK "시험을 구분하는 고유 번호"
        bigint user_id FK "이 시험의 주인"
        varchar name "시험 이름 (50자까지)"
        varchar type "시험 종류 (MOCK, SCHOOL, QUIZ, ETC 중 하나)"
        date exam_date "시험 날짜"
        bigint goal_id FK "이어진 점수형 목표 (없으면 비어 있음, 이슈 #228)"
    }
    experiments {
        bigint id PK "실험을 구분하는 고유 번호"
        bigint user_id FK "누구의 실험인지"
        varchar hypothesis "가설 한 줄 (100자까지)"
        varchar cause "가설의 원인 (카드 미실행 원인 값), 모르면 비어 있음"
        varchar strategy "고른 전략 (PHONE_FLIP 등 10개 중 하나)"
        date start_date "시작한 날, 이날부터 14일"
        int min_gain_pp "그만둘 기준(%p), 시작 뒤 바꾸지 않음"
        varchar status "RUNNING, EFFECTIVE, NO_EFFECT, UNCLEAR"
        float baseline_rate "판정 때 시작 전 14일 평균 실행률, 판정 전이면 비어 있음"
        float experiment_rate "판정 때 실험 14일 평균 실행률, 판정 전이면 비어 있음"
        timestamptz judged_at "판정한 시각, 진행 중이면 비어 있음"
        timestamptz created_at "실험을 저장한 시각"
    }
    experiment_cards {
        bigint experiment_id FK "어느 실험인지"
        bigint card_id "실험 규칙을 붙인 카드 (cards.id)"
    }
    exam_scores {
        bigint id PK "점수 한 줄을 구분하는 고유 번호"
        bigint user_id FK "이 점수의 주인"
        bigint exam_id FK "어느 시험의 점수인지"
        bigint subject_id FK "어느 과목의 점수인지 (같은 시험에 같은 과목은 한 번만)"
        int expected_score "시험 전에 적은 예상 점수 (0 ~ 100)"
        int actual_score "시험 후에 적은 실제 점수 (0 ~ 100, 아직 안 적었으면 비어 있음)"
    }
    reviews {
        bigint id PK "회고를 구분하는 고유 번호"
        bigint user_id FK "이 회고의 주인"
        varchar type "회고 종류 (EXAM, WEEKLY 중 하나)"
        bigint exam_id FK "어느 시험의 회고인지 (시험 후 회고만, 같은 사람 안에서 중복 불가)"
        date week_start "어느 주의 회고인지, 그 주의 월요일 (주간 회고만, 같은 사람 안에서 중복 불가)"
        varchar answer1 "1번 질문의 답 (200자까지, 건너뛰면 빈 글자)"
        varchar answer2 "2번 질문의 답 (200자까지, 건너뛰면 빈 글자)"
        varchar answer3 "3번 질문의 답 (200자까지, 건너뛰면 빈 글자)"
        timestamptz saved_at "마지막으로 저장한 시각"
    }
    care_settings {
        bigint id PK "설정 한 줄을 구분하는 고유 번호"
        bigint user_id FK "이 설정의 주인 (사용자 한 명에 한 줄, 중복 불가)"
        time call_time "AI 가 전화하는 시각"
        int daily_message_count "하루에 받을 AI 메시지 수 (0 ~ 5)"
        time quiet_start "연락 금지 시작 시각"
        time quiet_end "연락 금지 끝 시각"
        int max_harshness "허용하는 독설 수위 (1 ~ 5)"
        boolean allow_app_lock "앱 잠금(집중 모드)을 허용하는지"
        int max_care_level "지금 적용 중인 제재 한도 (0 ~ 4)"
        int pending_max_care_level "올렸는데 아직 적용 전인 한도 (없으면 비어 있음)"
        date pending_effective_date "바뀔 한도가 적용되는 날짜 (없으면 비어 있음)"
    }
    focus_lock_apps {
        bigint id PK "한 줄을 구분하는 고유 번호"
        bigint user_id FK "이 앱을 고른 사람"
        varchar app_name "잠글 앱 이름 (30자까지, 같은 사용자 안에서 중복 불가)"
    }
    setting_changes {
        bigint id PK "기록 한 줄을 구분하는 고유 번호 (나중에 바꾼 것일수록 큼)"
        bigint user_id FK "설정을 바꾼 사람"
        timestamptz changed_at "바꾼 시각"
        varchar field "바꾼 칸의 이름 (API 의 JSON 이름, 예: maxHarshness)"
        varchar old_value "바꾸기 전 값 (글자, 1000자까지)"
        varchar new_value "바꾼 뒤 값 (글자, 1000자까지)"
    }
    school_events {
        bigint id PK "일정 한 줄을 구분하는 고유 번호"
        varchar school_code "어느 학교의 일정인지 (NEIS 학교 번호, accounts 의 school_code 와 같은 값)"
        date event_date "행사 날짜"
        varchar name "행사 이름 (NEIS 가 준 그대로, 100자까지, 같은 학교 같은 날에 같은 이름은 한 번만)"
        varchar content "행사 설명 (NEIS 가 준 그대로, 200자까지, 없으면 비어 있음)"
        varchar grades "해당 학년 (예: 1,2 / 학년 구분이 없으면 빈 글자)"
    }
    school_event_fetches {
        varchar school_code PK "학교 번호 (학교마다 한 줄)"
        varchar office_code "그 학교의 교육청 코드 (NEIS 에 물을 때 같이 보냄, 예: J10)"
        timestamptz fetched_at "NEIS 에서 마지막으로 받은 시각 (24시간이 지나면 새로 받음)"
    }
    gpt_usages {
        bigint id PK "기록 한 줄을 구분하는 고유 번호 (나중에 부른 것일수록 큼)"
        bigint user_id FK "GPT 를 부르게 한 사람"
        varchar kind "무엇 때문에 불렀는지 (CALL, CALL_REVIEW, COACH, PLAN_PARSE, CHAT, CHECKLIST, ASSIST 중 하나)"
        timestamptz called_at "부른 시각 (통화는 시작한 시각)"
        bigint call_record_id FK "어느 통화인지 (통화일 때만, 그 외엔 비어 있음)"
        int call_seconds "통화한 시간(초) (통화가 아니거나 아직 안 끝났으면 비어 있음)"
        int cost_won "대략적인 비용(원) (통화가 아직 안 끝났으면 비어 있음)"
    }
    checkins {
        bigint id PK "줄을 구분하는 고유 번호"
        bigint user_id FK "이 체크인의 주인"
        date check_in_date "어느 날의 체크인인지 (같은 사람 안에서 날짜 중복 불가)"
        varchar source "어디서 만든 체크인인지 (MANUAL, CALL, QUICK 중 하나)"
        int condition "오늘 컨디션 (1 ~ 5), 안 골랐으면 비어 있음"
        int mood "오늘 기분 (1 ~ 5), 안 골랐으면 비어 있음"
        varchar miss_reason "어제 못 채운 이유 (TIRED, EVENT, PHONE, TOO_MUCH, UNWILLING, SICK, PROCRASTINATED, ETC 중 하나), 비어도 됨"
        varchar miss_reason_note "기타 이유 한 줄 (50자까지, ETC 일 때만)"
        int expected_minutes "오늘 예상 순공시간(분), 비어도 됨"
        varchar promise_if "오늘의 약속 만약 ~하면 (100자까지), 비어도 됨"
        varchar promise_then "오늘의 약속 ~한다 (100자까지), 비어도 됨"
        boolean suggestion_kept "개선안을 실천했는지, 비어도 됨"
    }
    checkin_emotions {
        bigint checkin_id FK "어느 체크인의 감정인지"
        varchar emotion "하루 첫 태그에서 고른 감정 (STUCK, ANXIOUS, TIRED, ANNOYED, CALM, MOTIVATED 중 하나)"
    }
    diaries {
        bigint id PK "줄을 구분하는 고유 번호"
        bigint user_id FK "이 일기의 주인 (원문은 본인만 봄)"
        date diary_date "어느 날의 일기인지 (쓴 시각을 새벽 4시 기준 날짜로)"
        varchar content "학생이 쓴 일기 원문 (2000자까지)"
        int mood "일기와 같이 고른 오늘 기분 (1 ~ 5, 체크인과 같은 척도), 비어도 됨"
        timestamptz written_at "일기를 저장한 시각"
        timestamptz analyzed_at "AI 분석을 마친 시각, 분석 안 했으면 비어 있음"
    }
    diary_emotions {
        bigint diary_id FK "어느 일기에서 뽑은 감정인지"
        varchar emotion "AI 가 뽑은 감정 (STUCK, ANXIOUS, TIRED, ANNOYED, CALM, MOTIVATED 중 하나)"
    }
    diary_causes {
        bigint diary_id FK "어느 일기에서 뽑은 원인인지"
        varchar cause "AI 가 뽑은 실패 원인 (TIRED, EVENT, PHONE, TOO_MUCH, UNWILLING, SICK, PROCRASTINATED, ETC 중 하나)"
    }
    diary_keywords {
        bigint diary_id FK "어느 일기에서 뽑은 키워드인지"
        varchar keyword "AI 가 뽑은 반복 키워드 (20자까지)"
    }
    calendar_alarms {
        bigint id PK "줄을 구분하는 고유 번호"
        bigint user_id FK "이 일정의 주인"
        varchar event_key "구글 캘린더의 일정 id (같은 일정을 다시 올렸을 때 찾는 데 씀)"
        varchar title "일정 제목, 알림 글에 들어감 (100자까지)"
        timestamptz starts_at "일정이 시작하는 순간"
        timestamptz handled_at "알림을 보냈거나 연락 금지 시간이라 건너뛴 시각, 아직이면 비어 있음"
    }
    answer_checks {
        bigint id PK "줄을 구분하는 고유 번호"
        bigint user_id FK "이 기록의 주인"
        date check_date "어느 날 공부한 것인지 (올릴 때 보낸 날짜, 보통 오늘)"
        varchar mode "채점 모드 (MARKED 채점했어요, UNMARKED 아직 안 했어요)"
        varchar subject_text "AI 가 사진에서 읽은 과목 (30자까지), 못 읽었으면 비어 있음"
        timestamptz created_at "채점한(사진을 올린) 시각"
    }
    answer_check_items {
        bigint id PK "줄을 구분하는 고유 번호"
        bigint answer_check_id FK "어느 채점 기록의 문제인지"
        bigint user_id FK "이 줄의 주인"
        int sort_order "사진 속 순서 (0 부터)"
        varchar problem_number "문제 번호 글자 (예: 2, 3-1, 20자까지)"
        varchar my_answer "AI 가 읽은 학생 답 (100자까지), 못 읽었으면 비어 있음"
        varchar ai_answer "AI 가 직접 푼 답 (UNMARKED 만, 100자까지)"
        varchar mark_read "AI 가 읽은 채점 표시 O 또는 X (MARKED 만), 못 읽었으면 비어 있음"
        varchar ai_result "AI 의 처음 판정 (CORRECT, WRONG, UNSURE 중 하나), 고쳐도 안 바뀜"
        varchar result "지금 판정 (CORRECT, WRONG, UNSURE 중 하나), 처음엔 ai_result 와 같음"
        varchar correct_answer "학생이 직접 적은 정답 (100자까지)"
        timestamptz edited_at "학생이 직접 고친 시각, 비어 있으면 AI 판정 그대로"
    }
    reflection_prescriptions {
        bigint id PK "처방을 구분하는 고유 번호 (나중에 내린 것일수록 큼)"
        bigint user_id FK "처방을 받은 사람"
        date start_date "처방 첫날 (처방을 내린 날)"
        date end_date "처방 마지막 날 (이 날까지 포함)"
        varchar miss_reason "처방한 이유가 된 못 채운 이유 (TIRED, EVENT, PHONE, TOO_MUCH, UNWILLING, ETC 중 하나)"
        int reason_days "처방할 때 최근 7일 중 그 이유가 나온 날 수 (3 ~ 7)"
        varchar question1 "첫 번째 질문 (100자까지)"
        varchar question2 "두 번째 질문 (100자까지)"
        varchar question3 "세 번째 질문 (100자까지)"
    }
    consent_records {
        bigint id PK "동의 기록을 구분하는 고유 번호 (나중에 한 동의일수록 큼)"
        bigint user_id FK "누가 동의했는지"
        timestamptz agreed_at "동의한 시각"
        varchar text_version "동의한 문구의 버전 (20자까지, 지금은 v1)"
        boolean age_confirmed_over14 "만 14세 이상 확인 (true 면 확인함, 비어 있으면 확인받은 적 없음)"
    }
    cards {
        bigint id PK "카드를 구분하는 고유 번호"
        bigint user_id FK "이 카드의 주인"
        varchar title "무엇을 하는지 (100자까지)"
        date card_date "1회성 카드면 그 날짜, 반복 카드면 반복을 시작한 날짜"
        varchar repeat_days "반복 요일을 쉼표로 이은 글자 (0 일 ~ 6 토, 예 1,3,5). 1회성 카드면 비어 있음"
        time start_time "시작 시각, 몇 시 몇 분 (옛 계획에서 옮긴 카드만 비어 있음)"
        int duration_minutes "길이(분), 1 ~ 600"
        bigint goal_id FK "연결한 목표 번호 (목표 없음이면 비어 있음)"
        bigint subject_id FK "어떤 과목의 카드인지 (과목 없음이면 비어 있음, 과목을 지우면 비워짐, #404)"
        varchar priority "우선순위 (ABSOLUTE 절대, FLEXIBLE 유연 중 하나)"
        varchar type "유형 (SELF 자습, LECTURE 인강 중 하나)"
        int expected_done "예상 달성 개수 (비어 있으면 체크리스트 전부)"
        varchar expected_done_reason "예상 달성 개수를 낮춘 이유 태그 (30자까지), 비어도 됨"
        varchar alert_method "알림 방법 (MESSAGE, CALL 중 하나). 알림 칸이 모두 비면 기본 알림"
        int alert_minutes_before "시작 몇 분 전에 알릴지 (0 ~ 120)"
        varchar alert_text "알림 문구 (100자까지, 비어 있으면 기본 문구)"
        bigint planning_ms "카드 편집 화면을 열고 저장할 때까지 걸린 시간(밀리초), 비어도 됨"
        timestamptz created_at "카드를 만든 시각"
    }
    card_checklist_items {
        bigint card_id FK "어느 카드의 항목인지"
        int position "카드 안에서 몇 번째 항목인지 (0 부터)"
        varchar item_key "항목을 구분하는 글자 (FE 의 ChecklistItem.id, 40자까지)"
        varchar text "할 일 (100자까지)"
    }
    card_expected_fail_causes {
        bigint card_id FK "어느 카드의 예상 실패 원인인지"
        int position "몇 번째 원인인지 (0 부터)"
        varchar cause "예상 실패 원인 글 (100자까지)"
    }
    card_day_marks {
        bigint id PK "표시를 구분하는 고유 번호"
        bigint user_id FK "누구의 표시인지"
        bigint card_id FK "어느 카드인지"
        date mark_date "어느 날의 카드인지 (미루거나 실패한 그 날짜)"
        varchar mark "미루기 POSTPONED 또는 실패 FAILED"
        varchar reason "미루기 이유 100~500자 또는 실패 사유 500자까지 (위기 표현이 있으면 비움)"
        varchar cause "실패 원인 태그 (TIRED, EVENT, PHONE, TOO_MUCH, UNWILLING, SICK, PROCRASTINATED, ETC 중 하나, 실패일 때만)"
        date new_date "미룬 새 날짜 (미루기일 때만)"
        time new_start_time "미룬 새 시작 시각 (미루기일 때만)"
        boolean crisis_detected "이유 글에 위기 표현이 있었으면 true"
        timestamptz created_at "표시를 남긴 시각"
    }
    rest_days {
        bigint id PK "줄을 구분하는 고유 번호"
        bigint user_id FK "누가 쉰 날인지"
        date rest_date "휴식으로 처리한 날짜 (같은 사람 안에서 중복 불가)"
        timestamptz created_at "휴식으로 처리한 시각"
    }
    quiz_results {
        bigint id PK "줄을 구분하는 고유 번호"
        bigint user_id FK "누가 푼 퀴즈인지"
        bigint subject_id FK "어느 과목의 퀴즈인지"
        varchar unit "어느 단원의 퀴즈인지 (퀴즈 한 번 = 한 단원, 30자까지)"
        date quiz_date "퀴즈를 푼 날짜 (새벽 4시 기준)"
        int correct_count "맞은 문제 수"
        int total_count "전체 문제 수"
        varchar question_seconds "문제마다 걸린 시간(초)을 쉼표로 이은 글자 (예: 45,30,62)"
    }
    insights {
        bigint id PK "줄을 구분하는 고유 번호"
        bigint user_id FK "이 인사이트의 주인"
        varchar sentence "카드에 보여 준 한 문장 (200자까지)"
        varchar insight_key "인사이트의 종류, 같은 발견이면 같은 값 (예: morning-vs-night)"
        boolean hypothesis "5일 미만이라 가설로 보여 줬는지"
        date shown_date "보여 준 날"
        varchar reaction "누른 반응 (SURPRISED, ALREADY_KNEW, WRONG 중 하나), 누르기 전에는 비어 있음"
    }
    insight_evidence_dates {
        bigint insight_id FK "어느 인사이트의 근거인지"
        date evidence_date "근거가 된 날짜 (한 인사이트 안에서 중복 불가)"
    }
    risk_forecasts {
        bigint id PK "예측 한 줄을 구분하는 고유 번호"
        bigint user_id FK "이 예측의 주인"
        date forecast_date "어느 날을 예측했는지 (같은 사람 안에서 날짜 중복 불가)"
        varchar risk_level "위험 수준 (NONE, WATCH, HIGH 중 하나)"
        int actual_achievement_rate "그날이 지난 뒤 채우는 실제 달성률 (0 ~ 100, 아직이거나 계획이 없었으면 비어 있음)"
        boolean was_correct "예측이 맞았는지 (실제 달성률을 채울 때 같이 채움, 모르면 비어 있음)"
    }
    risk_forecast_reasons {
        bigint risk_forecast_id FK "어느 예측의 이유인지"
        varchar reason "위험하다고 본 이유 (WEAK_WEEKDAY, LATE_SLEEP 중 하나)"
    }
    users ||--o{ source_problems : "user_id"
    users ||--o{ review_items : "user_id"
    users ||--o{ variants : "user_id"
    users ||--o{ weekly_sets : "user_id"
    users ||--o{ assignments : "user_id"
    users ||--o{ attempts : "user_id"
    source_problems ||--o{ source_problem_choices : "source_problem_id"
    source_problems ||--o{ review_items : "source_problem_id (번호만)"
    source_problems ||--o{ variants : "source_problem_id (번호만)"
    variants ||--o{ variant_choices : "variant_id"
    variants ||--o| assignments : "variant_id (번호만)"
    review_items ||--o{ assignments : "review_item_id (번호만)"
    weekly_sets ||--o{ assignments : "weekly_set_id (번호만)"
    assignments ||--o| attempts : "assignment_id (번호만)"
    source_problems {
        bigint id PK "원본 번호"
        bigint user_id FK "주인"
        varchar problem_text "문제 글 (수식은 $...$)"
        boolean is_correct "학생이 맞혔는지"
        boolean has_figure "그림이 있어야 풀 수 있는지"
        varchar unit_code "단원 코드 (taxonomy.json)"
        varchar type_code "유형 코드 (taxonomy.json)"
        varchar support_status "SUPPORTED, PENDING_CLASSIFICATION, NEEDS_REVIEW, UNSUPPORTED_FIGURE"
        varchar fingerprint "같은 문제인지 보는 지문"
        timestamptz solved_at "푼 순간"
    }
    source_problem_choices {
        bigint source_problem_id FK "어느 원본의 선지인지"
        int position "선지 순서"
        varchar text "선지 글"
    }
    review_items {
        bigint id PK "학습 항목 번호"
        bigint user_id FK "주인"
        bigint source_problem_id "원본 번호"
        bigint active_source_problem_id "ACTIVE 일 때만 원본 번호 (유니크, 같은 원본에 ACTIVE 하나)"
        varchar status "ACTIVE, GRADUATED, MASTERED"
        int current_level "레벨 -1, 0, 1"
        int wrong_streak "지금 레벨의 연속 오답"
        boolean needs_attention "비계에서도 계속 틀림"
        date next_due_date "다음에 낼 날"
    }
    variants {
        bigint id PK "변형 번호 (생성 시도 하나)"
        bigint user_id FK "주인"
        bigint source_problem_id "원본 번호"
        int level "레벨 -1, 0, 1, 2"
        varchar format "MC5, SHORT"
        varchar status "VERIFIED, REJECTED"
        varchar verification_code "AI 가 쓴 Symja 검증 코드 (학생에게 안 보냄)"
        varchar rejection_reason "떨어진 이유"
    }
    variant_choices {
        bigint variant_id FK "어느 변형의 선지인지"
        int position "섞은 뒤 순서"
        varchar value_expr "선지 값 (Symja 식)"
        varchar latex "화면용 LaTeX (샌드박스가 만듦)"
        varchar rationale "어떤 실수인지 (정답은 빈 글자)"
    }
    weekly_sets {
        bigint id PK "세트 번호"
        bigint user_id FK "주인"
        date week_start "돌아보는 주의 월요일 (2026-10-19)"
        date due_date "마감 날"
    }
    assignments {
        bigint id PK "과제 번호"
        bigint user_id FK "주인"
        bigint variant_id "낸 변형 (VERIFIED, 유니크)"
        bigint review_item_id "이어진 학습 항목"
        bigint daily_review_item_id "일일 과제일 때만 항목 번호 (날짜와 함께 유니크)"
        varchar kind "DAILY, WEEKLY_CORRECT_TYPE, WEEKLY_WRONG_DEEP"
        date assigned_date "낸 날"
        date due_date "마감 날"
        varchar status "PENDING, ANSWERED, EXPIRED"
    }
    attempts {
        bigint id PK "답 번호"
        bigint user_id FK "주인"
        bigint assignment_id "어느 과제의 답인지 (유니크, 과제당 한 번)"
        varchar submitted_answer "낸 답"
        boolean is_correct "맞았는지"
    }
```

<!-- DB-DIAGRAM:END -->

## 테이블 설명

### 기능별 반영 현황 (#74)

2026-10-17 `develop`의 엔티티와 아래 그림을 대조했습니다. 체크는 **그림 반영 여부**이며,
운영 DB에 테이블이 만들어졌다는 뜻은 아닙니다. 아직 없는 테이블은 이름·컬럼을 미리 정하지 않습니다.

| 기능 | 그림 반영 | 근거 / 남은 일 |
| --- | --- | --- |
| 사용자·과목·계획·기록 #3 | 완료 | `users`, `subjects`, `study_plans`, `study_sessions` |
| 시험·점수 #16 | 완료 | `exams`, `exam_scores` |
| 회고 #17 | 완료 | `reviews` |
| 온보딩 #21 | 완료 | `onboarding_answers`와 선택 항목, `initial_plans` |
| 관리 설정 #22 | 완료 | `care_settings`, `focus_lock_apps`, `setting_changes` |
| 체크인 #24 | 완료 | `checkins` |
| 통화 요약 #29 | 완료 | `call_records` |
| AI 메시지 #30 | 완료 | `messages` |
| 하루 요약 #34 | 대기 | 현재 `DaySummary` 계산 객체만 있고 저장 엔티티 없음 |
| 개선안·실험 #52 | 완료 | v3 자기 실험 `experiments` · `experiment_cards` (#52 · #54). 옛 v2 개선안 계산 객체는 그대로 |
| 일기 #60 | 완료 | `diaries`와 딸린 `diary_emotions` · `diary_causes` · `diary_keywords` |
| 구글 캘린더 일정 알림 (2026-10-24) | 완료 | `calendar_alarms` |
| 사진 채점 (2026-10-24) | 완료 | `answer_checks`, `answer_check_items` |
| 이번 주에 고른 것 #406 | 완료 | `weekly_picks` |
| 잠금 해제 #67 | 완료 | `lock_events` 엔티티·그림·운영 SQL 반영 |
| 동의 기록 #71 | 완료 | PR #204가 반영되어 `consent_records` 이력과 그림이 있음 |
| 계획 루틴 #85 | 완료 | `routines`, `routine_applications` 엔티티·그림·운영 SQL 반영 |

추가 테이블을 만드는 PR에서는 이 표의 상태도 함께 바꿉니다.
미완료 기능이 있어 #74 전체를 완료 처리하지 않습니다.

| 테이블 | 무엇을 담나 | 만든 이슈 |
| ------ | ----------- | --------- |
| routines | 요일마다 반복할 계획의 틀 | #85 |
| routine_applications | 루틴을 날짜에 적용한 기록 (계획 삭제 후에도 유지) | #85 |
| lock_events | 사용자별 잠금 해제 시각·앱·한 줄 이유 | #67 |
| `users` | 서비스를 쓰는 사람 한 명이 한 줄 (별명, 가입 시각) | #3 |
| `subjects` | 사용자가 만든 과목 하나가 한 줄 (이름, 색) | #3 |
| `study_plans` | 하루의 계획 하나가 한 줄 (날짜, 과목, 내용, 목표 시간, 완료 여부, 시작 시각, 공부/노는 계획, 루틴) | #3 · #12 |
| `study_sessions` | 타이머로 한 번 잰 기록 하나가 한 줄 (과목, 시작·끝 시각, 일시정지 횟수·시간, 계획, 목표 시간, 그만둔 이유, 공부/노는 기록, 그리고 #224 에서 카드 · 잰 방법 · 중도 종료 · 종료 팝업 답 · 노는 종류 · 초과 휴식) | #3 · #13 · #224 |
| `session_pauses` | 기록 하나의 일시정지 한 번이 한 줄씩 (시각 · 이유 태그 · 기타 글, `@ElementCollection`) | #224 |
| `session_checked_items` | 기록 하나에서 체크한 카드 체크리스트 항목이 한 줄씩 (항목 id · 체크한 시각, `@ElementCollection`) | #224 |
| `session_good_reasons` | 기록 하나의 잘 된 이유 태그가 한 줄씩 (`@ElementCollection`) | #224 |
| `session_memos` | 기록 하나의 딴생각 메모가 한 줄씩 (`@ElementCollection`) | #224 |
| `card_day_marks` | 카드 한 장 · 날짜 하나를 미뤘거나(이유 · 새 날짜 · 새 시각) 실패로 기록한(원인 태그 · 사유) 것이 한 줄. 같은 카드 · 같은 날짜에는 한 줄만 | #225 |
| `rest_days` | 한 사람이 하루를 사후에 휴식으로 처리한 것이 한 줄. 같은 날은 한 번만, 한 달에 4번까지 | #225 |
| `quiz_results` | 확인 문제(퀴즈) 한 번이 한 줄 (과목 · 단원 · 날짜 · 맞은 개수 · 전체 개수 · 문제마다 걸린 시간). 통계 탭이 씀. **설계는 학생이 정함** (2026-10-24) | 통계 탭 |
| `accounts` | 가입한 계정 하나가 한 줄 (이메일, 비밀번호 해시 또는 구글 고유 번호, 실명(선택), 학교, 동의). 사용자 한 명에 하나 | #97 · #19 |
| `login_tokens` | 로그인 한 번으로 내준 토큰 하나가 한 줄 (토큰 해시, 끝나는 시각) | #97 |
| `consent_records` | 개인정보 동의 한 번이 한 줄 (동의 시각, 문구 버전, 만 14세 확인). 다시 동의하면 **새 줄이 쌓여** 이력이 남음 | #71 |
| `messages` | AI 가 사용자에게 보낸 메시지 하나가 한 줄 (종류, 내용, 보낸 시각, 읽은 시각) | #30 |
| `call_records` | AI 통화 한 번이 한 줄 (확인하려던 생각 습관, 요약, 보인 생각 습관과 근거, 약속, 위기 표현 표시) | #28 · #29 |
| `push_subscriptions` | 웹 푸시(앱을 닫아도 오는 알림)를 받겠다고 한 기기 하나가 한 줄 (보낼 주소, 잠금 열쇠) | #32 |
| `daily_care_stages` | 한 사람의 하루 관리 단계(0~4)와 그 이유가 한 줄. 하루에 한 번 정하고 바꾸지 않음 | #62 |
| `daily_signals` | 한 사람의 하루치 휴대폰·수면·걸음 데이터가 한 줄 (수면 시간, 잠든·일어난 시각, 걸음 수, SNS 30분 초과). 같은 날을 다시 보내면 덮어씀. 날짜 말고는 다 비어도 됨 | #18 |
| `thinking_tests` | 생각 습관 테스트 한 번이 한 줄 (마친 시각, 테스트 모양 번호). 다시 하면 새 줄이 쌓임 | #35 |
| `thinking_test_scores` | 테스트 한 번의 습관 8개 평균이 한 줄씩 (`@ElementCollection`) | #35 |
| `thinking_test_weaknesses` | 테스트 한 번에서 고른 약점 칩이 한 줄씩 (`@ElementCollection`) | #35 |
| `psych_tests` | 온보딩 심리검사 한 번이 한 줄 (마친 시각). 다시 하면 새 줄이 쌓임. 척도 7개 점수는 답으로 계산(저장 안 함) | #219 |
| `psych_test_answers` | 검사 한 번의 문장마다의 답(1 ~ 7)이 한 줄씩. 지금 검사 35개 · 옛 검사 14개 (`@ElementCollection`) | #219 |
| `fixed_blocks` | 학교 · 학원처럼 요일마다 정해진 시간 하나가 한 줄 (이름, 반복 요일, 시작 · 끝 시각). 카드가 아니라 타이머 · 의지력 계산에서 빠짐 | #223 |
| `goals` | 목표 하나가 한 줄 (WOOP: 목표 한 줄 · D-day · 지금 점수 또는 분량 · 이루면 좋은 점 · 안 하면 생길 일 · 방해물 · 대처, 상태). 지우지 않고 포기로 남김 | #221 |
| `goal_target_snapshots` | 목표값을 고치기 전의 값과 이유가 고칠 때마다 한 줄씩. 지우지 않음 (`@ElementCollection`) | #221 |
| `onboarding_answers` | 온보딩 설문 하나가 한 줄. 고칠 때마다 원래 줄을 두고 **새 줄이 쌓임** (처음 답한 자기 인식을 남기려고) | #21 |
| `onboarding_answer_failure_causes` | 설문 하나에서 고른 "계획이 무너진 때" 가 한 줄씩 (`@ElementCollection`) | #21 |
| `onboarding_answer_subject_levels` | 설문 하나의 과목별 최근 성적·자신감이 과목마다 한 줄씩 (`@ElementCollection`) | #21 |
| `initial_plans` | 한 사람의 처음 계획이 한 줄. **한 사람에 한 줄**이고 "다시 세우기" 할 때만 그 줄을 고침 | #21 |
| `experiments` | 한 사람이 시작한 자기 실험 하나가 한 줄 (가설, 전략, 시작일, 그만둘 기준, 상태, 판정 근거 실행률) | #52 · #54 |
| `experiment_cards` | 실험 규칙을 붙인 카드마다 한 줄 | #52 |
| `exams` | 시험 하나가 한 줄 (이름, 종류, 날짜). 과목별 점수는 `exam_scores` 에 따로 적음 | #16 |
| `exam_scores` | 시험 하나의 과목 하나의 점수가 한 줄 (예상 점수, 실제 점수). 같은 시험에 같은 과목은 한 번만 | #16 |
| `reviews` | 회고 하나가 한 줄 (시험 후 회고 또는 주간 회고, 답 3개). 같은 시험·같은 주에 다시 쓰면 덮어씀 | #17 |
| `care_settings` | 사용자 한 명의 관리 설정이 한 줄 (전화 시간, 메시지 횟수, 연락 금지, 독설 수위, 앱 잠금, 제재 한도와 바뀔 한도) | #22 |
| `focus_lock_apps` | 한 사용자가 고른 잠금 대상 앱 하나가 한 줄. 같은 사용자 안에서 같은 앱은 한 번만 | #22 |
| `setting_changes` | 설정 칸 하나를 한 번 바꾼 것이 한 줄 (바꾼 시각, 칸 이름, 이전 값, 새 값) | #22 |
| `school_events` | 학교 하나의 학사일정 하루치 행사가 한 줄 (날짜, 이름, 설명, 학년). **사람이 아니라 학교마다 한 벌**이고 NEIS 에서 받을 때마다 그 기간을 통째로 바꿈 | #282 |
| `school_event_fetches` | 학교 하나가 한 줄 (교육청 코드, NEIS 에서 마지막으로 받은 시각). 다시 받을지 정할 때 봄 | #282 |
| `gpt_usages` | GPT 를 한 번 부른 것이 한 줄 (종류, 부른 시각, 통화 번호·통화 시간, 대략적인 비용). 얼마나 썼는지 보는 기록 (하루 한도는 2026-10-24 에 없앰) | #33 |
| `checkins` | 한 사람의 하루치 체크인이 한 줄 (컨디션, 기분, 어제 못 채운 이유와 기타 글, 예상 순공시간, 약속 두 칸, 개선안 실천 여부, 어디서 했는지). 같은 날을 다시 보내면 덮어씀. 누구·날짜·source 말고는 다 비어도 됨 | #24 |
| `cards` | 카드 한 장이 한 줄 (제목, 날짜, 반복 요일, 시작 시각, 길이, 목표, 과목, 절대/유연, 자습/인강, 예상 달성 개수와 낮춘 이유, 알림, 계획에 쓴 시간). 반복 카드도 **한 장**이고 날마다 복사하지 않음 | #222 |
| `card_checklist_items` | 카드 하나의 체크리스트 항목이 한 줄씩 (`@ElementCollection`, 보낸 순서대로) | #222 |
| `card_expected_fail_causes` | 카드 하나의 예상 실패 원인 글이 한 줄씩 (`@ElementCollection`, 5개까지) | #222 |
| `checkin_emotions` | 체크인 하나에서 고른 감정 태그가 한 줄씩 (`@ElementCollection`). 안 골랐으면 줄이 없음 | #220 |
| `diaries` | 학생이 쓴 일기 하나가 한 줄 (원문, 기분, 날짜, 쓴 시각, AI 분석 시각). 같은 날에 여러 개 가능. **원문은 본인만** | #60 |
| `calendar_alarms` | 학생의 구글 캘린더 일정 하나가 한 줄 (일정 id, 제목, 시작 시각, 알림 처리 시각). FE 가 앞으로 7일 일정을 **덮어쓰기**로 올리고, 예약 작업이 시작 10분 전에 푸시. 지난 일정은 하루 뒤 지움 | 2026-10-24 |
| `answer_checks` | 문제집 사진을 한 번 올려 채점한 기록 하나가 한 줄 (날짜, 모드, AI 가 읽은 과목, 채점 시각). **사진은 저장하지 않음** | 2026-10-24 |
| `answer_check_items` | 채점 기록 하나 안의 문제 하나가 한 줄 (번호, 학생 답, AI 답 또는 O · X 표시, AI 처음 판정, 지금 판정, 학생이 적은 정답, 고친 시각) | 2026-10-24 |
| `weekly_picks` | 한 사람이 한 주에 "해 보기로 고른 것" 하나가 한 줄 (주, 해 볼 것, 한 줄 설명, 넘어가기 여부, 고른 시각). 같은 주에 다시 고르면 덮어씀 | #406 |
| `diary_emotions` · `diary_causes` · `diary_keywords` | 일기 하나에서 AI 가 뽑은 감정 · 실패 원인 · 키워드가 한 줄씩 (`@ElementCollection`). 일기를 지우면 같이 지워짐 | #60 |
| `reflection_prescriptions` | 성찰일기 처방 한 번이 한 줄 (첫날·마지막 날, 처방 이유, 질문 3개). 처음 물어본 날 정해지고 기간 동안 그대로 | #61 |
| `insights` | 학생에게 보여 준 인사이트 하나가 한 줄 (문장, 종류, 가설 여부, 보여 준 날, 누른 반응). 반응은 다시 누르면 바뀜. 반응 말고는 다 있어야 함 | #43 · #41 |
| `insight_evidence_dates` | 인사이트 하나의 근거 날짜가 한 줄씩 (`@ElementCollection`). 날짜로 찾기 쉽게 날짜로 저장 | #43 · #41 |
| `risk_forecasts` | 한 사람의 하루치 위험 예측이 한 줄 (예측한 날, 위험 수준, 그날이 지난 뒤 채우는 실제 달성률과 맞았는지). **위험 없음인 날도 한 줄**. 같은 날을 다시 예측하면 그 줄을 최신으로 고침 | #49 |
| `risk_forecast_reasons` | 예측 하나에서 위험하다고 본 이유가 한 줄씩 (`@ElementCollection`). 위험 없음이면 줄이 없음 | #49 |

> `accounts` 와 `login_tokens` 는 2026-10-11 에 SNS 로그인을 빼고 일반 로그인으로 정하면서 **선생님 요청으로 AI 가 먼저 만든 테이블**입니다.
> 학생이 설계한 `users` 는 그대로 두려고 로그인 정보를 따로 뺐습니다. 고칠 점이 보이면 이슈로 남겨 주세요.

> `consent_records` 는 2026-10-17 에 이슈 #71 에서 **학생이 설계**했습니다. (AI 는 질문으로만 도왔습니다)
> - 한 줄 = 동의 한 번. 다시 동의하면 **옛 줄을 고치지 않고 새 줄을 더합니다.** 그래서 "언제 어떤 문구에 동의했는지" 이력이 남습니다.
> - 지금 동의 상태 = 이 사람의 줄 중 번호(`id`)가 가장 큰 줄입니다. 그 줄의 `text_version` 이 서버가 지금 받는 버전(`ConsentPolicy.CURRENT_VERSION`)과 다르면 "최신 문구에 동의하지 않은 사람" 이고, 그 사람은 저장(POST · PUT · PATCH)이 403 입니다.
> - 칸은 동의 시각 · 문구 버전 · 만 14세 확인 셋과 `user_id` 입니다. 필수 동의(이용약관 · 개인정보)는 칸을 따로 두지 않았습니다. 동의하지 않으면 가입이 안 되어서, 줄이 있다는 것이 곧 필수 동의를 했다는 뜻이기 때문입니다.
> - **만 14세 확인만 비어도 됩니다.** 이 확인을 받기 전에 가입한 계정의 옛 동의는 확인받은 적이 없어서 비워 둡니다.
> - `accounts.agreed_at`(가입할 때의 동의 시각)은 그대로 둡니다. 두 곳에 같은 시각이 남지만, 학생이 그렇게 정했습니다.
> - 칸 이름은 AI 가 정했습니다. `age_confirmed_over14` 로 한 이유: `over14_confirmed` 처럼 숫자 뒤에 대문자가 오면 Spring 이 밑줄을 넣지 않아 `over14confirmed` 가 될 수 있어서입니다. 그림에서 생각한 이름과 같은지 봐 주세요.
> - 실명(`accounts.name`)은 이 이슈에서 **선택**으로 바꿨습니다. (이슈 완료 조건: 실명·전화번호·생년월일은 받지 않는다) FE 가 실명 입력을 없앤 뒤에 칸을 지우는 PR 을 따로 만듭니다.

> `messages` 는 2026-10-12 에 이슈 #30 을 만들면서 **AI 가 초안을 먼저 만든 테이블**입니다. (사용자가 "AI 가 초안을 만들어 달라" 고 해서 예외로 했습니다)
> 이슈 완료 조건에 적힌 것(종류, 보낸 시각, 읽은 시각, 주인)에 메시지 내용 글자(`text`)만 더했습니다.
> FE 의 `AiMessage` 에 있는 `careLevel`(관리 강도), `eventLabel`(이벤트 이름), `source`(AI/앱) 는 **아직 담지 않았습니다.** 필요한지는 위 그림을 보고 정해 주세요.

> `call_records` 는 2026-10-14 에 **선생님이 "FE·BE·테이블까지 AI 가 만들라" 고 맡긴 기능**(인지편향 잡기)에서 AI 가 만든 테이블입니다.
> - 통화를 **시작할 때** 한 줄을 만들고(`asked_*`), 통화가 **끝나면** 같은 줄을 채웁니다(`finished_at` 아래 칸들). 마무리하는 API 는 다음 PR(#29)에서 만듭니다.
> - **대화 원문(자막)과 녹음은 저장하지 않습니다.** 요약과 정해진 분류값만 남깁니다. 위기 표현이 감지된 통화는 내용 칸을 비우고 `crisis_detected` 만 남깁니다.
> - 다음 통화는 최근 2주 기록을 읽어 자주 보인 생각 습관을 다시 확인하고, 지난 약속을 지켰는지 묻습니다. 최근 7일 안에 위기 표현이 있었으면 위로하는 통화로 합니다.
> - 체크인(#24) 테이블과 겹치는 칸(못 채운 이유·약속)이 있습니다. 체크인 테이블이 생기면 합칠지 정해 주세요.
> - 2026-10-17 에 `channel` 을 더했습니다. 상담 채팅(#238)은 "글로 하는 통화" 라서 같은 테이블에 남기고 이 칸으로만 구분합니다. 채팅도 대화 원문은 저장하지 않습니다.

> `thinking_tests` 와 딸린 테이블 두 개는 2026-10-15 에 같은 기능(인지편향 잡기)에서 AI 가 만들었습니다.
> - **문장별 답은 저장하지 않고 습관 8개의 평균만** 저장합니다. 테스트 문장은 앞으로 바뀔 수 있어서(선생님 말씀), 문장이 바뀌어도 테이블은 그대로 쓰게 하려는 것입니다.
> - 한 테스트에 점수 8개 · 약점 여러 개라서 **딸린 테이블(`@ElementCollection`)** 에 담았습니다. 엔티티는 `ThinkingTest` 하나이고, JPA 가 두 테이블을 알아서 같이 읽고 씁니다. 그래서 딸린 테이블에는 `id` 가 없습니다.
> - "기타" 에 직접 쓴 약점 글은 서버로 받지 않아서 저장하지 않습니다.

> `onboarding_answers` · 딸린 테이블 두 개 · `initial_plans` 는 2026-10-17 에 이슈 #21 에서 **학생이 설계**했습니다.
> - 한 줄 = 설문 하나. 고치면 원래 줄을 고치지 않고 **새 줄을 더합니다.** 그래야 처음 답한 자기 인식(아침형/저녁형 등)이 남아서 #40 에서 실제 기록과 견줄 수 있습니다.
> - 지금 내 설문은 `answered_at` 이 가장 늦은 줄, 처음 자기 인식은 항목마다 **처음으로 답이 있는 줄**에서 꺼냅니다. (온보딩이 짧아져서 아침형/저녁형 같은 것은 나중에 처음 답하기 때문)
> - 한 줄에 여러 개인 답(무너진 때 · 과목별 수준)은 `thinking_tests` 처럼 딸린 테이블에 담았습니다.
> - 처음 계획은 규칙이 달라서(온보딩을 다시 해도 안 바뀌고 "다시 세우기" 때만 바뀜) 테이블을 따로 뒀습니다. 한 사람에 한 줄이라 그림의 선이 `||--o|` (0개 또는 1개) 입니다.
> - 엔티티 이름은 `OnboardingAnswer` · `UserInitialPlan` 입니다. (`routine/InitialPlan` 은 루틴 초안 계산 도구라서 이름이 겹치지 않게 했습니다)
> - 과목을 지우면 그 과목의 수준 줄은 DB 에 남지만 응답에서는 빼고 보여 줍니다.

> `exams` 와 `exam_scores` 는 2026-10-17 에 이슈 #16 에서 **학생이 직접 설계한** 테이블입니다. (AI 는 질문으로 도왔고, 칸 이름의 영어 표기만 같이 정했습니다)
> - **시험 하나 = 한 줄, 과목별 점수 = 딸린 테이블.** 과목은 사용자마다 달라서(`subjects`) 시험 표에 `국어 예상` 같은 칸을 미리 만들 수 없고, 시험마다 과목 수도 다르기 때문입니다. (모의고사 3과목, 쪽지시험 1과목)
> - **두 테이블 모두 `user_id` 가 있습니다.** 그래서 탈퇴하면(`AccountService.withdraw`) 코드를 고치지 않아도 시험과 점수가 같이 지워집니다.
> - 실제 점수(`actual_score`)만 비어도 됩니다. 시험 전에는 아직 모르기 때문입니다. 나머지 칸은 꼭 있어야 합니다.
> - 점수는 **0 ~ 100 의 정수**입니다. 같은 시험에 같은 과목은 한 번만(`uk_exam_scores_exam_id_subject_id`) 적을 수 있습니다. 이 둘은 DB 에도 규칙(unique · check)으로 걸어 두었습니다.
> - 시험 종류(`type`)는 이슈에는 3개(모의고사·내신·쪽지시험)였지만 FE 의 `ExamType` 과 화면(#80)에 **기타(`ETC`)** 가 있어서 4개로 정했습니다.
> - 시험 후 회고는 #17 에서 `reviews` 테이블에 따로 담았습니다. (아래 설명)

> `reviews` 는 2026-10-17 에 이슈 #17 에서 **AI 가 초안을 먼저 만든 테이블**입니다. (이슈를 자동으로 처리하는 중이라 학생에게 묻지 못했습니다. **PR 리뷰에서 학생이 꼭 확인해 주세요**)
> - **시험 후 회고와 주간 회고를 한 테이블에 담고 `type` 으로 구분합니다.** 둘 다 "정해진 질문 3개에 짧게 답한 것" 이라 모양이 같고, 이슈가 `POST /reviews` 하나로 저장하라고 해서입니다.
> - 시험 후 회고는 `exam_id` 만, 주간 회고는 `week_start`(그 주 월요일)만 차 있습니다. 나머지 하나는 비어 있습니다.
> - **답은 `answer1` · `answer2` · `answer3` 세 칸**입니다. 질문이 늘 3개 이하라 딸린 테이블로 빼지 않았습니다. 질문 글은 저장하지 않고, 몇 번 질문인지는 칸 순서로 압니다. (질문 순서는 docs/API.md "회고")
> - 덮어쓰기 기준은 `(user_id, exam_id)` 와 `(user_id, week_start)` 두 개의 중복 금지 규칙입니다. 비어 있는 칸(null)끼리는 겹친다고 보지 않아서 서로 부딪히지 않습니다.
> - `user_id` 가 있어서 탈퇴하면 회고도 같이 지워집니다.
> - FE 의 시험 회고 ① "준비가 충분했나" 는 1~5 버튼이지만, 이슈대로 **모든 답을 글자(200자까지)** 로 받습니다. FE 는 `"4"` 처럼 보내면 됩니다. 숫자 칸으로 따로 뺄지는 학생이 정해 주세요.

> `care_settings` · `focus_lock_apps` · `setting_changes` 는 2026-10-17 에 이슈 #22 에서 **학생이 직접 설계한** 테이블입니다. (AI 는 질문으로 돕고 영어 이름만 같이 정했습니다)
> - **사용자 한 명의 설정 = 한 줄.** 한 번도 바꾸지 않은 사람은 줄이 없고 처음 값을 보여 줍니다. 처음 바꿀 때 줄이 생깁니다.
> - **제재 한도는 "지금 한도" 와 "바뀔 한도 · 적용 날짜" 를 따로 적습니다.** 높이면 다음 날부터 적용해야 해서, 오늘 값과 내일 값을 둘 다 기억해야 하기 때문입니다. 이 두 칸만 비어도 됩니다.
> - **잠금 대상 앱은 여러 개라서 딸린 테이블**로 뺐습니다. (시험 점수와 같은 이유)
> - **바꾼 기록은 칸 하나를 바꿀 때마다 한 줄**입니다. 값의 모양이 제각각이라 이전 값 · 새 값은 글자로 적습니다. 설정 표는 값을 덮어써서, 옛 값은 여기에만 남습니다.
> - 세 테이블 모두 `user_id` 가 있어서 탈퇴하면 같이 지워집니다.
> - 처음 값은 FE 와 같습니다 (전화 시간 21:00 은 학생이 정함). FE 의 `allowOffHoursCall`(정해진 시간 외 독촉 전화 허용)은 이슈에 없어서 담지 않았습니다.

> `gpt_usages` 는 2026-10-17 에 이슈 #33 에서 **학생이 직접 설계한** 테이블입니다. (AI 는 질문으로 돕고 영어 이름만 같이 정했습니다)

> `goals` · `goal_target_snapshots` 는 2026-10-17 에 이슈 #221 을 따라 **AI(Claude)가 만든** 테이블입니다. 고정 일정과 같은 이유로 멘토가 AI 가 만들기로 정했습니다. 칸은 FE 의 `Goal` 과 같은 이름입니다.
> - **목표값 이력은 지우지 않습니다.** 목표를 슬쩍 낮추면 원래 목표를 잊기 쉬워서(기획서 3 · 7번 오류), 고칠 때마다 원래 값과 이유를 딸린 테이블에 쌓습니다.
> - 다짐 녹음은 서버에 두지 않습니다. (미성년자 음성, 기기에만 — 2026-10-05 결정)

> `fixed_blocks` 는 2026-10-17 에 이슈 #223 을 따라 **AI(Claude)가 만든** 테이블입니다. AI 하루 계획이 학교 · 학원 시간을 보려면 서버에 있어야 해서, 멘토가 AI 가 만들기로 정했습니다. (그전에는 FE 의 localStorage 에만 있었습니다) 칸은 FE 의 `FixedBlock` 과 같은 이름이고, 반복 요일은 카드(`cards.repeat_days`)와 같은 모양으로 한 칸에 적었습니다.

> `school_events` · `school_event_fetches` 는 2026-10-17 에 이슈 #282 에서 **AI(Claude)가 만든** 테이블입니다. 가입 때 고른 학교의 학사일정(시험 기간 · 방학 등)을 나이스(NEIS) 공개 API 에서 받아 둡니다.
> - **user_id 가 없습니다.** 개인 정보가 아니라 학교의 공개 자료라서, 같은 학교 학생들이 한 벌을 같이 씁니다. 그래서 탈퇴해도 지우지 않습니다. (`AccountServiceTest` 의 `PUBLIC_TABLES`)
> - **시험인지는 저장하지 않습니다.** 이름(정기시험 · 학력평가 등)을 보고 응답할 때 코드가 정합니다. 판정 규칙을 고쳐도 다시 받지 않아도 됩니다.

> `psych_tests` · `psych_test_answers` 는 2026-10-17 에 이슈 #219 · 최종 기획서 6장을 따라 **AI(Claude)가 만든** 테이블입니다. 멘토가 "데이터가 쌓여야 AI 가 분석한다" 며 심리검사 결과를 서버에 쌓자고 정했고, 칸은 생각 습관 테스트(`thinking_tests`)와 같은 모양으로 했습니다. 문장마다의 답을 그대로 남기고 척도 점수는 저장하지 않습니다.
> - **GPT 를 한 번 부른 것 = 한 줄.** 예전에는 이 기록을 세서 하루 한도(통화 횟수 · 통화 시간 · 글 호출 횟수)를 지켰는데, 2026-10-24 에 한도를 없애서 지금은 비용을 보는 기록입니다.
> - **통화 시간과 비용만 비어도 됩니다.** 코치·요약에는 통화 시간이 없고, 통화는 끝나야 시간과 비용을 알 수 있기 때문입니다.
> - **통화 번호(`call_record_id`)** 는 "통화가 끝났을 때 어느 줄을 채울지" 알려고 더했습니다. 같은 날 통화가 여러 번이면 이것 없이는 구별할 수 없습니다.
> - **비용은 원 단위 정수**의 어림값입니다. (읽기 쉬워서 학생이 정함) 환산 기준은 `application.yml` 의 `cost-won-*` 설정에 있습니다.
> - 종류는 지금 GPT 를 부르는 세 곳(통화 · 통화 요약 · 코치)만 있습니다. 인사이트 · 개선안 · 일기 분석이 생기면 하나씩 더합니다.

> `cards` 와 딸린 테이블 두 개는 2026-10-17 에 이슈 #222 에서 **AI 가 만든 테이블**입니다. (칸 이름은 FE 의 `Card` 타입과 최종 기획서 「4.3 카드」 를 그대로 따랐습니다. **PR 리뷰에서 학생이 꼭 확인해 주세요**)
> - 처음 이슈는 `study_plans` 에 칸을 더하는 것이었지만, **새 테이블 `cards`** 로 바꿨습니다. (#222 댓글) 반복 카드는 한 장이라 "날짜마다 한 줄" 인 `study_plans` 와 모양이 다르고, 옛 화면이 쓰는 `/study-plans` 를 깨지 않으려고 입니다.
> - **반복 요일은 한 칸(`repeat_days`)에 `"1,3,5"` 처럼** 적습니다. 요일은 7개뿐이라 딸린 테이블로 빼지 않았습니다. 그날 하는 카드인지는 `Card.isOnDate` 가 FE 의 `isCardOnDate` 와 같은 규칙으로 봅니다.
> - **체크리스트 · 예상 실패 원인은 여러 개라서 딸린 테이블**에 담았습니다. `position` 은 보낸 순서입니다. 체크했는지(`doneAt`)는 카드가 아니라 **그날의 세션 기록**(#224)에 남깁니다.
> - `goal_id` 는 목표 테이블(`goals`, #221)의 번호입니다. DB 의 외래 키는 걸지 않았습니다. 카드를 저장할 때 "내 목표인지" 검사는 #221 다음 PR 에서 더합니다.
> - `subject_id` 는 2026-10-24 에 이슈 #404 에서 **AI 가 더한 칸**입니다. (사용자가 칸 추가를 허락함, **PR 리뷰에서 학생이 꼭 확인해 주세요**) 과목(`subjects`)의 번호이고 비어 있으면 "과목 없음" 입니다.
>   - 카드로 켠 공부 기록에 과목을 안 보내면 서버가 카드의 과목을 `study_sessions.subject_id` 에 채웁니다. 과목별 공부 시간이 '기타' 로 묶이지 않게 하려는 것입니다.
>   - `goal_id` 처럼 DB 외래 키는 걸지 않았습니다. 대신 **과목을 지우면 그 과목을 붙인 카드의 `subject_id` 를 비웁니다.** (카드는 지우지 않음, `SubjectService.delete`) 과목 없는 카드도 있어서, 그림의 선은 "과목 하나에 카드 여러 장(0장도 됨)" 이라는 논리적인 관계입니다.
>   - 카드를 저장할 때 "내 과목인지" 를 검사합니다. 없는 과목 · 남의 과목은 404 입니다.
> - 알림 세 칸(`alert_*`)이 모두 비어 있으면 **기본 알림**(정각 · 메시지 · 기본 문구)입니다.
> - 모든 줄에 `user_id` 가 있어서(딸린 테이블은 카드를 따라) 탈퇴하면 같이 지워집니다.

> `study_sessions` 의 새 칸과 `session_*` 테이블 네 개는 2026-10-17 에 이슈 #224 에서 **AI 가 만들었습니다.** (칸 이름은 FE 의 `StudySession` · `SessionPause` · `SessionEndReport` 를 따름. **PR 리뷰에서 학생이 꼭 확인해 주세요**)
> - 옛 기록이 이미 있어서 **새 칸은 모두 비어도 되게** 했습니다. 비어 있는 `mode` 는 타이머(NORMAL), 비어 있는 `ended_early` 는 false 로 봅니다.
> - **일시정지 · 체크한 항목 · 잘 된 이유 · 메모는 여러 개라서 딸린 테이블**에 담았습니다. 일시정지를 보내면 `pause_count` · `paused_ms` 는 그 목록으로 계산해서 같이 저장합니다. (옛 칸을 읽는 계산이 그대로 맞게)
> - 체크리스트를 체크했는지는 카드가 아니라 **여기(`session_checked_items`)** 에 남깁니다. 반복 카드는 한 장이라 날짜마다 나눠 담을 곳이 세션이기 때문입니다. (#222)
> - `end_reported` 는 "종료 팝업에 답했는지" 입니다. 이것이 없으면 "체크 0개로 답함" 과 "팝업을 닫아 버림" 을 구별할 수 없습니다.
> - 카드로 켠 공부 기록은 과목이 없어도 됩니다. 그래서 검사 규칙을 `ck_study_sessions_study_has_subject_or_card` (노는 기록이거나, 과목이나 카드가 있음)로 바꿨습니다.

> `card_day_marks` · `rest_days` 는 2026-10-17 에 이슈 #225 에서 **AI 가 만든 테이블**입니다. (**PR 리뷰에서 학생이 꼭 확인해 주세요**)
> - **미루기와 실패는 한 테이블**에 `mark` 로 구분합니다. 둘 다 "카드 하루치에 사람이 남긴 표시" 이고, 한 날에는 둘 중 하나만 있을 수 있어서입니다. (`uk_card_day_marks_card_id_mark_date`)
> - 미루기 한도(반복 카드 월 5회 · 1회성 장당 2회 · 절대 카드 0회)는 이 테이블의 줄을 세서 지킵니다. 1회성 카드를 미루면 카드 자체도 새 날짜 · 시각으로 옮깁니다. 반복 카드는 한 장이라 표시에만 새 날짜 · 시각이 남습니다.
> - **휴식은 카드가 아니라 하루 전체**에 붙어서 `rest_days` 로 따로 뒀습니다. 휴식한 날의 카드는 실패 집계에서 빠집니다.
> - 이유 글에 **위기 표현**(#56)이 있으면 글은 저장하지 않고 `crisis_detected` 만 남깁니다. (`call_records` 와 같은 방식)
> - 실패 원인은 체크인의 못 채운 이유(`MissReason`)와 같은 값이고, 이 이슈에서 **'미루다 보니'(`PROCRASTINATED`)** 를 더했습니다. 그래서 `call_records.miss_reason` · `checkins.miss_reason` · `study_sessions.stop_reason` 의 검사 규칙에도 이 값이 들어갑니다.
> - 두 테이블 모두 `user_id` 가 있어서 탈퇴하면 같이 지워집니다.

> `daily_care_stages` 는 2026-10-18 에 이슈 #62 에서 **AI 가 만든 테이블**입니다. (테이블 설계를 AI 에게 맡긴 범위, **PR 리뷰에서 학생이 꼭 확인해 주세요**)
> - 하루의 단계를 **한 번 정하면 바꾸지 않습니다.** 그래서 "어제 몇 단계였나"(하루 한 칸 규칙)와 "당근 주 vs 채찍 주"(#47)를 이 표로 셉니다.
> - 4단계 칸(`forced_card_id` · `forced_card_date` · `released_at`)은 2026-10-24 에 이슈 #234 에서 **학생이 고른 설계**입니다.
>   - 절대 전환은 카드(`cards.priority`)를 바꾸지 않고 "그 카드 · 그 날짜만 절대" 로 이 줄에 적습니다. 풀면 두 칸을 비워서 원래대로 돌아갑니다. (FE `stage4.ts` 와 같은 방식)
>   - 풀어도 판정한 `stage`(4)는 그대로 두고 `released_at` 만 적습니다. 응답과 다음 날 판정은 이 칸이 있으면 3단계로 봅니다.
>   - 증빙 강제는 세션 쪽 `study_sessions.proof_required` 에 저장 순간의 값으로 남깁니다. 나중에 단계가 바뀌어도 그때 규칙이 남게 하려는 것입니다.
> - 이유 문장(`reason`)을 그대로 남깁니다. 규칙 문장이 나중에 바뀌어도 그날 학생이 본 문장이 남게 하려는 것입니다.
> - 계산 재료(의지력 등)는 남기지 않습니다. 의지력은 어떤 응답에도 넣지 않는 값이라서입니다. (낙인 방지, `WillpowerNotInResponseTest`)
> - `user_id` 가 있어서 탈퇴하면 같이 지워집니다.

> `experiments` · `experiment_cards` 는 2026-10-18 에 이슈 #52 · #54 에서 **AI 가 만든 테이블**입니다. (사용자 "테이블 상관없이 BE 이슈 계속", **PR 리뷰에서 학생이 꼭 확인해 주세요**)
> - 실험은 **새 카드가 아니라 있던 카드에 붙는 규칙**이라서, 붙인 카드는 딸린 테이블(`experiment_cards`)에 번호만 담습니다. 판정은 그 카드들의 카드 결과로 합니다.
> - 판정이 끝나면 `status` 와 **근거 숫자**(`baseline_rate` · `experiment_rate`)를 남기고 바꾸지 않습니다. 나중에 판정 규칙이 바뀌어도 그때 학생이 본 결과가 남게 하려는 것입니다.
> - 이행률(#53)은 저장하지 않고 볼 때마다 카드 기록으로 셉니다. 진행 중에는 매일 바뀌는 값이라서입니다.
> - `user_id` 가 있어서 탈퇴하면 같이 지워집니다. (붙인 카드 줄도 함께)

> `checkin_emotions` 는 2026-10-17 에 이슈 #220(최종 기획서 4.2 「홈」의 하루 첫 태그)에서 **AI 가 만든 딸린 테이블**입니다. (체크인은 선생님이 AI 에게 맡긴 범위)
> - 하루 첫 태그 = 컨디션(1~5) + 감정 태그 여러 개. 감정은 한 체크인에 여러 개라서 `thinking_test_weaknesses` 처럼 딸린 테이블에 담았습니다. 엔티티는 `CheckIn` 하나입니다.
> - 감정 이름은 이슈에 적힌 6개(`EmotionTag`)이고, DB 에도 이름 그대로 저장합니다.
> - 감정 태그를 보내면 그날의 감정이 **전부 바뀝니다.** 다른 칸과 달리 **안 보내면 그대로 둡니다**(감정 태그를 모르는 화면이 다시 보내도 지워지지 않게). 체크인 줄이 지워지면(탈퇴) 같이 지워집니다.

> `answer_checks` 와 `answer_check_items` 는 2026-10-24 에 **학생이 직접 설계한** 사진 채점 테이블입니다. (AI 는 설계대로 코드로 옮기고 글자 칸 길이만 정했습니다)
> - **사진 한 번 올린 것 = `answer_checks` 한 줄, 그 안의 문제 하나 = `answer_check_items` 한 줄.** 사진은 저장하지 않고 결과만 남깁니다.
> - 맞음 · 틀림 개수는 저장하지 않고 `answer_check_items` 의 `result` 를 세어서 보여 줍니다. 학생이 판정을 고치면 개수도 바로 바뀝니다.
> - AI 의 처음 판정(`ai_result`)은 고쳐도 바뀌지 않습니다. 고치면 `result` · `correct_answer` 만 바뀌고 `edited_at` 이 찹니다. (`edited_at` 이 차 있으면 "직접 고침")
> - `ai_answer` 는 "아직 안 했어요"(`UNMARKED`) 에서만, `mark_read` 는 "채점했어요"(`MARKED`) 에서만 값이 있습니다.
> - **AI 가 정한 글자 칸 길이:** 과목 30자, 문제 번호 20자, 학생 답 · AI 답 · 정답 100자. 요청 검사(정답 100자)와 같은 값입니다. 짧거나 길면 알려 주세요.
> - `answer_check_id` 는 다른 테이블처럼 **번호만 적고 DB 외래 키 제약은 걸지 않았습니다.** 그림의 선은 논리적인 관계입니다. (탈퇴는 `user_id` 가 있는 테이블을 하나씩 지우는데, 외래 키가 있으면 지우는 순서에 따라 실패할 수 있어서)
> - 두 테이블 모두 `user_id` 가 있어서 탈퇴하면 같이 지워집니다.

> `weekly_picks` 는 2026-10-24 에 이슈 #406 에서 **AI 가 만든 테이블**입니다. (사용자가 새 테이블을 허락하고 칸을 정함, **PR 리뷰에서 학생이 꼭 확인해 주세요**)
> - 주간 리포트 아래 [다음 주에 하나만 해 볼까요?] 에서 고른 것입니다. 전에는 FE 의 이 기기(localStorage, `src/lib/weeklyPicks.ts`)에만 있었고, 칸 이름은 그 모양을 그대로 따랐습니다.
> - **한 사람의 한 주(월요일)에 한 줄**입니다. (`uk_weekly_picks_user_id_week_start`) 다시 고르면 새 줄을 만들지 않고 그 줄을 덮어씁니다.
> - "이번 주는 넘어가기" 도 한 줄로 남기고 `skip` 을 `true` 로 둡니다. 이때도 `title` · `hint` 에는 보기 글자가 들어 있습니다.
> - "지켰어?" 는 따로 적지 않습니다. 그 주에 해냈는지는 타이머 기록으로 봅니다.
> - `user_id` 가 있어서 탈퇴하면 같이 지워집니다.

> `calendar_alarms` 는 2026-10-24 에 **AI 가 만든 테이블**입니다. (사용자 "캘린더 시작 10분 전에 실제 앱처럼 알람 오게", "알림에 일정 제목 넣자", **PR 리뷰에서 학생이 꼭 확인해 주세요**)
> - 서버는 구글에 직접 접속하지 않습니다. FE 가 학생의 브라우저에서 읽은 **앞으로 7일 일정**을 올립니다. (`PUT /api/v1/calendar-alarms`, 덮어쓰기)
> - 하루 종일 일정은 "10분 전" 이 없어서 받지 않습니다. 시각이 있는 일정만 한 줄씩입니다.
> - 한 사람의 같은 일정(`user_id` + `event_key`)은 **한 줄만** 둡니다(unique). 올리기가 동시에 두 번 오면 같은 일정이 두 줄이 되어 알림이 두 번 가던 것을 로컬에서 확인하고 막았습니다. 겹친 요청은 한 번 더 올려서 있는 줄을 고칩니다.
> - `handled_at` 이 차 있으면 다시 보내지 않습니다. 구글에서 일정을 옮겨 `starts_at` 이 바뀌면 비워서 새 시각에 다시 알립니다.
> - **일정 제목이 서버에 저장됩니다.** (사용자가 알림에 제목을 넣기로 정함) 알림 글에만 쓰고, 로그 · GPT 에는 보내지 않습니다. 시작한 지 하루 지난 줄은 예약 작업이 지우고, 캘린더 연결을 끊으면(`DELETE`) 바로 다 지웁니다.
> - `user_id` 가 있어서 탈퇴하면 같이 지워집니다.

> `diaries` 와 딸린 테이블 3개는 2026-10-24 에 이슈 #60 에서 **AI 가 만든 테이블**입니다. (사용자 "이슈 완료 및 참고 자료 조건에 따라 진행", **PR 리뷰에서 학생이 꼭 확인해 주세요**)
> - **일기 하나 = 한 줄**은 학생이 정했습니다. 같은 날에 여러 개를 쓸 수 있습니다.
> - `mood`(기분 1 ~ 5)는 FE 짝 이슈(Project-FE#63)의 `DiaryEntry.mood` 에 맞춰 더했습니다. FE 는 필수로 고르지만 서버는 비어도 받습니다.
> - 뽑은 감정 · 원인 · 키워드는 한 일기에 여러 개라서 `checkin_emotions` 처럼 딸린 테이블에 담았습니다. 일기 줄을 지우면 같이 지워집니다. (이슈 완료 조건)
> - 감정은 체크인 감정 태그(`EmotionTag`), 원인은 체크인 못 채운 이유(`MissReason`)와 **같은 값**입니다. 실패 원인 분석(#39)에서 같이 셀 수 있습니다.
> - `analyzed_at` 이 비어 있으면 분석을 안 한 것입니다(분석 동의 안 함 · 위기 표현 · AI 꺼짐 · 실패). 그때 딸린 줄이 없는 것은 "없음" 이 아니라 "모름" 입니다.
> - `user_id` 가 있어서 탈퇴하면 같이 지워집니다. 딸린 테이블은 `AccountServiceTest` 의 `CHILD_TABLES` 에 더했습니다.

> `reflection_prescriptions` 는 2026-10-17 에 **선생님이 "FE·BE·테이블까지 AI 가 만들라" 고 맡긴 기능**(통화 기록·회고·성찰)에서 AI 가 만든 테이블입니다. (이슈 #61)
> - **처방 한 번 = 한 줄.** 언제 처방하는지는 `ReflectionRule` 과 docs/API.md "성찰일기 처방" 에 있습니다.
> - **질문 글을 그대로 저장합니다.** 질문 세트(`ReflectionQuestions`)를 나중에 고쳐도 이미 내린 처방의 질문이 바뀌지 않게 하려는 것입니다. 질문은 늘 3개라서 `reviews` 처럼 칸 3개에 담았습니다.
> - **답(일기 원문)은 저장하지 않습니다.** 지금은 FE 기기에만 있습니다. 서버에 둘지는 일기 저장(#60)·암호화(#68)에서 정합니다.
> - `user_id` 가 있어서 탈퇴하면 같이 지워집니다.

> `risk_forecasts` 와 `risk_forecast_reasons` 는 2026-10-17 에 이슈 #49 에서 **학생이 직접 설계한** 테이블입니다. (AI 는 질문으로 돕고, 아래 "칸 이름" 만 정했습니다)
> - **한 줄 = 하루 예측 하나.** `(user_id, forecast_date)` 가 겹치지 않게 막아 두었습니다. 같은 날을 다시 예측하면 새 줄을 만들지 않고 그 줄의 위험 수준과 이유를 **최신 결과로 고칩니다.**
> - **위험 없음(`NONE`)인 날도 한 줄을 남깁니다.** 위험하다고 한 날만 남기면 "위험 없다고 했는데 무너진 날" 을 셀 수 없어서 정확도를 알 수 없기 때문입니다.
> - **이유는 딸린 테이블**입니다. 한 예측에 이유가 0 ~ 2개(약한 요일 · 늦게 잠)라서 `thinking_tests` 처럼 `@ElementCollection` 으로 담았습니다. 규칙이 늘어도 칸을 늘릴 필요가 없습니다.
> - **실제 달성률과 맞았는지는 둘 다 비어도 됩니다.** 예측할 때는 결과를 아직 모르기 때문입니다. 그날이 지난 뒤 채웁니다. (학생은 처음에 "실제 달성률은 꼭 있어야 한다" 고 했다가, 그러면 예측할 때 줄을 만들 수 없어서 바꿨습니다)
> - ⚠️ 비어 있는 이유가 둘입니다: **아직 그날이 안 지남**, 또는 **그날 계획이 없어서 달성률을 구할 수 없었음**. 둘 다 "맞았는지 알 수 없음" 이라 한 칸으로 두었습니다. 구별이 필요해지면 칸을 더해 주세요.
> - 다시 예측해도 **실제 달성률이 이미 채워진 줄은 고치지 않고**, 고칠 때도 위험 수준과 이유만 바꿉니다. (결과를 본 뒤에 예측을 바꾸는 일을 막으려고)
> - `user_id` 가 있어서 탈퇴하면 예측이 같이 지워집니다. 딸린 테이블은 `AccountServiceTest` 의 `CHILD_TABLES` 에 더했습니다.
> - **칸 이름은 AI 가 정했습니다.** `forecast_date`(예측한 날), `risk_level`(위험 수준), `actual_achievement_rate`(실제 달성률), `was_correct`(맞았는지), `reason`(이유). `level` 은 DB 예약어와 겹칠 수 있어 `risk_level` 로 했습니다. 그림에서 생각한 이름과 같은지 봐 주세요.
> - 규칙과 근거는 [docs/RISK-FORECAST.md](./RISK-FORECAST.md) 에 있습니다.

> `study_plans` 의 `start_time` · `kind` · `routine_id` 는 2026-10-12 에 이슈 #12 에서 더했습니다. (칸 이름과 뜻은 Project#12 의 표, 시작 시각을 `time` 으로 저장하는 것은 학생이 정함)
> - 공부 계획(`STUDY`)은 `subject_id` 가 꼭 있어야 하고, 노는 계획(`PLAY`)만 비어도 됩니다. DB 에도 검사 규칙(`ck_study_plans_study_has_subject`)으로 걸어 두었습니다.
> - 루틴으로 만든 계획은 routine_id를 기록합니다(#85). 루틴 삭제 후에도 원본 번호와 적용 기록을 유지하므로 그림의 선은 논리적인 관계이고 DB의 삭제 제약은 아닙니다.

> **운영 DB 에만 있는 `seed_6m_rows` 표** — 2026-10-24 발표용 가상 데이터 6개월치를 넣으면서, 넣은 줄의 번호를 적어 둔 표입니다.
> 엔티티가 없어서 위 그림 · `db/schema.sql` 에는 없습니다. 지울 때 이 표를 씁니다. → [DEMO-SEED.md](./DEMO-SEED.md)

> 변형 문제의 8개 테이블(`source_problems` · `source_problem_choices` · `review_items` · `variants` · `variant_choices` · `weekly_sets` · `assignments` · `attempts`)은 2026-10-24 에 더했습니다. 규칙과 근거는 [docs/VARIANT-SPEC.md](./VARIANT-SPEC.md) 4장입니다.
> - ⚠️ **이 테이블들은 팀의 설계 문서(claude.ai 에서 만든 변형 모듈 SPEC)를 그대로 옮긴 것이고, 이 저장소의 방식대로 학생이 직접 설계한 것이 아닙니다.** 담당 학생이 그림을 보고 칸 이름 · 뜻을 확인해 주세요.
> - 모든 테이블에 `user_id` 가 있어서 탈퇴하면 같이 지워집니다. 테이블끼리는 **번호만 적고 외래키를 걸지 않습니다**(그림의 "번호만" 선). 탈퇴할 때 테이블을 순서 없이 지우기 때문입니다. 선지 딸린 테이블 두 개는 `AccountServiceTest` 의 `CHILD_TABLES` 에 더했습니다.
> - "같은 원본에 ACTIVE 학습 항목은 하나" · "한 항목에 하루 한 과제" 는 원래 설계의 **부분 유니크 인덱스** 대신 **있을 때만 채우는 칸 + 유니크**(`active_source_problem_id`, `daily_review_item_id`)로 지킵니다. 로컬 DB(H2)가 부분 인덱스를 지원하지 않기 때문입니다. 비어 있는(null) 칸끼리는 겹친다고 보지 않습니다.
> - `value` 는 H2 예약어라서 `value_expr` 로 했습니다. (주간 세트는 같은 저장소의 weekly_goals 처럼 `week_start` 날짜를 씁니다)

## 그림 읽는 법

- **네모 하나 = 테이블 하나 = 엔티티 클래스 하나**
- **선** `||--o{` = "왼쪽 하나가 오른쪽 여러 개를 가진다" (예: 사용자 한 명이 메모 여러 개)
- **PK** = 그 줄을 구분하는 고유 번호
- **FK** = 다른 테이블의 줄을 가리키는 번호 (예: `user_id` 는 `users` 의 어느 줄인지)
- ⚠️ 지금은 DB 가 FK **제약**을 걸지 않습니다. (엔티티에 `Long userId` 처럼 번호만 적기 때문입니다)
  그래서 "없는 사용자", "남의 과목" 이 붙지 않게 하는 검사는 **API 쪽(#10~#13)에서** 합니다.

## 실제로 만들어진 모양 보기

그림은 엔티티 코드를 읽어 그린 것입니다. DB 에 **실제로** 만들어진 테이블은 서버를 켠 뒤 여기서 봅니다.

👉 http://localhost:8080/h2-console (`JDBC URL` 에 `jdbc:h2:mem:projectbe`, `User Name` 에 `sa`)
