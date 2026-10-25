# 주간 점수 (#412) — 결정 기록과 작업 내용

> 이 문서는 "사용자 점수 계산 로직" 회의 메모에서 시작해 BE API 를 만들기까지의 **결정 · 이유 · 작업 · 남은 일**을 한곳에 모은 기록입니다. (2026-10-24)
> 계산 규칙을 바꾸려면 이 문서와 `WeeklyScore.java` 주석, `docs/API.md` "주간 점수" 를 **함께** 고칩니다.

| 항목 | 위치 |
| --- | --- |
| BE 이슈 | [#412](https://github.com/Tmax-IT-Hope-School/Project-BE/issues/412) `feat: 주간 점수 계산 API (정답률 + 공부 달성률, 본인만 보기)` |
| FE 짝 이슈 | [Project-FE#660](https://github.com/Tmax-IT-Hope-School/Project-FE/issues/660) `feat: 주간 점수 보여주기` |
| 먼저 merge 돼야 하는 PR | [#375](https://github.com/Tmax-IT-Hope-School/Project-BE/pull/375) 퀴즈 결과 저장 · 통계 API (`quiz_results` 테이블) |
| 작업 브랜치 | `feat/412-weekly-score` (#375 브랜치 `feat/quiz-result-stats` 에서 땀, GitHub 에 푸시됨) |
| API 문서 | `docs/API.md` → "주간 점수" |

---

## 1. 시작점 — 회의 메모

> 사용자 점수 - 점수 계산 로직
> 정오답률, 공부시간 ← 필수로 들어가야 하는 분모
> 추가로 고려할 것: 리포트 때 나오는 데이터를 활용할 건지 or 그냥 안 할 건지

"분모"는 나눗셈의 아래쪽이 아니라 **"꼭 들어가는 재료"** 라는 뜻으로 이해하고 진행했습니다.

## 2. 조사 결과 — 저장소에 데이터가 있나 (2026-10-24 기준)

| 재료 | 상태 |
| --- | --- |
| 정오답률 | develop 에는 **없음**. 확인 문제(`POST /api/v1/ai/quiz`)는 채점을 FE 가 기기에서 하고 서버에 저장하지 않음 (`StudyQuizResponse.java` 주석). **PR #375** 가 `quiz_results` 테이블(과목 · 단원 · 날짜 · 맞은 수 · 전체 수 · 문제별 시간)을 추가함 — 아직 OPEN |
| 공부시간 | **있음**. `study_sessions` + `StudyFacts.studyMinutes`, 계획 대비 실제 `StudyFacts.planRate` |
| 리포트 데이터 | 주간 리포트 API(#44)는 **아직 없음**. 리포트에 들어갈 숫자(planRate · 완주율 · 시작 지연)는 `StudyFacts` 에 이미 계산돼 있음 |
| 확인 문제 문제 수 | 한 번에 1~5문제, 기본 3문제 (`StudyQuizRequest`) |

## 3. 결정 사항과 이유

질문을 하나씩 해서 정했습니다. (모두 2026-10-24)

| # | 질문 | 결정 | 이유 |
| --- | --- | --- | --- |
| 1 | 리포트 데이터를 점수에 넣을까? | **이번에는 안 넣음** (정답률 + 공부시간 두 가지만) | ① 재료가 2개면 점수가 왜 바뀌었는지 설명하기 쉬움 ② #44 를 기다리지 않아도 됨 ③ 리포트 숫자는 표본 5개 미만이면 숨기는 규칙이라 사람마다 재료가 달라짐. 나중에 계산식에 항목만 추가하면 됨 |
| 2 | 점수는 누가 보나? | **본인만** | 다른 사람과 비교하면 CLAUDE.md 4장(비교해 깎아내리기 금지)에 걸림. 비교는 **지난주의 나**와만 |
| 3 | 계산 기간은? | **주간** (월~일) | 하루 단위는 퀴즈 문제가 적어 정답률이 크게 튐. 기존 `WeeklyFacts` · 주간 리포트(#44)와 기간이 같음 |
| 4 | 퀴즈를 거의 안 푼 주는? | **점수 대신 안내** ("퀴즈 N문제 더 풀면 이번 주 점수가 나와요") | 매주 같은 계산식이라 비교가 정확함. 앱의 완주율도 같은 방식(표본 적으면 숫자 대신 안내). 0점 처리는 선택 기능이라 억울해서 제외 |
| 5 | 최소 문제 수는? | **10문제** | 기본 3문제 퀴즈를 주 3~4번. 한 문제에 정답률이 10%p 씩만 움직임 |
| 6 | 두 재료의 비율은? | **5 : 5** | "점수 = 두 값의 평균" 한 줄로 설명됨 |
| 7 | 계획이 없는 주는? | **점수 대신 안내** ("이번 주 계획을 세우면 점수가 나와요") | 4번과 같은 규칙. 남은 재료만으로 계산하면 주마다 계산식이 달라짐 |

추가로 정한 것 (설계하면서)

- **공부시간은 분이 아니라 "계획 대비 %"** — 정답률(%)과 단위를 맞춰야 더할 수 있음. 기존 `StudyFacts.planRate` 를 그대로 사용
- **100% 상한** — 타이머만 켜 두고 점수 올리기 방지, 오래 할수록 점수가 오르는 "무리하게 만드는 점수" 방지 (안전 브레이크 · 회복 모드 우선)
- **점수가 낮아도 깎아내리는 문장 금지** — 왜 바뀌었는지만 보여줌
- 확인 문제는 AI 가 만들어 난이도가 들쭉날쭉 → 실력 평가가 아니라 **내 흐름을 보는 참고값**
- **새 테이블 없음** — 부를 때마다 `quiz_results` + 계획 · 타이머 기록으로 계산

## 4. 최종 계산 규칙

```
주간 점수 (월~일, 하루는 새벽 4시 기준, 본인만 봄)

정답률      = 그 주에 맞힌 문제 ÷ 푼 문제 × 100          (과목 상관없이 모두 더함, 소수점 버림)
              푼 문제가 10개 미만이면 null
공부 달성률 = min(계획 대비 실제, 100)                    (StudyFacts.planRate, 노는 계획 · 기록 제외)
              계획 목표 시간 합이 0 이면 null
점수        = (정답률 + 공부 달성률) ÷ 2                  (소수점 버림)
              둘 중 하나라도 null 이면 null — 0 점이 아님

이번 주는 월요일부터 "오늘까지"만 계산 (주중에 바뀜). 지난 주들은 7일 모두.
비교: 지난주 점수와의 차이만 (같으면 0, 어느 한쪽이 null 이면 null)
```

| 예시 | 정답률 | 달성률 | 점수 |
| --- | --- | --- | --- |
| 10문제 중 8개, 계획 대비 75% | 80 | 75 | 77 (77.5 → 버림) |
| 10문제 중 8개, 계획 대비 130% | 80 | 100 | 90 |
| 11문제 중 7개, 계획 대비 80% | 63 | 80 | 71 |
| 3문제 중 2개 | null (7문제 더) | — | null |
| 퀴즈 8/10, 계획 없음 | 80 | null | null (계획 안내) |

## 5. 만든 API

`GET /api/v1/weekly-scores/{weekStart}` — 로그인 필요. `weekStart` 는 그 주의 **월요일**.

```json
{ "weekStart": "2026-10-19", "inProgress": true,
  "score": 77, "accuracy": 80, "planRate": 75,
  "correctCount": 8, "totalCount": 10, "moreQuestionsNeeded": 0, "planNeeded": false,
  "lastWeekScore": 72, "scoreChange": 5 }
```

| 상태 | 언제 | 메시지 |
| --- | --- | --- |
| 400 | 월요일이 아님 | "주의 시작(월요일) 날짜를 보내 주세요" |
| 400 | 아직 오지 않은 주 | "아직 오지 않은 주의 점수는 볼 수 없어요" |
| 400 | 날짜 모양이 틀림 | "보낸 값을 다시 확인해 주세요" |
| 401 | 로그인하지 않음 | "로그인이 필요해요" |

## 6. 바꾼 파일

| 파일 | 무엇 |
| --- | --- |
| `domain/weeklyscore/WeeklyScore.java` | 계산만 하는 record. `MIN_QUESTIONS = 10`, 100 상한, 소수점 버림 |
| `domain/weeklyscore/WeeklyScoreService.java` | 내 퀴즈 결과 · 계획 · 타이머 기록을 읽어 이번 주 · 지난주 점수 계산. `WeeklyFacts.of` 재사용 |
| `domain/weeklyscore/WeeklyScoreController.java` | `GET /api/v1/weekly-scores/{weekStart}`, `@Tag("주간 점수")` |
| `domain/weeklyscore/dto/WeeklyScoreResponse.java` | 응답 DTO, `scoreChange` 계산 |
| `test/.../weeklyscore/WeeklyScoreTest.java` | 계산 규칙 6개 (버림 · 상한 · 9/10문제 경계 · 계획 없음 · 둘 다 없음) |
| `test/.../weeklyscore/WeeklyScoreServiceTest.java` | 4개 (오늘까지 계산 + 노는 기록 제외 + 지난주 비교 · 끝난 주 · 빈 주 · 400) |
| `test/.../weeklyscore/WeeklyScoreControllerTest.java` | 3개 (200 모양 · null 점수 · 날짜 모양 400) |
| `test/.../global/auth/LoginRequiredFlowTest.java` | 로그인 필수 표에 한 줄 추가 |
| `docs/API.md` | "주간 점수" 항목 추가 |
| `docs/WEEKLY-SCORE.md` | 이 문서 |

커밋 (`feat/412-weekly-score`)

- `feat: 정답률과 공부 달성률로 주간 점수 API 추가`
- `docs: 주간 점수 API 문서 추가`
- `docs: 주간 점수 결정 기록 문서 추가` (이 문서)

## 7. 확인한 것

- `./gradlew spotlessCheck build` **통과** (새 테스트 13개 + `LoginRequiredFlowTest` 포함 전체)
- 서버를 켜서 실제 호출 (로컬 H2, 8080 에 다른 서버가 켜져 있어 8081 로 확인)

| 보낸 것 | 온 것 |
| --- | --- |
| 토큰 없이 | 401 |
| 기록 없음 | 200, `score: null`, `moreQuestionsNeeded: 10`, `planNeeded: true` |
| 퀴즈 10문제 중 8개, 계획 없음 | 200, `accuracy: 80`, `score: null`, `planNeeded: true` |
| 계획 60분 추가, 공부 0분 | 200, `planRate: 0`, `score: 40` |
| 화요일 날짜 / 다음 주 / `abc` | 400 (위 표의 메시지) |

아직 하지 않은 것: 학생이 Swagger 에서 직접 눌러 보기, 원격 DB(Neon) 확인(새 테이블이 없어 schema 변경은 없음 — `quiz_results` 는 #375 의 migration).

## 8. 남은 일

- [ ] **#375 merge 기다리기** (팀원 PR, merge 는 사람이 함)
- [ ] #375 merge 후: `feat/412-weekly-score` 를 최신 develop 에 맞춤 (`/sync`) → `develop` 으로 PR (`Closes #412`)
- [ ] FE #660 — 정할 것: 어느 화면에 보여줄지(통계 탭 `/stats`?), 진행 중인 주 표시 여부, 여러 주 그래프(필요하면 BE 에 여러 주 API 추가)
- [ ] 나중에 고려: 주간 리포트(#44) 숫자를 점수 재료로 추가할지

## 9. 이어서 작업하는 법

```bash
git fetch origin
git switch feat/412-weekly-score      # 없으면: git switch -c feat/412-weekly-score origin/feat/412-weekly-score
./gradlew spotlessCheck build         # Windows: gradlew.bat
```

⚠️ 이 브랜치는 #375 브랜치에서 땄습니다. 처음 만들 때 upstream 이 `origin/feat/quiz-result-stats` 로 잡혀서 풀어 두었고, 지금은 `origin/feat/412-weekly-score` 를 따라갑니다. 그냥 `git push` 해도 #375 브랜치로 올라가지 않습니다.
