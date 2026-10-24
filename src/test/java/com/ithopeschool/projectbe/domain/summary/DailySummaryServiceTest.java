package com.ithopeschool.projectbe.domain.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.checkin.CheckIn;
import com.ithopeschool.projectbe.domain.checkin.CheckInSource;
import com.ithopeschool.projectbe.domain.dailysignal.DailySignal;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.session.SessionMode;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.user.User;
import com.ithopeschool.projectbe.global.time.DayRule;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

// 하루 요약표(daily_summaries)를 진짜 DB(H2)에 만들어 보며 이슈 #34 의 완료 조건을 확인합니다.
//   ① 같은 날을 다시 만들면 덮어쓴다  ② 공부를 안 한 날도 순공 0 인 줄이 생긴다
//   ④ 하루의 기준(새벽 4시)을 따른다  + 바뀐 점(일시정지 빼기 · 노는 기록 빼기 · 추가 칸 · 예상 순공)
// ③ "지난 날짜를 다시 만드는 명령" 은 DailySummaryControllerTest · DailySummaryJobTest 가 확인합니다.
@DataJpaTest
@Import(DailySummaryService.class)
class DailySummaryServiceTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  // 요약을 만들 날: 2026-10-20 (화요일)
  private static final LocalDate DAY = LocalDate.of(2026, 10, 20);

  // 과목 번호 (이 테스트는 과목 테이블을 보지 않아서 번호만 씁니다)
  private static final Long MATH = 1L;
  private static final Long ENGLISH = 2L;

  private static final long MINUTE = 60_000;

  @Autowired private DailySummaryService service;
  @Autowired private DailySummaryRepository repository;
  @Autowired private EntityManager entityManager;

  private Long userId;
  private Long otherUserId;

  @BeforeEach
  void createUsers() {
    User user = new User("민수");
    User other = new User("지은");
    entityManager.persist(user);
    entityManager.persist(other);
    userId = user.getId();
    otherUserId = other.getId();
  }

  // 한국 시간 10월 day 일 hour:minute 의 순간
  private static Instant at(int day, int hour, int minute) {
    return LocalDateTime.of(2026, 10, day, hour, minute).atZone(SEOUL).toInstant();
  }

  private StudyPlan plan(Long subjectId, int minutes, LocalTime startTime, PlanKind kind) {
    StudyPlan plan = new StudyPlan(userId, subjectId, DAY, "계획", minutes, startTime, kind);
    entityManager.persist(plan);
    return plan;
  }

  // 공부 기록 하나. 일시정지는 횟수와 시간(분)으로 받습니다.
  private StudySession session(
      Long subjectId,
      Instant startedAt,
      Instant endedAt,
      int pauseCount,
      int pausedMinutes,
      Long planId,
      Integer targetMinutes,
      MissReason stopReason,
      PlanKind kind) {
    return save(
        new StudySession(
            userId,
            subjectId,
            startedAt,
            endedAt,
            pauseCount,
            pausedMinutes * MINUTE,
            planId,
            targetMinutes,
            stopReason,
            kind));
  }

  // 과목 없이 카드로 켠 공부 기록 하나 (공부 기록은 과목이나 카드 중 하나가 꼭 있어야 합니다)
  private StudySession cardSession(Instant startedAt, Instant endedAt, MissReason stopReason) {
    StudySession session =
        new StudySession(
            userId, null, startedAt, endedAt, 0, 0, null, null, stopReason, PlanKind.STUDY);
    session.recordCard(99L, SessionMode.NORMAL, false);
    return save(session);
  }

  private StudySession save(StudySession session) {
    entityManager.persist(session);
    return session;
  }

  private void checkIn(
      LocalDate date,
      Integer condition,
      Integer mood,
      MissReason missReason,
      Integer expectedMinutes,
      Boolean suggestionKept) {
    entityManager.persist(
        new CheckIn(
            userId,
            date,
            CheckInSource.MANUAL,
            condition,
            mood,
            missReason,
            null,
            expectedMinutes,
            null,
            null,
            suggestionKept));
  }

  // 저장한 것을 DB 에서 다시 읽어 옵니다. (메모리에 남은 객체가 아니라 DB 의 값을 보려고)
  private DailySummary rebuildAndReload(Long who, LocalDate date) {
    service.rebuild(who, date);
    entityManager.flush();
    entityManager.clear();
    return repository.findByUserIdAndSummaryDate(who, date).orElseThrow();
  }

  @Test
  @DisplayName("② 공부도 계획도 체크인도 없는 날: 순공 0 인 줄이 생기고, 답하지 않은 칸은 비어 있다")
  void emptyDay() {
    DailySummary summary = rebuildAndReload(userId, DAY);

    assertThat(summary.getSummaryDate()).isEqualTo(DAY);
    // 2026-10-20 은 화요일 → 2 (1 = 월요일 … 7 = 일요일)
    assertThat(summary.getDayOfWeek()).isEqualTo(2);
    // 타이머 · 계획에서 센 숫자는 0
    assertThat(summary.getStudyMinutes()).isZero();
    assertThat(summary.getSessionCount()).isZero();
    assertThat(summary.getAverageSessionMinutes()).isZero();
    assertThat(summary.getPauseCount()).isZero();
    assertThat(summary.getTargetSessionCount()).isZero();
    assertThat(summary.getCompletedSessionCount()).isZero();
    assertThat(summary.getPlayPlannedMinutes()).isZero();
    assertThat(summary.getPlayActualMinutes()).isZero();
    assertThat(summary.getExpectedMinutes()).isZero();
    // 계산할 수 없거나 답하지 않은 칸은 비어 있음 ("0" 이 아니라 "모름")
    assertThat(summary.getAchievementRate()).isNull();
    assertThat(summary.getFirstStartAt()).isNull();
    assertThat(summary.getAverageStartDelayMinutes()).isNull();
    assertThat(summary.getStopProgressPercent()).isNull();
    assertThat(summary.getCondition()).isNull();
    assertThat(summary.getMood()).isNull();
    assertThat(summary.getMissReason()).isNull();
    assertThat(summary.getSuggestionKept()).isNull();
    assertThat(summary.getSleepMinutes()).isNull();
    assertThat(summary.getSubjects()).isEmpty();
    assertThat(summary.getStopReasons()).isEmpty();
  }

  @Test
  @DisplayName("하루치 기록이 모두 있는 날: 22개 칸이 규칙대로 채워진다")
  void fullDay() {
    // 계획: 수학 120분(19:00 시작) · 영어 60분(시간 미정) · 노는 계획 30분 → 공부 계획 합 180분
    StudyPlan mathPlan = plan(MATH, 120, LocalTime.of(19, 0), PlanKind.STUDY);
    StudyPlan englishPlan = plan(ENGLISH, 60, null, PlanKind.STUDY);
    StudyPlan playPlan = plan(null, 30, null, PlanKind.PLAY);

    // 수학 19:10 ~ 20:10, 5분 일시정지 1번 → 순공 55분. 목표 60분을 못 채우고 '피곤함' 으로 멈춤 (91%)
    session(
        MATH,
        at(20, 19, 10),
        at(20, 20, 10),
        1,
        5,
        mathPlan.getId(),
        60,
        MissReason.TIRED,
        PlanKind.STUDY);
    // 노는 기록 20:15 ~ 21:00 (45분) → 순공 · 세션 수에는 안 들어가고 "노는 실제" 에만 들어감
    session(null, at(20, 20, 15), at(20, 21, 0), 0, 0, playPlan.getId(), null, null, PlanKind.PLAY);
    // 영어 21:00 ~ 22:00 → 60분, 목표 60분 완주
    session(
        ENGLISH, at(20, 21, 0), at(20, 22, 0), 0, 0, englishPlan.getId(), 60, null, PlanKind.STUDY);
    // 과목 없이 카드로 22:30 ~ 22:50 → 20분, '피곤함' 으로 멈춤
    cardSession(at(20, 22, 30), at(20, 22, 50), MissReason.TIRED);
    // 다음 날 새벽 02:00 ~ 02:30 수학 → 새벽 4시 전이라 20일 기록 (30분)
    session(MATH, at(21, 2, 0), at(21, 2, 30), 0, 0, null, null, null, PlanKind.STUDY);
    // 20일 새벽 03:00 ~ 03:40 영어 → 새벽 4시 전이라 19일 기록 (20일에는 안 들어감)
    session(ENGLISH, at(20, 3, 0), at(20, 3, 40), 0, 0, null, null, null, PlanKind.STUDY);

    // 체크인: 20일 것은 그날 값, 미달 원인은 21일 체크인의 "어제 못 채운 이유"
    checkIn(DAY, 4, 3, MissReason.SICK, 150, true);
    checkIn(DAY.plusDays(1), null, null, MissReason.TOO_MUCH, null, null);
    entityManager.persist(new DailySignal(userId, DAY, 420, null, null, null, null));

    // 다른 사람의 기록은 섞이면 안 됩니다.
    entityManager.persist(new StudySession(otherUserId, MATH, at(20, 19, 0), at(20, 23, 0), 0));

    DailySummary summary = rebuildAndReload(userId, DAY);

    // 순공 = 55 + 60 + 20 + 30 = 165분 (일시정지 5분 빠짐, 노는 45분 빠짐, 19일 기록 빠짐)
    assertThat(summary.getStudyMinutes()).isEqualTo(165);
    // 달성률 = 165 ÷ 180 = 91.6… → 91
    assertThat(summary.getAchievementRate()).isEqualTo(91);
    assertThat(summary.getFirstStartAt()).isEqualTo(at(20, 19, 10));
    assertThat(summary.getSessionCount()).isEqualTo(4);
    // 평균 = 165 ÷ 4 = 41.25 → 41
    assertThat(summary.getAverageSessionMinutes()).isEqualTo(41);
    assertThat(summary.getPauseCount()).isEqualTo(1);

    assertThat(summary.getCondition()).isEqualTo(4);
    assertThat(summary.getMood()).isEqualTo(3);
    // 20일 체크인의 SICK 은 19일의 원인이라 쓰지 않고, 21일 체크인의 TOO_MUCH 를 씁니다.
    assertThat(summary.getMissReason()).isEqualTo(MissReason.TOO_MUCH);
    // 체크인에 적은 값이 있으면 계획 합(180)이 아니라 그 값
    assertThat(summary.getExpectedMinutes()).isEqualTo(150);
    assertThat(summary.getSuggestionKept()).isTrue();
    assertThat(summary.getSleepMinutes()).isEqualTo(420);

    // 목표가 있는 타이머: 수학 · 영어 2개, 그중 완주 1개(영어)
    assertThat(summary.getTargetSessionCount()).isEqualTo(2);
    assertThat(summary.getCompletedSessionCount()).isEqualTo(1);
    // 시작 지연: 수학 계획 19:00 → 19:10 시작 = 10분 (영어는 시작 시각이 없어서 표본에서 빠짐)
    assertThat(summary.getAverageStartDelayMinutes()).isEqualTo(10);
    // 멈춘 타이머(수학)의 진행률: 55 ÷ 60 = 91.6… → 91
    assertThat(summary.getStopProgressPercent()).isEqualTo(91);

    assertThat(summary.getPlayPlannedMinutes()).isEqualTo(30);
    assertThat(summary.getPlayActualMinutes()).isEqualTo(45);

    // 과목별 순공: 수학 85(55 + 30) · 영어 60 · 과목 없음 20 → 합 165 = 순공
    assertThat(summary.getSubjects())
        .extracting(DailySummarySubject::getSubjectId, DailySummarySubject::getStudyMinutes)
        .containsExactly(tuple(MATH, 85), tuple(ENGLISH, 60), tuple(null, 20));
    // 멈춘 이유: 피곤함 2번 (수학 · 카드)
    assertThat(summary.getStopReasons())
        .extracting(DailySummaryStopReason::getStopReason, DailySummaryStopReason::getStopCount)
        .containsExactly(tuple(MissReason.TIRED, 2));
  }

  @Test
  @DisplayName("예상 순공: 체크인에 적은 값이 없으면 그날 공부 계획 목표 시간의 합 (노는 계획은 빼고)")
  void expectedMinutesFromPlans() {
    plan(MATH, 90, null, PlanKind.STUDY);
    plan(ENGLISH, 40, null, PlanKind.STUDY);
    plan(null, 60, null, PlanKind.PLAY);
    checkIn(DAY, 3, null, null, null, null);

    DailySummary summary = rebuildAndReload(userId, DAY);

    assertThat(summary.getExpectedMinutes()).isEqualTo(130);
    // 계획은 있는데 공부를 안 한 날: 순공 0, 달성률 0% (비어 있지 않음)
    assertThat(summary.getStudyMinutes()).isZero();
    assertThat(summary.getAchievementRate()).isZero();
  }

  @Test
  @DisplayName("① 같은 날을 다시 만들면 새 줄 없이 덮어쓴다: 기록을 고친 뒤 다시 만들면 값이 바뀐다")
  void overwritesSameDay() {
    session(MATH, at(20, 19, 0), at(20, 20, 0), 0, 0, null, 60, MissReason.PHONE, PlanKind.STUDY);
    DailySummary first = rebuildAndReload(userId, DAY);
    assertThat(first.getStudyMinutes()).isEqualTo(60);

    // 같은 기록으로 한 번 더: 줄도 하나, 값도 같음
    DailySummary again = rebuildAndReload(userId, DAY);
    assertThat(again.getId()).isEqualTo(first.getId());
    assertThat(again.getStudyMinutes()).isEqualTo(60);
    assertThat(again.getSubjects()).hasSize(1);
    assertThat(again.getStopReasons()).hasSize(1);

    // 기록을 하나 더한 뒤 다시: 같은 줄이 새 값으로 바뀜 (딸린 줄도 늘거나 겹치지 않음)
    session(ENGLISH, at(20, 21, 0), at(20, 21, 30), 0, 0, null, null, null, PlanKind.STUDY);
    DailySummary changed = rebuildAndReload(userId, DAY);
    assertThat(changed.getId()).isEqualTo(first.getId());
    assertThat(changed.getStudyMinutes()).isEqualTo(90);
    assertThat(changed.getSubjects())
        .extracting(DailySummarySubject::getSubjectId, DailySummarySubject::getStudyMinutes)
        .containsExactly(tuple(MATH, 60), tuple(ENGLISH, 30));
    assertThat(repository.findByUserIdAndSummaryDateBetweenOrderBySummaryDateAsc(userId, DAY, DAY))
        .hasSize(1);
  }

  @Test
  @DisplayName("미달 원인: 다음 날 체크인이 생긴 뒤 다시 만들면 비어 있던 칸이 채워진다")
  void missReasonFilledNextDay() {
    assertThat(rebuildAndReload(userId, DAY).getMissReason()).isNull();

    checkIn(DAY.plusDays(1), null, null, MissReason.PHONE, null, null);

    assertThat(rebuildAndReload(userId, DAY).getMissReason()).isEqualTo(MissReason.PHONE);
  }

  @Test
  @DisplayName("과목별 순공의 합은 순공과 같다: 30.4분 + 30.4분 → 30 + 31 = 61")
  void subjectMinutesAddUpToStudyMinutes() {
    // 30분 24초 = 30.4분
    session(
        MATH,
        at(20, 19, 0),
        at(20, 19, 30).plusSeconds(24),
        0,
        0,
        null,
        null,
        null,
        PlanKind.STUDY);
    session(
        ENGLISH,
        at(20, 20, 0),
        at(20, 20, 30).plusSeconds(24),
        0,
        0,
        null,
        null,
        null,
        PlanKind.STUDY);

    DailySummary summary = rebuildAndReload(userId, DAY);

    assertThat(summary.getStudyMinutes()).isEqualTo(61);
    assertThat(summary.getSubjects())
        .extracting(DailySummarySubject::getStudyMinutes)
        .containsExactly(30, 31);
  }

  @Test
  @DisplayName("요약을 만들 사람: 그날보다 뒤에 가입한 사람은 빠진다 (가입 전 날짜에 '순공 0' 줄이 생기지 않게)")
  void userIdsOnSkipsUsersJoinedLater() {
    // 이 테스트의 사용자는 방금(지금) 가입했습니다.
    LocalDate joinedOn = DayRule.dateOf(Instant.now());

    assertThat(service.userIdsOn(joinedOn)).contains(userId, otherUserId);
    assertThat(service.userIdsOn(joinedOn.minusDays(1))).doesNotContain(userId, otherUserId);
  }
}
