package com.ithopeschool.projectbe.domain.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.insight.ConditionPerformance.Condition;
import com.ithopeschool.projectbe.domain.insight.ConditionPerformance.ConditionResult;
import com.ithopeschool.projectbe.domain.insight.ConditionPerformance.Day;
import com.ithopeschool.projectbe.domain.insight.ConditionPerformance.Result;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 조건-성과 계산 규칙(ConditionPerformance 맨 위 주석)을 하나씩 확인합니다.
class ConditionPerformanceTest {

  // 10월 dayOfMonth 일
  private static LocalDate date(int dayOfMonth) {
    return LocalDate.of(2026, 10, dayOfMonth);
  }

  // 수면만 있는 하루 (나머지 조건은 모름)
  private static Day sleepDay(int dayOfMonth, int performance, Integer sleepMinutes) {
    return new Day(date(dayOfMonth), performance, sleepMinutes, null, null, null);
  }

  private static ConditionResult find(Result result, Condition condition) {
    return result.conditions().stream()
        .filter(c -> c.condition() == condition)
        .findFirst()
        .orElse(null);
  }

  @Test
  @DisplayName("8일이면 상위 2일·하위 2일을 고르고, 조건 값이 빈 날은 그 조건에서만 뺀다")
  void comparesTopAndBottomQuarter() {
    List<Day> days =
        List.of(
            sleepDay(1, 120, 420),
            sleepDay(2, 100, 400),
            sleepDay(3, 90, 300),
            sleepDay(4, 80, 300),
            sleepDay(5, 60, 300),
            sleepDay(6, 50, 300),
            sleepDay(7, 30, 360),
            sleepDay(8, 10, null));

    Result result = ConditionPerformance.of(days);

    assertThat(result.dayCount()).isEqualTo(8);
    assertThat(result.topDays())
        .extracting(ConditionPerformance.GroupDay::date)
        .containsExactly(date(1), date(2));
    assertThat(result.bottomDays())
        .extracting(ConditionPerformance.GroupDay::date)
        .containsExactly(date(7), date(8));

    ConditionResult sleep = find(result, Condition.SLEEP_MINUTES);
    assertThat(sleep.topAverage()).isEqualTo(410.0);
    assertThat(sleep.bottomAverage()).isEqualTo(360.0);
    assertThat(sleep.difference()).isEqualTo(50.0);
    assertThat(sleep.topDates()).containsExactly(date(1), date(2));
    // 10/08 은 수면을 몰라서 근거에서 빠집니다.
    assertThat(sleep.bottomDates()).containsExactly(date(7));
    assertThat(sleep.hypothesis()).isTrue();
  }

  @Test
  @DisplayName("데이터가 하루도 없는 조건(취침·컨디션·걸음)은 건너뛴다")
  void skipsConditionsWithoutData() {
    List<Day> days = List.of(sleepDay(1, 100, 420), sleepDay(2, 10, 360));

    Result result = ConditionPerformance.of(days);

    assertThat(result.conditions())
        .extracting(ConditionResult::condition)
        .containsExactly(Condition.SLEEP_MINUTES);
  }

  @Test
  @DisplayName("한 그룹에만 값이 있으면 그 조건도 건너뛴다")
  void skipsConditionWhenOneGroupHasNoValue() {
    List<Day> days = List.of(sleepDay(1, 100, 420), sleepDay(2, 10, null));

    assertThat(ConditionPerformance.of(days).conditions()).isEmpty();
  }

  @Test
  @DisplayName("날이 2일 미만이거나, 모든 날의 성과가 같으면 비교하지 않는다")
  void doesNotCompareWithoutDifference() {
    Result one = ConditionPerformance.of(List.of(sleepDay(1, 100, 420)));
    assertThat(one.dayCount()).isEqualTo(1);
    assertThat(one.topDays()).isEmpty();
    assertThat(one.conditions()).isEmpty();

    Result same = ConditionPerformance.of(List.of(sleepDay(1, 0, 420), sleepDay(2, 0, 300)));
    assertThat(same.topDays()).isEmpty();
    assertThat(same.conditions()).isEmpty();
  }

  @Test
  @DisplayName("두 그룹 모두 5일 이상이면 가설이 아니다 (20일 → 그룹 5일)")
  void notHypothesisWithFiveDaysEach() {
    List<Day> days = new ArrayList<>();
    for (int i = 1; i <= 20; i++) {
      // 성과가 높은 날일수록 컨디션도 높게: 1~5일 컨디션 5, 16~20일 컨디션 2
      int condition = i <= 5 ? 5 : 2;
      days.add(new Day(date(i), 200 - i, null, null, condition, 1000 * i));
    }

    Result result = ConditionPerformance.of(days);

    ConditionResult condition = find(result, Condition.CONDITION);
    assertThat(condition.topDates()).hasSize(5);
    assertThat(condition.difference()).isEqualTo(3.0);
    assertThat(condition.hypothesis()).isFalse();

    // 걸음: 상위 1~5일 평균 3000, 하위 16~20일 평균 18000 → 차이는 음수
    ConditionResult steps = find(result, Condition.STEPS);
    assertThat(steps.difference()).isEqualTo(-15000.0);
  }

  @Test
  @DisplayName("성과가 같으면 최근 날짜가 상위 쪽에 온다")
  void tieGoesToRecentDate() {
    List<Day> days =
        List.of(
            sleepDay(1, 50, 400), sleepDay(2, 50, 300), sleepDay(3, 10, 200), sleepDay(4, 10, 500));

    Result result = ConditionPerformance.of(days);

    // 4일 → 그룹 1일. 줄 세우기: 10/02(50), 10/01(50), 10/04(10), 10/03(10)
    // 상위는 맨 앞 10/02, 하위는 맨 뒤 10/03
    assertThat(result.topDays().getFirst().date()).isEqualTo(date(2));
    assertThat(result.bottomDays().getFirst().date()).isEqualTo(date(3));
  }

  @Test
  @DisplayName("평균은 소수 첫째 자리까지 반올림한다")
  void roundsToOneDecimal() {
    // 12일 → 그룹 3일. 상위 컨디션 [5, 4, 4] → 4.333… → 4.3, 하위 [1, 1, 2] → 1.333… → 1.3
    List<Day> days = new ArrayList<>();
    int[] conditions = {5, 4, 4, 3, 3, 3, 3, 3, 3, 1, 1, 2};
    for (int i = 0; i < conditions.length; i++) {
      days.add(new Day(date(i + 1), 100 - i, null, null, conditions[i], null));
    }

    ConditionResult condition = find(ConditionPerformance.of(days), Condition.CONDITION);

    assertThat(condition.topAverage()).isEqualTo(4.3);
    assertThat(condition.bottomAverage()).isEqualTo(1.3);
    assertThat(condition.difference()).isEqualTo(3.0);
  }
}
