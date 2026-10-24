package com.ithopeschool.projectbe.domain.insight;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

// 조건-성과 관계를 계산합니다. (이슈 #38 분석 ③, docs/API.md "조건-성과 관계")
// 성과가 상위 25% 인 날과 하위 25% 인 날을 골라, 두 그룹의 조건(수면·취침 시각·컨디션·걸음)을 비교합니다.
// "잘한 날에는 평소보다 30분 더 잤어" 같은 문장의 재료입니다.
// 상관은 인과가 아닙니다. 원인처럼 보이는 것은 개선안 실험(#52)으로 확인합니다.
//
// 정해 둔 규칙
//   1. 성과 값이 있는 날만 씁니다. (부르는 쪽이 성과가 없는 날을 빼고 넘깁니다)
//   2. 그룹 크기 = 날 수 ÷ 4 (소수점 버림). 단, 날이 2일 이상이면 적어도 1일입니다. 2일 미만이면 비교하지 않습니다.
//   3. 성과가 높은 순서로 세우고, 같으면 최근 날짜가 앞입니다. 앞에서 그룹 크기만큼이 상위, 뒤에서 그만큼이 하위입니다.
//   4. 모든 날의 성과가 같으면 상위와 하위가 구별되지 않아서 비교하지 않습니다.
//   5. 조건 값이 비어 있는 날은 그 조건에서만 뺍니다. ("모름" 이지 "0" 이 아닙니다)
//      어느 한 그룹이라도 그 조건의 값이 하루도 없으면 그 조건은 건너뜁니다. (워치가 없는 학생 등)
//   6. 평균과 차이는 소수 첫째 자리까지 반올림합니다. 차이 = 상위 평균 − 하위 평균 (반올림 전 값으로 뺍니다)
//   7. 두 그룹 중 하나라도 그 조건의 날이 5일 미만이면 가설입니다. (InsightEvidence.isHypothesis)
//
// 예시
//   8일, 순공 [120, 100, 90, 80, 60, 50, 30, 10]  →  그룹 크기 2, 상위 [120, 100] · 하위 [30, 10]
//   상위 두 날 수면 [420, 400], 하위 두 날 수면 [360, 비어 있음]  →  상위 410.0 · 하위 360.0 · 차이 50.0, 가설
//   걸음 데이터가 하루도 없음  →  걸음 조건은 결과에 없음
public final class ConditionPerformance {

  // 하루치 재료입니다. performance 말고는 모두 비어 있을 수 있습니다.
  // date: 날짜 / performance: 그날 성과 (순공 분 또는 달성률)
  // sleepMinutes: 그날 아침까지 실제로 잔 시간(분)
  // bedtimeMinutes: 잠든 시각. 전날 자정부터 몇 분 뒤인지 (23:30 → 1410, 다음 날 01:00 → 1500)
  // condition: 체크인에 적은 컨디션(1 ~ 5) / steps: 걸음 수
  public record Day(
      LocalDate date,
      int performance,
      Integer sleepMinutes,
      Integer bedtimeMinutes,
      Integer condition,
      Integer steps) {}

  // 비교하는 조건들. FE 와 주고받을 때는 value 글자를 씁니다.
  public enum Condition {
    SLEEP_MINUTES("sleepMinutes", Day::sleepMinutes),
    BEDTIME("bedtime", Day::bedtimeMinutes),
    CONDITION("condition", Day::condition),
    STEPS("steps", Day::steps);

    // FE 와 주고받는 글자
    private final String value;

    // 하루치 재료에서 이 조건의 값을 꺼내는 방법
    private final Function<Day, Integer> reader;

    Condition(String value, Function<Day, Integer> reader) {
      this.value = value;
      this.reader = reader;
    }

    public String getValue() {
      return value;
    }
  }

  // 성과 그룹의 하루 (근거). date: 날짜 / performance: 그날 성과
  public record GroupDay(LocalDate date, int performance) {}

