package com.ithopeschool.projectbe.domain.weeklyscore;

import com.ithopeschool.projectbe.domain.stats.StudyFacts.FactValue;

// 한 주의 점수를 계산합니다. 본인만 보는 점수이고, 다른 사람과 비교하지 않습니다. (이슈 #412)
// 값만 계산하는 도구라서, DB 는 WeeklyScoreService 가 읽어서 숫자만 넘겨줍니다.
//
// 정해 둔 규칙 (2026-10-24 결정)
//   1. 정답률 = 그 주에 맞힌 문제 ÷ 푼 문제 × 100. 소수점은 버립니다.
//      푼 문제가 10개(MIN_QUESTIONS) 미만이면 정답률은 null 입니다.
//      확인 문제는 한 번에 1~5문제라, 문제가 적으면 정답률이 0 · 33 · 67 · 100% 로 크게 튀기 때문입니다.
//   2. 공부 달성률 = 계획 대비 실제(StudyFacts.planRate)를 100 에서 자른 값입니다.
//      타이머를 켜 두기만 해서 점수를 올리거나, 무리할수록 점수가 오르면 안 되기 때문입니다.
//      계획 목표 시간의 합이 0 이면(계획이 없으면) null 입니다.
//   3. 점수 = (정답률 + 공부 달성률) ÷ 2. 소수점은 버립니다.
//      둘 중 하나라도 null 이면 점수도 null 입니다. 0 점으로 계산하지 않고, 남은 하나로만 계산하지도 않습니다.
//      매주 같은 계산식이어야 지난주와 비교할 수 있기 때문입니다.
//
// 예시
//   10문제 중 8개 맞힘, 계획 대비 실제 75%    →  정답률 80, 달성률 75, 점수 77   ((80 + 75) ÷ 2 = 77.5 → 77)
//   10문제 중 8개 맞힘, 계획 대비 실제 130%   →  정답률 80, 달성률 100, 점수 90
//   3문제 중 2개 맞힘                         →  정답률 null, 7문제 더 필요, 점수 null
//
// accuracy: 정답률(%) / planRate: 공부 달성률(%, 100 까지)
// correctCount · totalCount: 그 주에 맞힌 문제 수 · 푼 문제 수
// moreQuestionsNeeded: 점수가 나오려면 더 풀어야 하는 문제 수. 충분하면 0
// planNeeded: 계획이 없어서 공부 달성률을 계산할 수 없으면 true
public record WeeklyScore(
    Integer score,
    Integer accuracy,
    Integer planRate,
    int correctCount,
    int totalCount,
    int moreQuestionsNeeded,
    boolean planNeeded) {

  // 정답률을 계산하려면 한 주에 풀어야 하는 최소 문제 수입니다.
  public static final int MIN_QUESTIONS = 10;

  // 공부 달성률의 최대값입니다.
  private static final int MAX_PLAN_RATE = 100;

  // 한 주의 퀴즈 결과(맞힌 수 · 푼 수)와 계획 대비 실제로 점수를 계산합니다.
  public static WeeklyScore of(int correctCount, int totalCount, FactValue planRate) {
    // 1) 정답률 (규칙 1). 둘 다 0 이상의 정수라서, 정수 나눗셈이 곧 소수점 버림입니다.
    Integer accuracy = null;
    if (totalCount >= MIN_QUESTIONS) {
      accuracy = correctCount * 100 / totalCount;
    }
    int moreQuestionsNeeded = Math.max(0, MIN_QUESTIONS - totalCount);

    // 2) 공부 달성률 (규칙 2)
    Integer cappedPlanRate = null;
    if (planRate.value() != null) {
      cappedPlanRate = Math.min(planRate.value(), MAX_PLAN_RATE);
    }

    // 3) 점수 (규칙 3)
    Integer score = null;
    if (accuracy != null && cappedPlanRate != null) {
      score = (accuracy + cappedPlanRate) / 2;
    }

    return new WeeklyScore(
        score,
        accuracy,
        cappedPlanRate,
        correctCount,
        totalCount,
        moreQuestionsNeeded,
        cappedPlanRate == null);
  }
}
