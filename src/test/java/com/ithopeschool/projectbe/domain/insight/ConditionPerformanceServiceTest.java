package com.ithopeschool.projectbe.domain.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.checkin.CheckIn;
import com.ithopeschool.projectbe.domain.checkin.CheckInRepository;
import com.ithopeschool.projectbe.domain.checkin.CheckInSource;
import com.ithopeschool.projectbe.domain.dailysignal.DailySignal;
import com.ithopeschool.projectbe.domain.dailysignal.DailySignalRepository;
import com.ithopeschool.projectbe.domain.insight.dto.ConditionPerformanceResponse;
import com.ithopeschool.projectbe.domain.insight.dto.ConditionPerformanceResponse.ConditionItem;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.summary.DaySummary;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Service 단위 테스트: DB(Repository)를 가짜로 바꿔서, 기간과 날짜 맞추기를 제대로 하는지 확인합니다.
// 상위·하위를 고르는 규칙 자체는 ConditionPerformanceTest 가 확인합니다.
class ConditionPerformanceServiceTest {

  private static final Long USER_ID = 7L;
  // 지금은 2026-10-17 오후 6시(한국 시간) → 오늘 10/17, 기간은 9/17 ~ 어제 10/16
  private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
  private static final LocalDate FROM = LocalDate.of(2026, 9, 17);
  private static final LocalDate TO = LocalDate.of(2026, 10, 16);

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final StudyPlanRepository studyPlanRepository = mock(StudyPlanRepository.class);
  private final StudySessionRepository studySessionRepository = mock(StudySessionRepository.class);
  private final CheckInRepository checkInRepository = mock(CheckInRepository.class);
  private final DailySignalRepository dailySignalRepository = mock(DailySignalRepository.class);
  private final ConditionPerformanceService service =
      new ConditionPerformanceService(
          currentUserProvider,
          studyPlanRepository,
          studySessionRepository,
          checkInRepository,
          dailySignalRepository,
          Clock.fixed(NOW, ZoneOffset.UTC));

