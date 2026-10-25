package com.ithopeschool.projectbe.domain.weeklyscore.dto;

import com.ithopeschool.projectbe.domain.weeklyscore.WeeklyScore;
import java.time.LocalDate;

// 주간 점수 응답입니다. (이슈 #412)
//   { "weekStart": "2026-10-19", "inProgress": true,
//     "score": 77, "accuracy": 80, "planRate": 75,
//     "correctCount": 8, "totalCount": 10, "moreQuestionsNeeded": 0, "planNeeded": false,
//     "lastWeekScore": 72, "scoreChange": 5 }
// - inProgress: 이번 주라서 월요일부터 "오늘까지" 만 계산했으면 true. 주중에 점수가 바뀝니다.
// - score 가 null 이면 점수 대신 안내를 보여줍니다. (0 점이 아닙니다)
//     moreQuestionsNeeded > 0 → "퀴즈 N문제 더 풀면 이번 주 점수가 나와요"
//     planNeeded 가 true      → "이번 주 계획을 세우면 점수가 나와요"
// - lastWeekScore: 그 전 주(7일 모두)의 점수. 계산할 수 없으면 null
// - scoreChange: score − lastWeekScore. 둘 중 하나라도 null 이면 null 이고, 같으면 0 입니다.
public record WeeklyScoreResponse(
    LocalDate weekStart,
    boolean inProgress,
    Integer score,
    Integer accuracy,
    Integer planRate,
    int correctCount,
    int totalCount,
    int moreQuestionsNeeded,
    boolean planNeeded,
    Integer lastWeekScore,
    Integer scoreChange) {

  public static WeeklyScoreResponse of(
      LocalDate weekStart, boolean inProgress, WeeklyScore thisWeek, WeeklyScore lastWeek) {
    Integer scoreChange = null;
    if (thisWeek.score() != null && lastWeek.score() != null) {
      scoreChange = thisWeek.score() - lastWeek.score();
    }
    return new WeeklyScoreResponse(
        weekStart,
        inProgress,
        thisWeek.score(),
        thisWeek.accuracy(),
        thisWeek.planRate(),
        thisWeek.correctCount(),
        thisWeek.totalCount(),
        thisWeek.moreQuestionsNeeded(),
        thisWeek.planNeeded(),
        lastWeek.score(),
        scoreChange);
  }
}
