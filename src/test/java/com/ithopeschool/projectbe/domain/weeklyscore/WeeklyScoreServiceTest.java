package com.ithopeschool.projectbe.domain.weeklyscore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.quizresult.QuizResult;
import com.ithopeschool.projectbe.domain.quizresult.QuizResultRepository;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.weeklyscore.dto.WeeklyScoreResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 주간 점수의 기간 나누기 · 노는 기록 빼기 · 지난주 비교 · 입력 검사를 확인합니다. DB 는 가짜로 바꿉니다. (이슈 #412)
class WeeklyScoreServiceTest {

  private static final Long ME = 7L;
  // 지금 = 2026-10-24(토) 12:00 (한국 시간) → 이번 주는 10-19(월) ~ 오늘
  private static final Instant NOW = Instant.parse("2026-10-24T03:00:00Z");
  private static final LocalDate THIS_MONDAY = LocalDate.of(2026, 10, 19);
  private static final LocalDate LAST_MONDAY = LocalDate.of(2026, 10, 12);
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);

  private QuizResultRepository quizResultRepository;
  private StudyPlanRepository studyPlanRepository;
  private StudySessionRepository studySessionRepository;
  private WeeklyScoreService service;

  @BeforeEach
  void setUp() {
    CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    quizResultRepository = mock(QuizResultRepository.class);
    studyPlanRepository = mock(StudyPlanRepository.class);
    studySessionRepository = mock(StudySessionRepository.class);
    service =
        new WeeklyScoreService(
            currentUserProvider,
            quizResultRepository,
            studyPlanRepository,
            studySessionRepository,
            Clock.fixed(NOW, ZoneOffset.UTC));
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
  }

  private static QuizResult quiz(LocalDate date, int correct, int total) {
    return new QuizResult(ME, 3L, "함수", date, correct, total, Collections.nCopies(total, 30));
  }

  private static StudyPlan plan(LocalDate date, int minutes, PlanKind kind) {
    return new StudyPlan(ME, null, date, "계획", minutes, null, kind);
  }

  // 그 날 정오(한국 시간)에 시작해서 minutes 분 동안 한 기록
  private static StudySession session(LocalDate date, int minutes, PlanKind kind) {
    Instant start = date.atTime(3, 0).toInstant(ZoneOffset.UTC);
    return new StudySession(
        ME, null, start, start.plusSeconds(minutes * 60L), 0, 0, null, null, null, kind);
  }

  private static void assertRejected(Runnable action, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  @Test
  @DisplayName("이번 주는 오늘까지 계산하고, 노는 계획 · 기록은 빼고, 지난주 점수와 비교한다")
  void thisWeek() {
    // 이번 주: 퀴즈 8/10 → 정답률 80 / 공부 계획 150분 중 120분 → 달성률 80 → 점수 80
    // 지난주: 퀴즈 5/10 → 정답률 50 / 계획 60분 중 60분 → 달성률 100 → 점수 75
    given(
            quizResultRepository.findByUserIdAndQuizDateBetweenOrderByQuizDateAscIdAsc(
                ME, LAST_MONDAY, TODAY))
        .willReturn(
            List.of(
                quiz(LAST_MONDAY.plusDays(2), 5, 10),
                quiz(THIS_MONDAY.plusDays(1), 5, 6),
                quiz(THIS_MONDAY.plusDays(4), 3, 4)));
    given(
            studyPlanRepository.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
                ME, LAST_MONDAY, TODAY))
        .willReturn(
            List.of(
                plan(LAST_MONDAY, 60, PlanKind.STUDY),
                plan(THIS_MONDAY.plusDays(1), 60, PlanKind.STUDY),
                plan(THIS_MONDAY.plusDays(2), 600, PlanKind.PLAY),
                plan(THIS_MONDAY.plusDays(3), 90, PlanKind.STUDY)));
    given(studySessionRepository.findStartedBetween(eq(ME), any(), any()))
        .willReturn(
            List.of(
                session(LAST_MONDAY, 60, PlanKind.STUDY),
                session(THIS_MONDAY.plusDays(1), 60, PlanKind.STUDY),
                session(THIS_MONDAY.plusDays(2), 600, PlanKind.PLAY),
                session(THIS_MONDAY.plusDays(3), 60, PlanKind.STUDY)));

    WeeklyScoreResponse response = service.getWeeklyScore(THIS_MONDAY);

    assertThat(response.inProgress()).isTrue();
    assertThat(response.correctCount()).isEqualTo(8);
    assertThat(response.totalCount()).isEqualTo(10);
    assertThat(response.accuracy()).isEqualTo(80);
    assertThat(response.planRate()).isEqualTo(80);
    assertThat(response.score()).isEqualTo(80);
    assertThat(response.lastWeekScore()).isEqualTo(75);
    assertThat(response.scoreChange()).isEqualTo(5);
  }

  @Test
  @DisplayName("끝난 주는 일요일까지 계산하고, 그 전 주 점수가 없으면 차이는 null 이다")
  void endedWeekWithoutLastWeek() {
    given(
            quizResultRepository.findByUserIdAndQuizDateBetweenOrderByQuizDateAscIdAsc(
                ME, LAST_MONDAY.minusDays(7), LAST_MONDAY.plusDays(6)))
        .willReturn(List.of(quiz(LAST_MONDAY.plusDays(6), 6, 10)));
    given(
            studyPlanRepository.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
                ME, LAST_MONDAY.minusDays(7), LAST_MONDAY.plusDays(6)))
        .willReturn(List.of(plan(LAST_MONDAY, 100, PlanKind.STUDY)));
    given(studySessionRepository.findStartedBetween(eq(ME), any(), any()))
        .willReturn(List.of(session(LAST_MONDAY, 50, PlanKind.STUDY)));

    WeeklyScoreResponse response = service.getWeeklyScore(LAST_MONDAY);

    assertThat(response.inProgress()).isFalse();
    assertThat(response.score()).isEqualTo(55);
    assertThat(response.lastWeekScore()).isNull();
    assertThat(response.scoreChange()).isNull();
  }

  @Test
  @DisplayName("퀴즈도 계획도 없으면 점수는 null 이고 두 안내가 모두 필요하다")
  void empty() {
    WeeklyScoreResponse response = service.getWeeklyScore(THIS_MONDAY);

    assertThat(response.score()).isNull();
    assertThat(response.moreQuestionsNeeded()).isEqualTo(10);
    assertThat(response.planNeeded()).isTrue();
    assertThat(response.lastWeekScore()).isNull();
  }

  @Test
  @DisplayName("월요일이 아니거나 아직 오지 않은 주면 400 이다")
  void invalidWeek() {
    assertRejected(() -> service.getWeeklyScore(THIS_MONDAY.plusDays(1)), "주의 시작(월요일) 날짜를 보내 주세요");
    assertRejected(
        () -> service.getWeeklyScore(THIS_MONDAY.plusDays(7)), "아직 오지 않은 주의 점수는 볼 수 없어요");
  }
}
