# 변형 문제 명세서 (Spring Boot 판)

> "변형 문제 제공" 기능의 **단일 기준 문서**입니다. 코드와 이 문서가 다르면 이 문서가 기준이고,
> 바꿔야 하면 **이 문서를 먼저 고치고** 코드를 고칩니다.
>
> 원본은 claude.ai 설계 대화에서 만든 Python/FastAPI 키트(`variant-module`)의 `docs/SPEC.md` v1 입니다.
> 이 저장소가 Spring Boot 라서 자바로 옮겼고, 옮기면서 바뀐 점은 **0-1 표**에 모두 적었습니다.
> 교육 규칙(변형 4단계, 출제 · 졸업 규칙)은 원본과 **같습니다.** 심화 세트의 주기와 원본이 들어오는 곳은 2026-10-24 에 바꿨습니다(12장).
>
> 표시
> - **⏳** : 이 문서에는 정했지만 **코드에는 아직 없는** 부분입니다. (이슈 #394)
> - **🟡 제안(확인 필요)** : 결정에서 바로 나오지 않아 문서를 쓰며 채운 부분입니다. 팀이 확인하면 표시를 지웁니다.

## 0. 확정된 결정 사항

| 항목 | 결정 |
|---|---|
| 정답 검증 | 검증되지 않은 문제는 **어떤 경우에도 학생에게 나가지 않습니다.** **수학**은 AI 가 문제와 함께 **Symja 계산 코드**를 만들고, 샌드박스에서 계산한 값이 AI 가 말한 정답과 같을 때만 출제합니다. **수학이 아닌 과목**은 계산할 수 없으므로, 정답을 숨긴 채 AI 에게 **따로 풀게 해서(G7 독립 풀이)** 같을 때만 출제합니다. ⏳(수학 밖) |
| 변형 단계 | 교육학 원리에 근거한 4단계(L-1, L0, L1, L2). 2장. |
| 출제 일정 | 틀린 문제는 **매일** 검증 문제(변형)로 출제합니다. **매주 월요일** 심화 세트(맞힌 문제의 대표 유형 심화 + 극복한 오답의 심화)를 냅니다. 3장. ⏳(주간) |
| 졸업 조건 | L0 정답 다음에 L1 정답(서로 다른 날). 3장 상태 머신. |
| 원본이 들어오는 곳 | ① **확인 문제**(공부 카드를 끝내고 푸는 4지선다, `POST /api/v1/ai/quiz`)의 결과 ⏳ ② 원본 등록 API(사람 · 시드). 사진 채점과의 연결은 아직 정하지 않았습니다(#401). 5장. |
| 문제 형식 | 5지선다(MC5) 또는 단답형(SHORT, 정답은 1 이상 999 이하 자연수). 🟡 제안(확인 필요): 수학이 아닌 과목은 **MC5 만** 씁니다. |
| 범위 | **수학**: 고1 공통수학1(2022 개정) 중 그림 · 그래프 없이 글과 수식만으로 풀 수 있는 문항. **수학이 아닌 과목** ⏳: 확인 문제로 들어온 고등학교 국어 · 영어 · 사회 · 과학 문항(이 저장소의 2022 개정 성취기준 파일 `resources/curriculum/2022-*.json` 에 있는 과목). |
| 기술 스택 | 이 저장소 그대로(Java 21, Spring Boot 4.1, JPA, H2/Neon). 수식 계산만 **Symja**(`org.matheclipse:matheclipse-core:3.0.0`)를 더합니다. AI 는 이미 있는 `AiClient`(OpenAI)를 씁니다. |

### 0-1. 원본(Python 키트)에서 바뀐 점

| 원본 | 이 저장소 | 이유 |
|---|---|---|
| SymPy 로 쓴 `compute_answer()` 파이썬 함수 | **Symja 식 프로그램**(`;` 로 문장을 잇고 마지막 값이 정답) | 자바에는 SymPy 가 없습니다. Symja 는 자바용 수식 계산 라이브러리입니다. |
| 파이썬 하위 프로세스 + RLIMIT | **따로 띄운 자바 프로그램**(`VariantSandboxMain`) + 시간 제한 + 메모리 제한(`-Xmx`) | AI 가 쓴 식을 서버 안에서 계산하지 않기 위해서입니다. RLIMIT 는 리눅스 전용이라 `-Xmx` 와 강제 종료로 대신합니다. |
| G3: 파이썬 AST 검사(금지 이름 목록) | **허용 목록 검사**: 식을 계산하지 않고 모양만 읽어서, 허용한 함수 이름만 쓰였는지 봅니다. | 금지 목록은 빠뜨리기 쉽습니다. 허용 목록이 더 안전합니다. |
| SymPy 의 `FiniteSet`, `Interval`, `Matrix` 선지 | 수 · 목록(집합) · 행렬 · **x 에 대한 부등식 조건**(`1 < x < 3`, `x < 1 \|\| x > 3`) | Symja 의 구간 계산은 이차부등식에서 멈추는 것을 확인했습니다(스파이크). 부등식 답은 조건식으로 쓰고, 경계값과 그 사이 값을 대입해 같은지 판정합니다. |
| Anthropic SDK, `submit_variant` 도구 강제 호출 | `AiClient.askLong` + **Structured Outputs**(답 JSON 모양 고정) | 이 저장소의 AI 호출 규칙(키는 서버에만, 호출은 `gpt_usages` 에 기록)을 그대로 따릅니다. |
| 설정 LLM_MODEL, LLM_TEMPERATURE_* | 모델은 공용 설정 `app.ai.model` 을 씁니다. 온도는 쓰지 않습니다. | `AiClient` 가 온도를 보내지 않고, 다른 기능과 같은 모델 · 키를 씁니다. |
| 토큰 수(input_tokens, output_tokens) 저장 | 칸은 두되 지금은 비워 둡니다(null). | `AiClient` 가 토큰 수를 돌려주지 않습니다. 비용은 `gpt_usages`(종류 `VARIANT`)로 셉니다. |
| uuid 기본키, `student_id text` | `bigint` 기본키, `user_id bigint` (로그인 토큰에서 얻음) | 이 저장소의 규칙입니다. 요청에서 사용자 번호를 받지 않습니다. |
| 테이블 사이 외래키 | 외래키 없이 **번호만 적습니다**. 모든 테이블에 `user_id` 가 있습니다. | 탈퇴할 때 `user_id` 가 있는 테이블을 순서 없이 지웁니다(`AccountService.withdraw`). 외래키가 있으면 지우는 순서에 따라 실패합니다. |
| 부분 유니크 인덱스 (`where status = 'ACTIVE'`) | **"있을 때만 채우는 칸" + 유니크**: `active_source_problem_id`, `daily_review_item_id` | H2(로컬 · 테스트 DB)가 부분 인덱스를 지원하지 않습니다. 비어 있는(null) 칸끼리는 겹친다고 보지 않는 성질을 씁니다. |
| 날짜: 한국 시간 자정 기준 | **새벽 4시 기준**(`DayRule`) | 이 저장소의 모든 "날짜별" 계산이 새벽 4시 기준입니다. 한 곳만 다르면 숫자가 서로 안 맞습니다. |
| APScheduler (02:00 생성, 23:59 만료, 1일 03:00 월간) | 예약 작업 주소 `POST /api/v1/jobs/{이름}` 를 바깥 스케줄러(cron-job.org)가 부름 | 무료 서버는 잠들어서 서버 안 알람이 울리지 않습니다. (docs/JOBS.md) |
| 일일 조회 시 없으면 **즉시 생성** | 조회는 **이미 검증된 변형만 배정**합니다. 생성은 예약 작업이 합니다. 아직 못 만든 개수는 `preparingCount` 로 알려 줍니다. | 변형 하나에 AI 호출이 최대 3번(각 25초까지)이라, 조회 요청 안에서 10문항을 만들면 몇 분이 걸립니다. |
| 예약 작업이 한 번에 끝까지 생성 | 한 번 부를 때 **정해진 시간(기본 20초)까지만** 만들고 멈춥니다. 다음 호출이 이어서 합니다. | 부르는 쪽(cron-job.org)은 30초까지만 기다립니다. |
| **월간** 심화 세트 (매월 1일, 전월분) | **주간** 심화 세트 (매주 월요일, 지난주분) ⏳ | 2026-10-24 사용자 결정(#394). 월간은 없앱니다. |
| `include_answers=true` 는 정답지 전체 | **이미 답했거나 마감된 문항만** 정답 · 해설을 붙입니다. | 교사 역할이 아직 없어서, 학생 본인이 마감 전에 정답을 볼 수 있게 되면 절대 규칙 9를 어깁니다. |
| 원본은 앞단계(사진 OCR)가 보냄 | **확인 문제 결과**가 원본이 됩니다 ⏳ | 2026-10-24 사용자 결정(#394). 확인 문제에는 문제 글 · 선지 · 정답 · 학생 답이 다 있습니다. 사진 채점은 문제 글을 저장하지 않아서 아직 쓰지 못합니다(#401). |
| MVP 는 수학만 | **수학이 아닌 과목도** 변형합니다 ⏳ (예: 국어는 같은 지문에 다른 문제) | 2026-10-24 사용자 결정(#394). 검증은 G7 독립 풀이, 분류는 성취기준. |
| SANDBOX_MEMORY_MB 기본 1024 | 기본 **256** | 무료 Render 서버 메모리가 512MB 안팎입니다. 스파이크에서 계산 한 번에 쓴 메모리는 30MB 이하였습니다. |
| `config/taxonomy.yaml` | `src/main/resources/variant/taxonomy.json` (내용 같음) | 이미 있는 교육과정 파일(`curriculum/*.json`)과 같은 방식입니다. |
| 프롬프트 `prompts/*_v1.md` | `src/main/resources/prompts/variant/*_v2.md` | 검증 코드가 SymPy → Symja 로 바뀌어 프롬프트 내용이 달라졌습니다. 규칙(기존 파일 수정 금지)에 따라 v2 로 만들었습니다. |

## 1. 범위

포함: 원본 문항 등록(입력 계약), **확인 문제 결과를 원본으로 받기** ⏳, 단원 · 유형(수학) / 성취기준(수학 밖) 분류, 변형 문항 생성, 정답 검증, 일일 출제, 채점, 학습 상태 갱신, **주간 심화 세트** ⏳, 생성 품질 평가.

제외: 사진 OCR(사진 채점 연결은 #401 에서 따로 정함), 타이머 · 캘린더, 통계 시각화, PDF 렌더링(단, 데이터 조회 API 는 제공), 알림 · 전화.

## 2. 변형 단계와 교육학 근거

| 레벨 | 이름 | 근거 원리 | 생성 규칙 | 쓰이는 곳 |
|---|---|---|---|---|
| L-1 | 비계 | 비계 설정과 근접발달영역(Vygotsky), 풀이 예제 점진 제거(인지부하 이론) | 원본과 같은 상황, 더 단순한 수치. 풀이를 2~3단계로 나누고 1단계는 풀이 예시로 보여 준 뒤 마지막 단계의 답만 묻습니다. | L0에서 연속 2회 오답 |
| L0 | 재인출(근전이) | 인출 연습과 시험 효과(Roediger & Karpicke) | 같은 개념, 같은 풀이 순서. 수치 · 문자 · 계수 · 표현만 바꿉니다. | 오답 일일 출제의 기본값 |
| L1 | 변형(중간 전이) | 연습의 다양성 | 같은 핵심 개념, 다른 조건 제시 방식이나 맥락. 풀이 단계는 원본보다 0~1단계 많습니다. | 졸업 검증 |
| L2 | 심화(원전이) | 바람직한 어려움(Bjork), 개정 블룸 분류의 분석 수준 | 역으로 구하기, 같은 단원의 다른 유형과 결합, 경우를 나누는 다단계 추론 중 하나 이상. 풀이 단계는 1~2단계 많습니다. 고1 범위를 넘지 않습니다. | **주간 심화** |

- 형식: 정답이 1~999 자연수가 되도록 자연스럽게 만들 수 있으면 SHORT, 아니면 MC5. AI 가 정하고 G2 가 검사합니다.
- 진단형 선지: MC5 의 오답 선지 4개는 그 유형의 실제 오개념 · 계산 실수에서 만들고, 선지마다 어떤 실수인지 한 문장(선지 해설)을 저장합니다. 학생이 그 선지를 고르면 채점 결과와 함께 보여 줍니다.
- 선지 위치: AI 가 정한 정답 위치는 쓰지 않습니다. 검증을 통과하면 서버가 선지와 선지 해설을 함께 섞고 정답 번호를 다시 계산합니다.

### 2-1. 수학이 아닌 과목 ⏳

- 레벨의 뜻(L-1 ~ L2)과 근거 원리는 위 표와 같습니다. 원본의 **성취기준** 범위를 넘지 않습니다.
- **지문이 있는 문항**(국어 독해, 영어 독해 등)은 **같은 지문에 다른 문제**를 냅니다. 지문은 확인 문제가 함께 준 것을 씁니다(5-1).
- 🟡 제안(확인 필요): 과목별 레벨 예시
  - L0: 같은 지문 · 같은 개념에서 묻는 대상만 바꿈 (예: 다른 문단의 중심 내용, 다른 낱말의 뜻)
  - L1: 같은 지문에서 다른 관점 · 다른 문제 유형 (예: 내용 일치 → 추론)
  - L2: 지문 전체를 종합하거나 다른 상황에 적용
  - L-1: 문제를 2단계로 나누고 1단계(근거 문장 찾기 등)를 예시로 보여 준 뒤 2단계만 묻습니다. 본문에 `[1단계]`, `[2단계]` 표지(G2)는 수학과 같습니다.
- 🟡 제안(확인 필요): 형식은 **MC5 만**(SHORT 없음). 선지는 수식이 아니라 **글**이고, 선지 해설 규칙(진단형)은 같습니다.

## 3. 출제 규칙

### 3-1. 학습 항목(review_items)
틀린 원본 1개당 학습 항목 1개. 같은 원본에 ACTIVE 항목은 최대 1개입니다.

- 상태: ACTIVE(일일 출제 중), GRADUATED(졸업, 주간 심화 대기), MASTERED(주간 심화 통과)
- 레벨: ACTIVE 일 때 -1, 0, 1
- wrongStreak: 지금 레벨에서 연속 오답 수. 레벨이 바뀌면 0
- needsAttention: 비계 단계에서도 계속 틀리는 항목 표시

만들 때: ACTIVE, 레벨 0, wrongStreak 0, nextDueDate = 푼 날(새벽 4시 기준) 다음 날.

### 3-2. 일일 결과 전이표 (`ReviewItemRules`, 순수 함수)

| 지금 레벨 | 결과 | 처리 |
|---|---|---|
| L0 | 정답 | 레벨 L1 |
| L0 | 오답 | wrongStreak+1. 2가 되면 레벨 L-1 |
| L1 | 정답 | GRADUATED, graduatedAt 기록, nextDueDate 비움 |
| L1 | 오답 | 레벨 L0 |
| L-1 | 정답 | 레벨 L0, needsAttention=false |
| L-1 | 오답 | wrongStreak+1. 3 이상이면 needsAttention=true (레벨 유지) |

ACTIVE 로 남는 모든 전이 뒤 nextDueDate = 답한 날 다음 날. 레벨이 바뀌면 wrongStreak=0. 임계값(2, 3)은 설정값입니다.

### 3-3. 일일 출제
- 그날 대상: nextDueDate ≤ 오늘인 ACTIVE 항목. 우선순위는 needsAttention 먼저, 그다음 nextDueDate 오래된 순, 그다음 만든 순.
- 상한: 하루 `daily-max-items`(기본 10)문항. 오늘 이미 배정한 수를 뺀 만큼만 위에서부터 고릅니다. 넘친 항목은 다음 날로 이월됩니다.
- 배정: 고른 항목마다 **그 원본 · 그 레벨의 검증된(VERIFIED) 변형 중 아직 아무 과제에도 쓰이지 않은 것** 하나를 씁니다. 없으면 예약 작업이 만듭니다.
- 생성 실패: 그날 그 원본 · 레벨로 거절된 시도가 `max-generation-attempts`(3)번 이상이면 오늘은 더 만들지 않고 이월합니다. 검증 안 된 문항으로 대체하지 않습니다.
- 미응답: 날이 바뀌면 그날의 PENDING 일일 과제는 EXPIRED. 학습 항목은 바뀌지 않고 다음 날 다시 대상이 됩니다(벌점 없음).

### 3-4. 주간 심화 세트 ⏳ (2026-10-24, 월간을 바꿈)
**매주 월요일**(새벽 4시 기준 날짜)에 **지난주(월요일 ~ 일요일)** 분을 학생별로 만듭니다. 마감은 세트를 만든 날 + `weekly-due-days`(7)일.

(A) 맞힌 문제 대표 유형 심화
1. 지난주에 맞힌 원본(isCorrect=true, SUPPORTED)을 **유형**으로 묶습니다. 유형은 수학이면 유형 코드(`type_code`), 수학이 아닌 과목이면 **성취기준 코드**입니다. (푼 날짜는 새벽 4시 기준)
2. 개수가 많은 순으로 상위 `weekly-correct-types`(5)개 유형. 동률이면 최근에 푼 유형 먼저.
3. 유형마다 가장 최근 원본 1개를 대표로 삼아 L2 변형.

(B) 극복한 오답 심화
1. GRADUATED 항목을 graduatedAt 오래된 순으로 최대 `weekly-wrong-max`(10)개. **ACTIVE 항목(아직 매일 풀고 있는 문제)은 넣지 않습니다.**
2. 각 원본의 L2 변형.

| 출처 | 결과 | 처리 |
|---|---|---|
| (A) | 정답 | 기록만 |
| (A) | 오답 | 그 원본에 ACTIVE 항목이 없으면 새 항목(origin=WEEKLY_CORRECT_FAIL, **레벨 1**, 다음 날부터 일일 출제). 원본을 맞혔으므로 L0을 건너뜁니다. |
| (B) | 정답 | MASTERED, masteredAt 기록 |
| (B) | 오답 | ACTIVE 로 복귀, **레벨 0**, wrongStreak 0, 다음 날부터 일일 출제 |

마감까지 답하지 않은 주간 과제는 EXPIRED, 상태 변화 없음. (B) 항목은 다음 주 세트에 다시 들어갑니다.
(결과 처리 규칙은 월간 때와 같습니다 — 2026-10-24 사용자 결정)

## 4. 데이터 모델

모든 테이블에 `user_id` 가 있고, 테이블끼리는 번호만 적습니다(외래키 없음). 선지처럼 여러 개인 값은 딸린 테이블(`@ElementCollection`)에 담습니다.

| 테이블 | 한 줄 | 주요 칸 |
|---|---|---|
| `source_problems` | 학생이 푼 원본 문항 하나 | problem_text, correct_answer, student_answer, is_correct, has_figure, unit_code, type_code, classification_source(UPSTREAM/LLM), classification_confidence, support_status, fingerprint, solved_at |
| `source_problem_choices` | 원본 선지 하나 | source_problem_id, position, text |
| `review_items` | 틀린 원본의 학습 항목 하나 | source_problem_id, active_source_problem_id(ACTIVE 일 때만 채움, 유니크), origin, status, current_level, wrong_streak, needs_attention, next_due_date, graduated_at, mastered_at |
| `variants` | 생성 시도 하나(통과 · 거절 모두) | source_problem_id, level, format, problem_text, answer_index, short_answer, computed_latex, solution, verification_code, verification_log, fingerprint, status(VERIFIED/REJECTED), rejection_reason, model, prompt_version, generation_attempt |
| `variant_choices` | 변형 선지 하나 (섞은 뒤 순서) | variant_id, position, value_expr, latex, rationale |
| `weekly_sets` ⏳ (이전 `monthly_sets`) | 한 학생의 한 주 세트 | week_start(지난주 월요일), due_date. (user_id, week_start) 유니크 |
| `assignments` | 학생에게 낸 과제 하나 | variant_id, review_item_id, daily_review_item_id(DAILY 일 때만 채움), source_problem_id, kind(DAILY · WEEKLY_CORRECT_TYPE · WEEKLY_WRONG_DEEP ⏳), weekly_set_id ⏳, assigned_date, due_date, status. (daily_review_item_id, assigned_date) 유니크 |
| `attempts` | 답 하나 (과제당 1번) | assignment_id(유니크), submitted_answer, is_correct, time_spent_sec, answered_at |

fingerprint: 문제 글에서 공백과 LaTeX 서식 명령(`\left`, `\right`, `\,` 등)을 지우고 소문자로 바꾼 뒤 SHA-256.

### 4-1. 확인 문제 연결 · 수학 밖 과목 때문에 더할 칸 ⏳ 🟡 제안(확인 필요)

**테이블 설계는 학생이 하는 공부**라서, 아래는 필요한 정보만 적은 제안입니다. 칸 이름과 모양은 담당 학생이 정합니다.

| 테이블 | 더할 정보 | 왜 |
|---|---|---|
| `source_problems` | 과목, 성취기준 코드, 지문, 원본이 어디서 왔는지(확인 문제 · 직접 등록 · 시드) | 수학 밖 과목 분류(3-4 의 유형), 같은 지문 변형(2-1), 통계 |
| `variants` | 검증 방법(Symja 계산 · AI 독립 풀이) | 수학 밖 과목은 검증 코드가 없음. 어느 쪽으로 검증했는지 남김 |
| `review_items` | origin 값 `WEEKLY_CORRECT_FAIL` (이전 `MONTHLY_CORRECT_FAIL`) | 3-4 |

## 5. 입력 계약

`POST /api/v1/variant/source-problems` (로그인 필요) — 사람 · 시드 · 나중의 다른 입구가 원본 하나를 등록합니다.

```json
{
  "problemText": "이차방정식 $x^2-5x+k=0$의 두 근의 곱이 6일 때, 상수 $k$의 값을 구하시오.",
  "choices": null,
  "correctAnswer": "6",
  "studentAnswer": "5",
  "isCorrect": false,
  "hasFigure": false,
  "unitCode": null,
  "typeCode": null,
  "solvedAt": 1792841400000
}
```

1. hasFigure=true → UNSUPPORTED_FIGURE 로 저장만 하고 학습 항목은 만들지 않습니다(통계에는 남습니다).
2. unitCode · typeCode 를 주면 분류체계에 있는지(그리고 그 유형이 그 단원 것인지) 검사합니다. 없으면 400. 있으면 classificationSource=UPSTREAM, SUPPORTED. 둘 중 하나만 주면 400.
3. 코드가 없으면 분류기로 바로 분류합니다. AI 가 꺼져 있거나 답이 없으면 PENDING_CLASSIFICATION.
4. 분류 결과의 신뢰도가 `classify-min-confidence`(0.6) 미만이거나 분류체계에 없는 코드면 NEEDS_REVIEW, 그림이 필요하다고 하면 UNSUPPORTED_FIGURE.
5. isCorrect=false 이고 SUPPORTED 면 학습 항목을 만듭니다.
6. 응답 201: `{ "id", "supportStatus", "unitCode", "typeCode", "reviewItemId" }`

### 5-1. 확인 문제 결과 ⏳ (2026-10-24 결정, #394)

**흐름**
1. 학생이 공부 카드를 끝내면 FE 가 **"확인 문제 풀어 볼래?"** 를 권합니다. 누르면 그 카드 주제로 확인 문제(`POST /api/v1/ai/quiz`, 4지선다)를 시작합니다. 건너뛸 수 있습니다.
2. 확인 문제는 문제마다 **그 문제가 기대는 지문**도 돌려줍니다. (확인 문제 기능의 응답에 칸 추가 — 그 기능 담당과 맞춤)
3. 다 풀면 FE 가 결과를 서버로 보냅니다. **"찍은 것 같다"(`guessed`) 고 본 결과는 틀린 문제만** 보냅니다. (찍어서 맞힌 것은 '아는 문제' 로 치지 않음)
4. 서버는 문제마다:
   1. **정답 확인**: 정답을 숨긴 채 AI 에게 따로 풀게 해서, 확인 문제의 정답과 다르면 **원본으로 쓰지 않습니다.** (확인 문제도 AI 가 만든 것이라 정답이 틀릴 수 있음)
   2. **분류**: 수학이면 지금 분류체계(단원 · 유형), 수학이 아닌 과목이면 **2022 개정 성취기준**(`resources/curriculum/2022-*.json`). 그 밖은 5장 3~4 와 같음.
   3. **맞음 여부는 서버가 다시 셉니다** (고른 번호 = 정답 번호). FE 가 보낸 판정을 믿지 않습니다.
   4. 원본으로 저장 → 틀렸으면 학습 항목(매일 검증), 맞았으면 저장만(주간 심화 (A) 의 재료).

🟡 제안(확인 필요): 요청 · 응답 모양

`POST /api/v1/variant/quiz-results` (로그인 필요)

```json
{
  "topic": "국어 비문학 3지문",
  "solvedAt": 1792841400000,
  "guessed": false,
  "questions": [
    { "question": "윗글의 내용과 일치하는 것은?", "passage": "…지문…", "choices": ["…", "…", "…", "…"],
      "answerIndex": 2, "chosenIndex": 1 }
  ]
}
```

응답 200: `{ "registered": 2, "skipped": { "answerMismatch": 1, "guessedCorrect": 0, "unsupported": 0 } }`
- 문제는 한 번에 5개까지(확인 문제 최대 개수), 지문은 2000자까지(확인 문제 자료 글 한도와 같게).
- 시연용 예시 문제(`isDemo`)는 FE 가 보내지 않습니다.
- 정답 확인 · 분류에 AI 를 부르므로 응답이 늦을 수 있습니다. 🟡 FE 는 기다리지 않고 보내기만 하거나, 서버가 받기만 하고 예약 작업이 나중에 처리하는 방식 중 하나를 정해야 합니다.

## 6. 생성 · 검증 파이프라인

```
원본 + 레벨 + 피해야 할 기존 변형(최근 VERIFIED 5개) + 직전 실패 사유
  → [생성] AiClient.askLong (답 JSON 모양 고정)
  → G1 모양 → G2 형식 → G3 코드 안전 → G4 샌드박스 실행 → G5 정답 일치 → G6 중복 → (옵션) G7 독립 풀이
  → 통과: 선지 섞기 → VERIFIED 저장
  → 실패: REJECTED 저장, 실패 사유를 붙여 다시 생성 (최대 max-generation-attempts = 3)
  → 모두 실패: 빈 값, 경고 로그
```

수학이 아닌 과목은 6-4 의 순서를 씁니다.

### 6-1. AI 가 돌려주는 모양

```json
{ "problemText": "...", "format": "MC5", "choices": ["3/2", "2*Sqrt(3)", "..."], "answerIndex": 1,
  "shortAnswer": null, "distractorRationales": ["...", "", "..."], "solution": "...",
  "verificationCode": "k = 2*3; r = x /. Solve(x^2 - 5*x + k == 0, x); Max(r)", "levelJustification": "..." }
```

choices 는 Symja 식입니다. 쓸 수 있는 값의 종류:

| 종류 | 예 | 같은지 판정 |
|---|---|---|
| 수 (정수 · 분수 · 무리수 · 복소수) | `3/2`, `2*Sqrt(3)`, `-1+2*I` | `PossibleZeroQ(a - b)` |
| 목록 = 집합 (순서 무시) | `{-1, 2}` | 겹치는 값을 빼고 정렬해서 원소마다 수 판정 |
| 행렬 | `{{1,2},{3,4}}` | 크기가 같고 성분마다 수 판정 |
| x 에 대한 부등식 조건 | `1 < x < 3`, `x < 1 \|\| x > 3`, `x <= -2 \|\| x >= 2` | 두 조건의 경계값 전부와 그 사이 · 바깥 값을 대입해 참 · 거짓이 모두 같으면 같음 |

종류가 다르면 다릅니다. 화면용 LaTeX 는 AI 가 쓰지 않고 샌드박스가 `TeXForm` 으로 만듭니다. 그래서 표시와 값이 어긋날 수 없습니다.

### 6-2. 게이트

| 게이트 | 검사 | 어디서 |
|---|---|---|
| G1 모양 | 답이 약속한 JSON 모양인지, 글자 수 한도(문제 2000, 해설 4000, 코드 2000, 선지 200, 선지 해설 300) | 서버 |
| G2 형식 | MC5: 선지 5개, answerIndex 0~4, 선지 해설 5개이고 정답 칸만 빈 글자, shortAnswer null. SHORT: shortAnswer 1~999, 선지 · answerIndex · 선지 해설 null. L-1: 본문에 `[1단계]`, `[2단계]` | 서버 |
| G3 코드 안전 | 식을 **계산하지 않고 모양만 읽어서**(Symja 파서): 허용 목록 밖의 함수 · 기호 금지, 글자(따옴표) 금지, 밑줄 · `$` 로 시작하는 이름 금지. 코드는 수만 있는 식(하드코딩)이면 거부. 선지는 값만 허용(`Solve` 같은 계산 함수 금지) | 서버 (계산 안 함) |
| G4 실행 | 샌드박스에서 코드 계산. 시간 · 메모리 제한 | 샌드박스 |
| G5 정답 일치 | SHORT: 계산값이 정수이고 shortAnswer 와 같음. MC5: 계산값이 정답 선지와 같고, 다른 어떤 선지와도 다르고, 선지끼리 모두 다름 | 샌드박스 |
| G6 중복 | fingerprint 가 원본, 같은 원본의 기존 VERIFIED 변형과 다름 | 서버 |
| G7 독립 풀이 | 정답을 숨긴 채 AI 에게 따로 풀게 해서 비교. 수학은 `enable-independent-solve-check` 로 켬(옵션), **수학이 아닌 과목은 항상 켬(필수)** | 서버 + AI |

### 6-3. 샌드박스 계약
- 실행: 지금 서버와 같은 자바 · 같은 클래스 경로로 `VariantSandboxMain` 을 **새 프로세스**로 띄웁니다. 옵션 `-Xmx{sandbox-memory-mb}m`, 임시 폴더를 작업 폴더로, 환경변수는 비웁니다. `sandbox-timeout-sec`(10) 안에 안 끝나면 강제로 끕니다.
  배포용 jar(Spring Boot 실행 jar)로 돌 때는 `PropertiesLauncher` 로 같은 jar 안의 `VariantSandboxMain` 을 띄웁니다.
- 입력(stdin): `{"code": "...", "format": "MC5", "choices": ["..."], "answerIndex": 2, "shortAnswer": null}`
- 출력(stdout): `{"ok": true, "computed": "...", "computedLatex": "...", "choicesLatex": ["..."], "integerValue": null, "matchedIndices": [2], "choicesDistinct": true, "error": null}`
- AI 가 만든 글자를 Symja 로 **계산**하는 일은 전부 샌드박스 안에서만 합니다. 서버는 파서로 모양만 읽습니다.

### 6-4. 수학이 아닌 과목의 게이트 ⏳

```
원본(+지문) + 레벨 + 피해야 할 기존 변형 + 직전 실패 사유
  → [생성] → G1 모양 → G2 형식(MC5) → G6 중복 → G7 독립 풀이(필수)
  → 통과: 선지 섞기 → VERIFIED 저장 (검증 방법: AI 독립 풀이)
```

- G3 · G4 · G5 는 계산할 식이 없어서 하지 않습니다. 검증 코드는 비어 있습니다(null).
- G7: 정답 · 해설을 숨긴 채 문제 · 지문 · 선지만 주고 AI 가 고른 번호가 answerIndex 와 같아야 합니다. 다르면 REJECTED("G7 …").
- 선지는 글입니다. 🟡 제안(확인 필요): 선지끼리 같은 글(공백 무시)이면 G2 에서 거부합니다.
- ⚠️ AI 독립 풀이는 계산만큼 확실하지 않습니다(두 AI 가 같이 틀릴 수 있음). 품질 평가(10장)에서 따로 잽니다.
- 프롬프트는 새 버전 파일로 만듭니다(기존 파일 수정 금지).

## 7. API

학생용 응답에는 답을 내기 전까지 정답, 해설, 검증 코드, 선지 해설을 절대 넣지 않습니다. 모두 로그인이 필요합니다.

| 방법 · 주소 | 설명 |
|---|---|
| `POST /api/v1/variant/source-problems` | 원본 등록(5장) |
| `POST /api/v1/variant/quiz-results` ⏳ | 확인 문제 결과를 원본으로(5-1) |
| `PATCH /api/v1/variant/source-problems/{id}/classification` | 분류 대기 · 확인 필요 원본의 단원 · 유형을 사람이 정함. SUPPORTED 로 바꾸고 틀린 원본이면 학습 항목을 만듦 (VAR-05). 그 밖의 상태면 409 |
| `GET /api/v1/variant/daily?date=YYYY-MM-DD` | 그날의 일일 과제. date 를 안 보내면 오늘(새벽 4시 기준). 검증된 변형이 준비된 항목만 배정하고(멱등), 아직 준비 중인 수는 `preparingCount` 로. 항목: assignmentId, level, format, problemText, choicesLatex, status |
| `POST /api/v1/variant/assignments/{id}/attempts` | 답 내기. `{ "answer": "3", "timeSpentSec": 95 }` (MC5 는 0~4 선지 번호, SHORT 는 정수). 채점 → 상태 전이 → `{ isCorrect, correctAnswer, correctAnswerLatex, solution, chosenDistractorRationale, reviewItem: {status, currentLevel} }`. 이미 답했거나 마감이면 409, 모양이 틀리면 400 |
| `GET /api/v1/variant/weekly?week=YYYY-MM-DD&includeAnswers=false` ⏳ (이전 `/monthly`) | 주간 세트. 🟡 제안(확인 필요): `week` 는 세트가 다루는 주의 월요일(지난주 월요일), 안 보내면 가장 최근 세트. 번호, 출처(a/b), problemText, choicesLatex, status. includeAnswers=true 면 **답했거나 마감된 문항만** answerKey(정답, 해설) |
| `GET /api/v1/variant/review-items?status=active` | 오답노트 현황: 원본, 단원 · 유형, 상태, 레벨, needsAttention, nextDueDate |

JSON 의 상태 · 형식 값은 이 저장소 규칙대로 **소문자**(`supported`, `mc5`, `active` …)로 주고받습니다.
수학이 아닌 과목의 선지는 수식이 아니라 글이라서, 🟡 제안(확인 필요): `choicesLatex` 에 글을 그대로 담고 FE 가 `$…$` 만 수식으로 그립니다.

## 8. 예약 작업

`POST /api/v1/jobs/{이름}` (헤더 `X-Job-Key`). 모두 멱등입니다. 한 번에 `job-budget-sec`(20)초까지만 일하고, 남은 일은 다음 호출이 이어서 합니다.

| 작업 | 언제 부르나 (cron-job.org, 한국 시간) | 내용 |
|---|---|---|
| `variant-expire` | 매일 04:05 | 지난 날의 PENDING 일일 과제와 마감이 지난 주간 과제를 EXPIRED |
| `variant-daily` | 매일 04:10 ~ 08:00, 10분마다 | 학생마다 3-3 대상 → 변형 생성 → 과제 배정 |
| `variant-weekly` ⏳ (이전 `variant-monthly`) | 매주 월요일 04:10 ~ 08:00, 10분마다 | 월요일에만 일합니다. 지난주 세트(3-4) 생성 → L2 변형 생성 → 과제 배정 |

## 9. 설정값 (`app.variant.*`, application.yml)

| 이름 | 기본값 |
|---|---|
| max-generation-attempts | 3 |
| enable-independent-solve-check | false (수학만. 수학이 아닌 과목은 설정과 상관없이 항상 켬) |
| sandbox-timeout-sec | 10 |
| sandbox-memory-mb | 256 |
| daily-max-items | 10 |
| l0-wrong-streak-to-drop | 2 |
| floor-wrong-streak-to-flag | 3 |
| weekly-correct-types ⏳ (이전 monthly-correct-types) | 5 |
| weekly-wrong-max ⏳ (이전 monthly-wrong-max) | 10 |
| weekly-due-days ⏳ (이전 monthly-due-days) | 7 |
| classify-min-confidence | 0.6 |
| job-budget-sec | 20 |

AI 키 · 모델은 공용 설정(`app.ai.openai-api-key`, `app.ai.model`)입니다. 내 컴퓨터(local)에서는 AI 가 꺼져 있어서 분류는 PENDING_CLASSIFICATION, 생성은 하지 않습니다.

## 10. 품질 평가

`VariantGenerationEvalTest` 가 시드 원본 × 레벨(-1, 0, 1, 2) × N 개를 **실제 AI 로** 만들어 `build/variant-eval/` 에 CSV 와 요약을 씁니다.
`LLM_LIVE_TESTS=1`, `OPENAI_API_KEY`, `OPENAI_MODEL` 환경변수가 있을 때만 돕니다(평소 `./gradlew build` 에서는 건너뜀). 키는 사람이 넣고 사람이 돌립니다.

- 자동 지표: 게이트별 통과율, 레벨별 최종 성공률(3회 이내), 평균 시도 횟수
- 사람 검수 칸(빈칸): 정답 정확(Y/N), 레벨 적합(Y/N), 문장 자연스러움(1~5)
- 통과 기준: 최종 성공률 85% 이상, 사람 검수 정답 정확 100%, 레벨 적합 80% 이상. 미달이면 프롬프트 v3 를 만들고 다시 평가합니다.
- **품질 평가를 통과하기 전에는 실제 학생에게 출제하지 않습니다.** (팀 합의 필요)
- ⏳ 수학이 아닌 과목은 지금 시드가 없습니다(시드 30문항은 모두 수학). 과목별 시드를 **직접 써서** 같은 방법으로 따로 평가하고, 통과 전에는 그 과목을 출제하지 않습니다. 🟡 제안(확인 필요): 과목별 시드 수 · 기준은 수학과 같게.

## 10-1. 키트 이슈(VAR-00 ~ 13)와 이 저장소의 코드 · 테스트

| 이슈 | 코드 (`domain/variant/`) | 테스트 |
|---|---|---|
| VAR-00 기반 | `VariantSettings`, build.gradle(Symja), application.yml `app.variant` | 전체 빌드 |
| VAR-01 분류체계 | `VariantTaxonomy`, `resources/variant/taxonomy.json` | `VariantTaxonomyTest` |
| VAR-02 DB | 엔티티 6개(테이블 8개, 선지 딸린 테이블 2개 포함), `db/schema.sql`, `db/migrations/NN-variant-problems.sql` | `SchemaSqlTest`, `MigrationSqlTest`, `AccountServiceTest`(탈퇴) |
| VAR-03 원본 등록 · 시드 | `SourceProblemService`, `VariantSeedData`, `VariantSeedInitializer`, `resources/variant/seed-source-problems.json` | `VariantFlowTest`, `VariantSeedDataTest` |
| VAR-04 AI 래퍼 · 프롬프트 | `generate/VariantAi`, `GptVariantAi`, `PromptTemplate`, `VariantPrompts`, `resources/prompts/variant/*_v2.md` | `PromptTemplateTest` |
| VAR-05 분류기 | `generate/SourceClassifier`, PATCH 분류 | `VariantFlowTest` |
| VAR-06 동치 판정 | `sandbox/SymjaValues` | `SymjaValuesTest` |
| VAR-07 샌드박스 | `sandbox/SymjaGuard`, `VariantSandbox`, `VariantSandboxMain` | `SymjaGuardTest`, `VariantSandboxTest` |
| VAR-08 · 09 생성 · 파이프라인 | `generate/VariantPipeline` | `VariantPipelineTest` |
| VAR-10 품질 평가 | (테스트 코드) `VariantEvalRunner`, `VariantGenerationEvalTest` | `VariantEvalRunnerTest`(가짜 AI). 실제 평가는 docs/VARIANT-EVAL.md |
| VAR-11 상태 머신 | `ReviewItemRules`, `ReviewItemState` | `ReviewItemRulesTest` |
| VAR-12 일일 출제 · API | `DailyAssignmentService`, `AttemptService`, `VariantQueryService`, `VariantController`, `VariantDailyJob`, `VariantExpireJob` | `VariantFlowTest`, `VariantControllerTest`, `VariantQueryServiceTest`, `LoginRequiredFlowTest` |
| VAR-13 월간 → **주간** ⏳ | 지금 코드는 `MonthlySetService`, `VariantMonthlyJob` (주간으로 바꿀 예정) | `VariantFlowTest` |
| (새) 확인 문제 연결 · 수학 밖 과목 ⏳ | (아직 없음) #394 | (아직 없음) |

GitHub 이슈: 전체 추적 #386, PR 0 ~ 6 은 #387 ~ #393, 확인 문제 연결 #394.

## 11. 다른 기능과의 연결(나중)

- 통계: assignments + attempts + variants(level) + source_problems(unit_code, type_code) 를 이으면 유형별 · 단원별 정답률, 미응답률.
- 주간 회고 "어필": review_items.needs_attention=true 목록.
- PDF 시험지: 주간 세트 API 의 includeAnswers 응답.
- 확인 문제 ⏳: 5-1. 확인 문제 기능(`/ai/quiz`, FE `lib/studyQuiz.ts`)과 공부 카드 화면은 다른 사람이 만든 기능이라, 바꾸기 전에 담당과 맞춥니다.
- 사진 채점: 문제 글을 저장하지 않아서 아직 잇지 않습니다. 문제 글을 어디서 얻을지 정해야 합니다(#401).

## 12. 결정 기록

| 날짜 | 결정 | 어디 |
|---|---|---|
| 2026-10-24 | 별도 Python 서비스 대신 Spring Boot 에 직접 구현, SymPy 대신 Symja | 0-1 |
| 2026-10-24 | 확인 문제 결과를 원본으로: 틀린 문제 → 매일 검증 + 졸업 뒤 주간 심화, 맞힌 문제 → 주간 심화 | 5-1, 3-4 |
| 2026-10-24 | 월간 심화 세트를 없애고 **주간**으로 (매주 월요일 새벽, 지난주분, 마감 7일, 유형 5개 · 졸업한 오답 10개) | 3-4 |
| 2026-10-24 | 주간 심화 오답 처리는 월간 규칙 그대로 ((A) → L1 새 항목, (B) → L0 복귀) | 3-4 |
| 2026-10-24 | 수학이 아닌 과목도 변형 (예: 국어 같은 지문 다른 문제). 검증은 G7 독립 풀이 필수, 분류는 2022 성취기준 | 0, 2-1, 6-4 |
| 2026-10-24 | 확인 문제가 문제마다 지문도 돌려줌 | 5-1 |
| 2026-10-24 | 원본 등록 전 확인 문제 정답을 AI 독립 풀이로 확인, 다르면 버림 | 5-1 |
| 2026-10-24 | 찍은 것 같은 결과는 틀린 문제만 보냄 | 5-1 |
| 2026-10-24 | 공부 카드를 끝내면 확인 문제를 권함 | 5-1 |
