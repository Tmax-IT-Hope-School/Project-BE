package com.ithopeschool.projectbe.domain.insight;

import com.ithopeschool.projectbe.domain.checkin.CheckIn;
import com.ithopeschool.projectbe.domain.checkin.CheckInRepository;
import com.ithopeschool.projectbe.domain.dailysignal.DailySignal;
import com.ithopeschool.projectbe.domain.dailysignal.DailySignalRepository;
import com.ithopeschool.projectbe.domain.insight.dto.ConditionPerformanceResponse;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.summary.DaySummary;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 조건-성과 관계 로직입니다. (이슈 #38 분석 ③, docs/API.md "조건-성과 관계")
// 계획·공부 기록·체크인·하루 신호를 꺼내 날마다 한 줄로 묶어 ConditionPerformance 에 넘기기만 합니다.
//
// 기간은 "어제까지 30일" 입니다. (리듬·패턴 #37 과 같음)
//   오늘은 아직 끝나지 않아서, 넣으면 낮에 부를 때마다 오늘이 "성과 하위" 로 잡힙니다.
//
// 날짜 맞추기
//   - 성과: 하루 요약표와 같은 계산(DaySummary). 새벽 4시 기준 날짜입니다.
//   - 수면·취침 시각·걸음: 하루 신호(#18)의 그 날짜 줄. 그날 아침에 깬 잠이 그날 성과와 짝입니다.
//   - 컨디션: 그 날짜의 체크인.
//
// 어떤 날을 쓰나
//   - studyMinutes: 공부 계획이 있거나 공부 기록이 있는 날. 앱을 아예 안 쓴 날을 "0분인 날" 로 세지 않습니다.
//   - achievementRate: 공부 계획이 있던 날 (달성률을 계산할 수 있는 날)
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 내 데이터만 봅니다.
@Service
@RequiredArgsConstructor
public class ConditionPerformanceService {

  // 몇 일을 보는지 (어제 포함)
  public static final int PERIOD_DAYS = 30;

  // 잠든 시각을 한국 시간으로 읽기 위한 시간대
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  // 이 시각(정오)보다 이르면 "자정을 넘겨 잔 것" 으로 보고 24시간을 더합니다.
  private static final int NOON_MINUTES = 12 * 60;

  private static final int MINUTES_PER_DAY = 24 * 60;

  private final CurrentUserProvider currentUserProvider;
  private final StudyPlanRepository studyPlanRepository;
  private final StudySessionRepository studySessionRepository;
  private final CheckInRepository checkInRepository;
  private final DailySignalRepository dailySignalRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public ConditionPerformanceResponse getConditionPerformance(String metricValue) {
    Long userId = currentUserProvider.getCurrentUserId();
    // 글자가 틀리면 DB 를 보기 전에 400 입니다.
    PerformanceMetric metric = PerformanceMetric.from(metricValue);

    LocalDate today = DayRule.dateOf(clock.instant());
    LocalDate to = today.minusDays(1);
    LocalDate from = to.minusDays(PERIOD_DAYS - 1);

    List<DaySummary.DayPlan> dayPlans = new ArrayList<>();
    for (StudyPlan plan :
        studyPlanRepository.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
            userId, from, to)) {
      dayPlans.add(
          new DaySummary.DayPlan(plan.getPlanDate(), plan.getTargetMinutes(), plan.getKind()));
    }

    List<DaySummary.DaySession> daySessions = new ArrayList<>();
    for (StudySession session :
        studySessionRepository.findStartedBetween(
            userId, DayRule.startOf(from), DayRule.startOf(today))) {
      daySessions.add(
          new DaySummary.DaySession(
              session.getStartedAt(),
              session.getEndedAt(),
              session.getPausedMs(),
              session.getPauseCount(),
              session.getKind()));
    }

    // 날짜 → 그날의 체크인 컨디션 (컨디션을 안 고른 날은 넣지 않습니다)
    Map<LocalDate, Integer> conditionByDate = new HashMap<>();
    for (CheckIn checkIn :
        checkInRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(
            userId, from, to)) {
      if (checkIn.getCondition() != null) {
        conditionByDate.put(checkIn.getCheckInDate(), checkIn.getCondition());
      }
    }

    // 날짜 → 그날의 하루 신호 (안 보낸 날은 없습니다)
    Map<LocalDate, DailySignal> signalByDate = new HashMap<>();
    for (DailySignal signal :
        dailySignalRepository.findByUserIdAndSignalDateBetweenOrderBySignalDateAsc(
            userId, from, to)) {
      signalByDate.put(signal.getSignalDate(), signal);
    }

    List<ConditionPerformance.Day> days = new ArrayList<>();
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      Integer performance = performanceOf(metric, DaySummary.of(date, dayPlans, daySessions));
      if (performance == null) {
        continue;
      }
      DailySignal signal = signalByDate.get(date);
      days.add(
          new ConditionPerformance.Day(
              date,
              performance,
              signal == null ? null : signal.getSleepMinutes(),
              signal == null ? null : bedtimeMinutes(signal.getSleptAt()),
              conditionByDate.get(date),
              signal == null ? null : signal.getSteps()));
    }

    return ConditionPerformanceResponse.of(from, to, metric, ConditionPerformance.of(days));
  }

  // 그날의 성과. 쓰지 않는 날이면 null 입니다. (위 "어떤 날을 쓰나")
  static Integer performanceOf(PerformanceMetric metric, DaySummary summary) {
    if (metric == PerformanceMetric.ACHIEVEMENT_RATE) {
      return summary.achievementRate();
    }
    // 공부 계획이 있으면 달성률이 있고, 공부 기록이 있으면 첫 시작 시각이 있습니다.
    boolean used = summary.achievementRate() != null || summary.firstStartAt() != null;
    return used ? summary.studyMinutes() : null;
  }

  // 잠든 시각을 "전날 자정부터 몇 분 뒤" 로 바꿉니다. 자정을 넘겨 자도 평균이 깨지지 않게 하려는 것입니다.
  //   23:30 → 1410  /  다음 날 01:00 → 1500  (01:00 을 60 으로 두면 23:30 과의 평균이 낮 12시가 됩니다)
  // 정오 전에 잠들었으면 자정을 넘긴 것으로 봅니다. 모르면 null 입니다.
  static Integer bedtimeMinutes(Instant sleptAt) {
    if (sleptAt == null) {
      return null;
    }
    LocalTime time = sleptAt.atZone(SEOUL).toLocalTime();
    int minutes = time.getHour() * 60 + time.getMinute();
    return minutes < NOON_MINUTES ? minutes + MINUTES_PER_DAY : minutes;
  }
}
