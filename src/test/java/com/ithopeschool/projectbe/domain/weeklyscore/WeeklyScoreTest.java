package com.ithopeschool.projectbe.domain.weeklyscore;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.stats.StudyFacts.FactValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 주간 점수 계산 규칙(정답률 · 공부 달성률 · 점수)을 확인합니다. DB 없이 숫자만 넣습니다. (이슈 #412)
class WeeklyScoreTest {

  // 계획 대비 실제 value% (표본 수는 계산에 쓰지 않아서 아무 값이나 넣습니다)
  private static FactValue planRate(int value) {
    return new FactValue(value, 3);
  }

  @Test
  @DisplayName("10문제 중 8개 · 계획 대비 75% 면 정답률 80, 달성률 75, 점수 77 (소수점 버림)")
  void score() {
    WeeklyScore score = WeeklyScore.of(8, 10, planRate(75));

    assertThat(score.accuracy()).isEqualTo(80);
    assertThat(score.planRate()).isEqualTo(75);
    assertThat(score.score()).isEqualTo(77);
    assertThat(score.moreQuestionsNeeded()).isZero();
    assertThat(score.planNeeded()).isFalse();
  }

  @Test
  @DisplayName("정답률도 소수점을 버린다 (11문제 중 7개 = 63.6% → 63)")
  void accuracyFloor() {
    WeeklyScore score = WeeklyScore.of(7, 11, planRate(80));

    assertThat(score.accuracy()).isEqualTo(63);
    assertThat(score.score()).isEqualTo(71);
  }

  @Test
  @DisplayName("계획보다 더 해도 달성률은 100 까지만 센다")
  void planRateCap() {
    WeeklyScore score = WeeklyScore.of(8, 10, planRate(130));

    assertThat(score.planRate()).isEqualTo(100);
    assertThat(score.score()).isEqualTo(90);
  }

  @Test
  @DisplayName("9문제면 정답률 · 점수는 null 이고 1문제 더 필요하다. 10문제면 점수가 나온다")
  void minQuestions() {
    WeeklyScore nine = WeeklyScore.of(9, 9, planRate(80));
    WeeklyScore ten = WeeklyScore.of(9, 10, planRate(80));

    assertThat(nine.accuracy()).isNull();
    assertThat(nine.score()).isNull();
    assertThat(nine.moreQuestionsNeeded()).isEqualTo(1);
    assertThat(nine.correctCount()).isEqualTo(9);
    assertThat(ten.score()).isEqualTo(85);
  }

  @Test
  @DisplayName("계획이 없으면 달성률 · 점수는 null 이고 planNeeded 다 (0 점이 아니다)")
  void noPlan() {
    WeeklyScore score = WeeklyScore.of(8, 10, FactValue.UNKNOWN);

    assertThat(score.accuracy()).isEqualTo(80);
    assertThat(score.planRate()).isNull();
    assertThat(score.planNeeded()).isTrue();
    assertThat(score.score()).isNull();
  }

  @Test
  @DisplayName("퀴즈를 하나도 안 풀고 계획도 없으면 두 안내가 모두 필요하다")
  void nothing() {
    WeeklyScore score = WeeklyScore.of(0, 0, FactValue.UNKNOWN);

    assertThat(score.score()).isNull();
    assertThat(score.moreQuestionsNeeded()).isEqualTo(WeeklyScore.MIN_QUESTIONS);
    assertThat(score.planNeeded()).isTrue();
  }
}
