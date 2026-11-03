package com.ithopeschool.projectbe.domain.insight.dto;

import com.ithopeschool.projectbe.domain.insight.ConditionPerformance;
import com.ithopeschool.projectbe.domain.insight.PerformanceMetric;
import java.time.LocalDate;
import java.util.List;

// 조건-성과 관계의 응답입니다. (이슈 #38, docs/API.md "조건-성과 관계")
// from, to: 계산한 기간 (둘 다 포함, to 는 어제)
// metric: 무엇을 성과로 봤는지 ("studyMinutes" / "achievementRate")
// dayCount: 기간 안에서 성과 값이 있던 날 수
// topDays, bottomDays: 성과 상위 25% · 하위 25% 의 날들 (성과 높은 순). 비교할 수 없으면 빈 목록
// conditions: 조건마다의 비교. 데이터가 없는 조건은 들어 있지 않습니다
public record ConditionPerformanceResponse(
    LocalDate from,
    LocalDate to,
    String metric,
    int dayCount,
    List<GroupDay> topDays,
    List<GroupDay> bottomDays,
    List<ConditionItem> conditions) {

  // 그룹의 하루. date: 날짜 / value: 그날 성과
  public record GroupDay(LocalDate date, int value) {}

  // 조건 하나의 비교.
  // condition: "sleepMinutes" · "bedtime" · "condition" · "steps"
  // topAverage, bottomAverage: 두 그룹의 평균 / difference: 상위 − 하위
  // topDayCount, bottomDayCount: 그 조건의 값이 있던 날 수 / topDates, bottomDates: 그 날들 (근거)
  // hypothesis: 두 그룹 중 하나라도 5일 미만이면 true
  public record ConditionItem(
      String condition,
      double topAverage,
      double bottomAverage,
      double difference,
      int topDayCount,
      int bottomDayCount,
      List<LocalDate> topDates,
      List<LocalDate> bottomDates,
      boolean hypothesis) {}

  public static ConditionPerformanceResponse of(
      LocalDate from, LocalDate to, PerformanceMetric metric, ConditionPerformance.Result result) {
    List<ConditionItem> conditions =
        result.conditions().stream()
            .map(
                c ->
                    new ConditionItem(
                        c.condition().getValue(),
                        c.topAverage(),
                        c.bottomAverage(),
                        c.difference(),
                        c.topDates().size(),
                        c.bottomDates().size(),
                        c.topDates(),
                        c.bottomDates(),
                        c.hypothesis()))
            .toList();
    return new ConditionPerformanceResponse(
        from,
        to,
        metric.getValue(),
        result.dayCount(),
        toGroupDays(result.topDays()),
        toGroupDays(result.bottomDays()),
        conditions);
  }

  private static List<GroupDay> toGroupDays(List<ConditionPerformance.GroupDay> days) {
    return days.stream().map(day -> new GroupDay(day.date(), day.performance())).toList();
  }
}
