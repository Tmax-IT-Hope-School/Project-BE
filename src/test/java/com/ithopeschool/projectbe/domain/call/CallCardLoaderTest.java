package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.call.dto.CallSessionRequest;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.checkin.CheckIn;
import com.ithopeschool.projectbe.domain.checkin.CheckInRepository;
import com.ithopeschool.projectbe.domain.checkin.CheckInSource;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// 학생 카드를 DB 기록으로 만드는지 확인합니다. 계산 규칙 자체는 CallCardTest 가 확인하고,
// 여기서는 "그 사람의 기록을 맞게 꺼내 넘기는지" 만 봅니다. (DB 는 가짜입니다)
class CallCardLoaderTest {

  private static final Long USER_ID = 7L;
  // 지금은 2026-10-11(일) 오후 3시(한국 시간). 이번 주 월요일은 10-05 입니다.
  private static final Instant NOW = Instant.parse("2026-10-11T06:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);

  private final StudyPlanRepository studyPlanRepository = mock(StudyPlanRepository.class);
  private final StudySessionRepository studySessionRepository = mock(StudySessionRepository.class);
  private final CheckInRepository checkInRepository = mock(CheckInRepository.class);
  private final CallCardLoader loader =
      new CallCardLoader(studyPlanRepository, studySessionRepository, checkInRepository);

  // 어제 오후 3시(한국 시간)에 시작해 minutes 분 한 기록
  private static StudySession yesterdaySession(int minutes, PlanKind kind, Integer targetMinutes) {
    Instant startedAt = Instant.parse("2026-10-10T06:00:00Z");
    return new StudySession(
        USER_ID,
        kind == PlanKind.STUDY ? 1L : null,
        startedAt,
        startedAt.plusSeconds(minutes * 60L),
        0,
        0,
        null,
        targetMinutes,
        null,
        kind);
  }

  private static CheckIn checkIn(LocalDate date, Integer mood, MissReason missReason) {
    return new CheckIn(
        USER_ID, date, CheckInSource.MANUAL, null, mood, missReason, null, null, null, null, null);
  }

  @Test
  void noPlansAndNoSessionsMeansNoCardEvenWithCheckIns() {
    // 체크인만 있으면 "어제 0분" 이 되어 버려서 카드를 만들지 않습니다.
    given(
            checkInRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(
                any(), any(), any()))
        .willReturn(List.of(checkIn(TODAY, 3, MissReason.PHONE)));

    assertThat(loader.load(USER_ID, TODAY, NOW)).isNull();
  }

  @Test
  void buildsTheCardFromMyRecordsOnly() {
    StudyPlan plan = new StudyPlan(USER_ID, 1L, TODAY.minusDays(1), "수학", 120);
    ReflectionTestUtils.setField(plan, "id", 5L);
    StudyPlan play =
        new StudyPlan(USER_ID, null, TODAY.minusDays(1), "게임", 60, null, PlanKind.PLAY);
    given(
            studyPlanRepository.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
                any(), any(), any()))
        .willReturn(List.of(plan, play));
    given(studySessionRepository.findStartedBetween(any(), any(), any()))
        .willReturn(
            List.of(
                yesterdaySession(100, PlanKind.STUDY, null),
                // 노는 기록은 순공에 들어가지 않습니다.
                yesterdaySession(50, PlanKind.PLAY, null)));
    given(
            checkInRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(
                any(), any(), any()))
        .willReturn(
            List.of(
                checkIn(TODAY.minusDays(1), 4, null),
                // 기분이 없는 체크인(통화가 만든 것 등)도 "어제 못 채운 이유" 는 씁니다.
                checkIn(TODAY, null, MissReason.TOO_MUCH)));

    CallSessionRequest.Card card = loader.load(USER_ID, TODAY, NOW);

    assertThat(card.yesterdayStudyMinutes()).isEqualTo(100);
    // 노는 계획(60분)은 빼고 공부 계획만 셉니다.
    assertThat(card.yesterdayPlanMinutes()).isEqualTo(120);
    assertThat(card.yesterdayMissReason()).isEqualTo("tooMuch");
    // 기분이 없는 날은 최근 기분에 넣지 않습니다.
    assertThat(card.recentMoods()).containsExactly(4);
    // 나간 기록 · 집중 모드는 서버에 없어서 모름(null)입니다. (0 이면 "한 번도 안 나갔다" 가 됩니다)
    assertThat(card.yesterdayAwayCount()).isNull();
    assertThat(card.yesterdayAwayPeakHour()).isNull();
    assertThat(card.yesterdayUnlockCount()).isNull();

    // 모든 조회가 로그인한 그 사람 번호로만 갑니다. 계획은 이번 주 월요일(10-05)부터 봅니다.
    verify(studyPlanRepository)
        .findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
            USER_ID, LocalDate.of(2026, 10, 5), TODAY);
    verify(studySessionRepository).findStartedBetween(eq(USER_ID), any(), any());
    verify(checkInRepository)
        .findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(USER_ID, TODAY.minusDays(6), TODAY);
  }

  @Test
  void onMondayThePlansStartFromYesterday() {
    // 월요일이면 이번 주 월요일 = 오늘이라, 어제(일요일) 계획을 위해 어제부터 봅니다.
    Instant mondayNow = Instant.parse("2026-10-12T06:00:00Z");
    LocalDate monday = LocalDate.of(2026, 10, 12);

    loader.load(USER_ID, monday, mondayNow);

    verify(studyPlanRepository)
        .findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
            USER_ID, monday.minusDays(1), monday);
  }

  @Test
  void focusValuesComeFromTheFeCard() {
    CallSessionRequest.Card server =
        new CallSessionRequest.Card(
            100, 120, null, 1, 60, null, 80, null, 5, List.of(3), 2, null, null, null);
    CallSessionRequest.Card fe =
        new CallSessionRequest.Card(
            999, 999, "phone", 9, 999, "19:00", 1, 1, 1, List.of(1), 99, 6, 21, 2);

    CallSessionRequest.Card merged = CallCardLoader.withFocusFrom(server, fe);

    // 숫자는 서버 것 그대로, 나간 횟수 · 시간대 · 집중 모드만 FE 것
    assertThat(merged.yesterdayStudyMinutes()).isEqualTo(100);
    assertThat(merged.streakDays()).isEqualTo(2);
    assertThat(merged.yesterdayAwayCount()).isEqualTo(6);
    assertThat(merged.yesterdayAwayPeakHour()).isEqualTo(21);
    assertThat(merged.yesterdayUnlockCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("'지난 통화 이후' 칸(결산 · 카드 · 조건부 모듈)은 FE 가 기기에서 센 값이라 FE 카드 것을 옮긴다 (#28 · #434)")
  void sinceLastCallComesFromTheFeCard() {
    CallSessionRequest.Card server =
        new CallSessionRequest.Card(
            100, 240, null, 3, 180, "19:00", 64, 55, 10, List.of(3), 2, null, null, null);
    CallSessionRequest.Card fe =
        new CallSessionRequest.Card(
            999,
            999,
            "phone",
            9,
            999,
            "19:00",
            1,
            1,
            1,
            List.of(1),
            99,
            6,
            21,
            2,
            5,
            4,
            80,
            2,
            1,
            "procrastinated",
            3,
            1,
            true,
            "review4");

    CallSessionRequest.Card merged = CallCardLoader.withFocusFrom(server, fe);

    assertThat(merged.yesterdayStudyMinutes()).isEqualTo(100);
    assertThat(merged.hoursSinceLastCall()).isEqualTo(5);
    assertThat(merged.doneCardCount()).isEqualTo(4);
    assertThat(merged.cardRunRate()).isEqualTo(80);
    assertThat(merged.missedCardCount()).isEqualTo(2);
    assertThat(merged.unhandledCardCount()).isEqualTo(1);
    assertThat(merged.mainMissCause()).isEqualTo("procrastinated");
    assertThat(merged.nextCardCount()).isEqualTo(3);
    assertThat(merged.nextAbsoluteCardCount()).isEqualTo(1);
    assertThat(merged.emotionTagsMissing()).isTrue();
    assertThat(merged.callModule()).isEqualTo("review4");
    // FE 카드가 없으면 새 칸은 모두 모름(null)입니다.
    CallSessionRequest.Card serverOnly = CallCardLoader.withFocusFrom(server, null);
    assertThat(serverOnly.doneCardCount()).isNull();
    assertThat(serverOnly.callModule()).isNull();
  }
}