  private final List<StudyPlan> plans = new ArrayList<>();
  private final List<StudySession> sessions = new ArrayList<>();
  private final List<CheckIn> checkIns = new ArrayList<>();
  private final List<DailySignal> signals = new ArrayList<>();

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(
            studyPlanRepository.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
                any(), any(), any()))
        .willReturn(plans);
    given(studySessionRepository.findStartedBetween(any(), any(), any())).willReturn(sessions);
    given(
            checkInRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(
                any(), any(), any()))
        .willReturn(checkIns);
    given(
            dailySignalRepository.findByUserIdAndSignalDateBetweenOrderBySignalDateAsc(
                any(), any(), any()))
        .willReturn(signals);
  }

  // 10월 day 일 저녁 8시(한국 시간)에 minutes 분 공부한 기록
  private void session(int day, int minutes) {
    Instant start = LocalDate.of(2026, 10, day).atTime(20, 0).toInstant(ZoneOffset.ofHours(9));
    sessions.add(new StudySession(USER_ID, 1L, start, start.plusSeconds(minutes * 60L), 0));
  }

  // 10월 day 일에 targetMinutes 분 공부 계획
  private void plan(int day, int targetMinutes) {
    plans.add(
        new StudyPlan(
            USER_ID, 1L, LocalDate.of(2026, 10, day), "수학", targetMinutes, null, PlanKind.STUDY));
  }

  // 10월 day 일의 하루 신호. 잠든 시각은 한국 시간 (hour:minute)
  private void signal(int day, Integer sleepMinutes, int hour, int minute) {
    LocalDate date = LocalDate.of(2026, 10, day);
    // 자정 전에 잠들었으면 전날 밤입니다.
    LocalDate sleptDate = hour >= 12 ? date.minusDays(1) : date;
    Instant sleptAt = sleptDate.atTime(hour, minute).toInstant(ZoneOffset.ofHours(9));
    signals.add(new DailySignal(USER_ID, date, sleepMinutes, sleptAt, null, null, null));
  }

  // 10월 day 일의 체크인 컨디션
  private void checkIn(int day, Integer condition) {
    checkIns.add(
        new CheckIn(
            USER_ID,
            LocalDate.of(2026, 10, day),
            CheckInSource.MANUAL,
            condition,
            null,
            null,
            null,
            null,
            null,
            null,
            null));
  }

  @Test
  @DisplayName("어제까지 30일치를 내 번호로 꺼낸다")
  void readsLast30DaysOfCurrentUser() {
    ConditionPerformanceResponse response = service.getConditionPerformance("studyMinutes");

    assertThat(response.from()).isEqualTo(FROM);
    assertThat(response.to()).isEqualTo(TO);
    assertThat(response.metric()).isEqualTo("studyMinutes");
    verify(studyPlanRepository)
        .findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(USER_ID, FROM, TO);
    verify(studySessionRepository)
        .findStartedBetween(USER_ID, DayRule.startOf(FROM), DayRule.startOf(TODAY));
    verify(checkInRepository)
        .findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(USER_ID, FROM, TO);
    verify(dailySignalRepository)
        .findByUserIdAndSignalDateBetweenOrderBySignalDateAsc(USER_ID, FROM, TO);
  }

  @Test
  @DisplayName("기록이 없으면 404 가 아니라 빈 비교를 돌려준다")
  void emptyWithoutRecords() {
    ConditionPerformanceResponse response = service.getConditionPerformance("studyMinutes");

    assertThat(response.dayCount()).isZero();
    assertThat(response.topDays()).isEmpty();
    assertThat(response.conditions()).isEmpty();
  }

  @Test
  @DisplayName("같은 날짜의 하루 신호·체크인을 그날 성과와 짝짓고, 자정을 넘긴 취침은 24시간을 더한다")
  void pairsSameDateConditions() {
    // 4일 → 그룹 1일. 상위 10/01(120분), 하위 10/04(10분)
    session(1, 120);
    session(2, 90);
    session(3, 60);
    session(4, 10);
    signal(1, 450, 23, 0); // 23:00 → 1380
    signal(4, 300, 1, 30); // 다음 날 01:30 → 1530
    checkIn(1, 5);
    checkIn(4, 2);

    ConditionPerformanceResponse response = service.getConditionPerformance("studyMinutes");

    assertThat(response.dayCount()).isEqualTo(4);
    assertThat(response.topDays().getFirst().date()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(response.topDays().getFirst().value()).isEqualTo(120);
    assertThat(response.conditions())
        .extracting(ConditionItem::condition)
        .containsExactly("sleepMinutes", "bedtime", "condition");

    ConditionItem bedtime = response.conditions().get(1);
    assertThat(bedtime.topAverage()).isEqualTo(1380.0);
    assertThat(bedtime.bottomAverage()).isEqualTo(1530.0);
    assertThat(bedtime.difference()).isEqualTo(-150.0);
    assertThat(bedtime.topDayCount()).isEqualTo(1);
    assertThat(bedtime.topDates()).containsExactly(LocalDate.of(2026, 10, 1));
    assertThat(bedtime.hypothesis()).isTrue();
  }

  @Test
  @DisplayName("achievementRate 는 공부 계획이 있던 날만 쓴다")
  void achievementRateUsesPlannedDaysOnly() {
    plan(1, 60);
    session(1, 60); // 100%
    plan(2, 60); // 0%
    session(3, 60); // 계획 없음 → 빠짐

    ConditionPerformanceResponse response = service.getConditionPerformance("achievementRate");

    assertThat(response.metric()).isEqualTo("achievementRate");
    assertThat(response.dayCount()).isEqualTo(2);
    assertThat(response.topDays().getFirst().value()).isEqualTo(100);
    assertThat(response.bottomDays().getFirst().value()).isZero();
  }

  @Test
  @DisplayName("studyMinutes 는 계획도 기록도 없는 날을 0분으로 세지 않는다")
  void studyMinutesSkipsUnusedDays() {
    assertThat(
            ConditionPerformanceService.performanceOf(
                PerformanceMetric.STUDY_MINUTES, new DaySummary(0, null, null, 0, 0)))
        .isNull();
    // 계획만 있고 공부를 안 한 날은 0분인 날입니다.
    assertThat(
            ConditionPerformanceService.performanceOf(
                PerformanceMetric.STUDY_MINUTES, new DaySummary(0, 0, null, 0, 0)))
        .isZero();
  }

  @Test
  @DisplayName("취침 시각: 정오 전이면 자정을 넘긴 것으로 보고, 모르면 null")
  void bedtimeMinutes() {
    assertThat(ConditionPerformanceService.bedtimeMinutes(null)).isNull();
    // 한국 22:15 = UTC 13:15
    assertThat(ConditionPerformanceService.bedtimeMinutes(Instant.parse("2026-10-01T13:15:00Z")))
        .isEqualTo(22 * 60 + 15);
    // 한국 02:00 = UTC 전날 17:00
    assertThat(ConditionPerformanceService.bedtimeMinutes(Instant.parse("2026-10-01T17:00:00Z")))
        .isEqualTo(26 * 60);
  }

  @Test
  @DisplayName("모르는 metric 이면 DB 를 보기 전에 400 을 던진다")
  void rejectsUnknownMetric() {
    assertThatThrownBy(() -> service.getConditionPerformance("score"))
        .isInstanceOf(ApiException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
    verifyNoInteractions(studyPlanRepository, dailySignalRepository);
  }
}
