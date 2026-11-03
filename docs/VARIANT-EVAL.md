# 변형 생성 품질 평가 (VAR-10)

AI 가 만든 변형 문제가 학생에게 내도 될 만한지 **숫자로** 판단하는 방법입니다. (docs/VARIANT-SPEC.md 10장)
**이 기준을 통과하기 전에는 실제 학생에게 출제하지 않습니다.** (팀 합의 필요)

## 1. 돌리는 법 (사람이 합니다 — 실제 AI 를 불러서 돈이 듭니다)

평소 `./gradlew build` 에서는 건너뜁니다. 키는 파일 · 대화 · PR 에 붙여 넣지 않고 환경변수로만 넣습니다.

```bash
LLM_LIVE_TESTS=1 OPENAI_API_KEY=키 OPENAI_MODEL=모델이름 ./gradlew test --tests '*VariantGenerationEvalTest'
```

| 환경변수 | 기본값 | 뜻 |
| --- | --- | --- |
| `EVAL_LIMIT` | 30 | 시드 원본 몇 문항까지 |
| `EVAL_LEVELS` | `-1,0,1,2` | 평가할 레벨 |
| `EVAL_N` | 3 | 원본 · 레벨마다 몇 번 만들지 |
| `EVAL_G7` | false | true 면 독립 풀이 검사(G7)를 켜고 돌림 |

결과는 `build/variant-eval/eval_날짜_시각.csv` 와 `eval_날짜_시각_summary.md` 입니다. (시드 30 × 레벨 4 × 3번 = 360번, AI 호출은 그보다 많습니다)

## 2. 사람 검수

CSV 의 마지막 세 칸은 비어 있습니다. **무작위로 20% 이상** 골라 채웁니다.

| 칸 | 채우는 법 |
| --- | --- |
| `human_answer_correct_YN` | 문제를 **직접 손으로 풀어서** 정답과 같으면 Y |
| `human_level_fit_YN` | VARIANT-SPEC 2장의 레벨 규칙(L-1 비계 · L0 수치만 · L1 조건 · L2 심화)에 맞으면 Y |
| `human_naturalness_1to5` | 문장이 교과서 · 수능처럼 자연스러운지 1 ~ 5 |

## 3. 통과 기준

| 지표 | 기준 |
| --- | --- |
| 3번 이내 최종 성공률 (요약의 "전체 최종 성공률") | 85% 이상 |
| 사람 검수 정답 정확 | **100%** |
| 사람 검수 레벨 적합 | 80% 이상 |
| 분류기 단원 정확도 / 유형 정확도 | 95% / 80% 이상 |

미달이면 `src/main/resources/prompts/variant/variant_v3.md` 를 새로 만들고(기존 파일은 고치지 않음) `VariantPrompts.VARIANT_VERSION` 을 바꾼 뒤 다시 평가합니다.
G7 을 켠 결과와 끈 결과를 비교해서 `app.variant.enable-independent-solve-check` 기본값을 정합니다.

## 4. 결과 기록

| 날짜 | 프롬프트 | 모델 | G7 | 성공률 | 정답 정확 | 레벨 적합 | 분류 (단원/유형) | 돌린 사람 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| (아직 돌리지 않음) | variant_v2 | | | | | | | |

> 2026-10-24: 평가 도구가 제대로 도는지는 가짜 AI 로만 확인했습니다(`VariantEvalRunnerTest`). **실제 AI 로는 아직 한 번도 돌리지 않았습니다.**