  // 조건 하나의 비교 결과입니다.
  // topDates / bottomDates: 그 조건의 값이 있던 날들 (오래된 날부터). 날 수는 이 목록의 길이입니다.
  public record ConditionResult(
      Condition condition,
      double topAverage,
      double bottomAverage,
      double difference,
      List<LocalDate> topDates,
      List<LocalDate> bottomDates,
      boolean hypothesis) {}

  // 전체 결과입니다. 비교할 수 없으면 그룹과 조건이 모두 빈 목록입니다.
  // dayCount: 성과 값이 있던 날 수 / topDays, bottomDays: 상위·하위 그룹의 날들 (성과 높은 순)
  public record Result(
      int dayCount,
      List<GroupDay> topDays,
      List<GroupDay> bottomDays,
      List<ConditionResult> conditions) {}

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private ConditionPerformance() {}

  public static Result of(List<Day> days) {
    int dayCount = days.size();
    // 그룹 크기 (규칙 2)
    int groupSize = dayCount < 2 ? 0 : Math.max(1, dayCount / 4);
    if (groupSize == 0) {
      return new Result(dayCount, List.of(), List.of(), List.of());
    }

    // 성과 높은 순, 같으면 최근 날짜가 앞 (규칙 3)
    List<Day> sorted = new ArrayList<>(days);
    sorted.sort(
        Comparator.comparingInt(Day::performance)
            .reversed()
            .thenComparing(Day::date, Comparator.reverseOrder()));

    // 모든 날의 성과가 같으면 비교하지 않습니다. (규칙 4)
    if (sorted.getFirst().performance() == sorted.getLast().performance()) {
      return new Result(dayCount, List.of(), List.of(), List.of());
    }

    List<Day> top = sorted.subList(0, groupSize);
    List<Day> bottom = sorted.subList(dayCount - groupSize, dayCount);

    List<ConditionResult> conditions = new ArrayList<>();
    for (Condition condition : Condition.values()) {
      List<Day> topWithValue = withValue(top, condition);
      List<Day> bottomWithValue = withValue(bottom, condition);
      // 한 그룹이라도 값이 없으면 건너뜁니다. (규칙 5)
      if (topWithValue.isEmpty() || bottomWithValue.isEmpty()) {
        continue;
      }
      double topAverage = average(topWithValue, condition);
      double bottomAverage = average(bottomWithValue, condition);
      conditions.add(
          new ConditionResult(
              condition,
              round(topAverage),
              round(bottomAverage),
              round(topAverage - bottomAverage),
              datesOf(topWithValue),
              datesOf(bottomWithValue),
              InsightEvidence.isHypothesis(List.of(topWithValue.size(), bottomWithValue.size()))));
    }

    return new Result(dayCount, groupDays(top), groupDays(bottom), List.copyOf(conditions));
  }

  // 그룹에서 이 조건의 값이 있는 날만 고릅니다.
  private static List<Day> withValue(List<Day> group, Condition condition) {
    List<Day> result = new ArrayList<>();
    for (Day day : group) {
      if (condition.reader.apply(day) != null) {
        result.add(day);
      }
    }
    return result;
  }

  // 값이 있는 날들만 넘겨받아 평균을 냅니다. (빈 목록은 넘기지 않습니다)
  private static double average(List<Day> daysWithValue, Condition condition) {
    long sum = 0;
    for (Day day : daysWithValue) {
      sum += condition.reader.apply(day);
    }
    return (double) sum / daysWithValue.size();
  }

  // 소수 첫째 자리까지 반올림 (규칙 6)
  private static double round(double value) {
    return Math.round(value * 10) / 10.0;
  }

  // 근거 날짜들, 오래된 날부터
  private static List<LocalDate> datesOf(List<Day> group) {
    return group.stream().map(Day::date).sorted().toList();
  }

  private static List<GroupDay> groupDays(List<Day> group) {
    return group.stream().map(day -> new GroupDay(day.date(), day.performance())).toList();
  }
}
