package com.ithopeschool.projectbe.domain.summary;

import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.checkin.CheckIn;
import com.ithopeschool.projectbe.domain.checkin.CheckInRepository;
import com.ithopeschool.projectbe.domain.dailysignal.DailySignal;
import com.ithopeschool.projectbe.domain.dailysignal.DailySignalRepository;
import com.ithopeschool.projectbe.domain.plan.DayProgress;
import com.ithopeschool.projectbe.domain.plan.DayProgress.PlanItem;
import com.ithopeschool.projectbe.domain.plan.DayProgress.PlayProgress;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.PlanProgress.PlanSession;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.session.StudyTime;
import com.ithopeschool.projectbe.domain.stats.StudyFacts;
import com.ithopeschool.projectbe.domain.stats.StudyFacts.FactPlan;
import com.ithopeschool.projectbe.domain.stats.StudyFacts.FactSession;
import com.ithopeschool.projectbe.domain.summary.DaySummary.DayPlan;
import com.ithopeschool.projectbe.domain.summary.DaySummary.DaySession;
import com.ithopeschool.projectbe.domain.user.User;
import com.ithopeschool.projectbe.domain.user.UserRepository;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 한 사람의 하루 요약(daily_summaries 한 줄)을 만들어 저장합니다. (이슈 #34)
// 숫자 계산은 새로 만들지 않고, FE 와 결과를 맞춰 둔 함수를 불러 씁니다.
//   순공 · 달성률 · 첫 시작 · 평균 길이 · 일시정지  →  DaySummary.of
//   목표가 있는 타이머 · 완주 · 시작 지연 · 멈춘 지점  →  StudyFacts.of (그날 하루를 기간으로)
//   노는 계획 · 노는 실제                           →  DayProgress.playOf
//
// 예약 작업(DailySummaryJob)이 부르는 곳이라 로그인한 사용자가 없습니다.
// userId 는 요청에서 받은 값이 아니라 userIdsOn 이 돌려준 DB 의 사용자 목록입니다.
// 그 사람의 기록만 꺼내도록 모든 조회에 userId 조건을 붙입니다.
@Service
@RequiredArgsConstructor
public class DailySummaryService {

  private final DailySummaryRepository dailySummaryRepository;
  private final UserRepository userRepository;
  private final StudyPlanRepository studyPlanRepository;
  private final StudySessionRepository studySessionRepository;
  private final CheckInRepository checkInRepository;
  private final DailySignalRepository dailySignalRepository;

  // 그날 요약을 만들 사용자 번호들입니다. 번호 순서대로.
  // 그날보다 뒤에 가입한 사람은 뺍니다. 가입 전 날짜에 "순공 0" 줄이 생기면 '공부를 안 한 날' 처럼 보이기 때문입니다.
  @Transactional(readOnly = true)
  public List<Long> userIdsOn(LocalDate date) {
    List<Long> userIds = new ArrayList<>();
    for (User user : userRepository.findAll(Sort.by("id"))) {
      if (!DayRule.dateOf(user.getCreatedAt()).isAfter(date)) {
        userIds.add(user.getId());
      }
    }
    return userIds;
  }

  // 한 사람의 하루 요약을 계산해서 저장합니다. 그날 줄이 이미 있으면 덮어씁니다. (두 번 불러도 결과가 같음)
  // 사람마다 따로 저장해서, 중간에 한 사람이 실패해도 앞사람의 요약은 남습니다.
  @Transactional
  public DailySummary rebuild(Long userId, LocalDate date) {
    // 그날의 계획과, 그날 "시작한" 기록 (새벽 4시 기준, 4시를 넘긴 기록은 시작한 날 것)
    List<StudyPlan> plans = studyPlanRepository.findByUserIdAndPlanDateOrderById(userId, date);
    List<StudySession> sessions =
        studySessionRepository.findStartedBetween(
            userId, DayRule.startOf(date), DayRule.startOf(date.plusDays(1)));

    // 계산 함수에 넘길 모양으로 바꿉니다. 노는 계획 · 노는 기록은 공부 쪽 계산에서 뺍니다. (Project#12)
    List<DayPlan> dayPlans = new ArrayList<>();
    List<FactPlan> factPlans = new ArrayList<>();
    List<PlanItem> playPlans = new ArrayList<>();
    int studyPlanMinutes = 0;
    for (StudyPlan plan : plans) {
      dayPlans.add(new DayPlan(plan.getPlanDate(), plan.getTargetMinutes(), plan.getKind()));
      if (plan.getKind() == PlanKind.PLAY) {
        playPlans.add(new PlanItem(plan.getId(), plan.getTargetMinutes(), plan.isDone()));
      } else {
        factPlans.add(
            new FactPlan(
                plan.getId(), plan.getPlanDate(), plan.getTargetMinutes(), plan.getStartTime()));
        studyPlanMinutes += plan.getTargetMinutes();
      }
    }

    List<DaySession> daySessions = new ArrayList<>();
    List<StudySession> studySessions = new ArrayList<>();
    List<FactSession> factSessions = new ArrayList<>();
    for (StudySession session : sessions) {
      daySessions.add(
          new DaySession(
              session.getStartedAt(),
              session.getEndedAt(),
              session.getPausedMs(),
              session.getPauseCount(),
              session.getKind()));
      if (session.getKind() != PlanKind.PLAY) {
        studySessions.add(session);
        int target = session.getTargetMinutes() == null ? 0 : session.getTargetMinutes();
        factSessions.add(
            new FactSession(
                session.getPlanId(), session.getStartedAt(), studyMillisOf(session), target));
      }
    }

    DaySummary day = DaySummary.of(date, dayPlans, daySessions);
    StudyFacts facts = StudyFacts.of(factPlans, factSessions, date, date);
    PlayProgress play = DayProgress.playOf(playPlans, playSessionsOf(userId, playPlans));

    DailySummary summary =
        dailySummaryRepository
            .findByUserIdAndSummaryDate(userId, date)
            .orElseGet(() -> new DailySummary(userId, date));
    summary.overwrite(
        day,
        studySessions.size(),
        facts,
        play,
        answersOf(userId, date, studyPlanMinutes),
        subjectMinutesOf(studySessions),
        stopReasonsOf(studySessions));
    return dailySummaryRepository.save(summary);
  }

  // 학생이 답한 값들을 모읍니다. 답하지 않은 칸은 null("모름") 입니다.
  private DailySummary.Answers answersOf(Long userId, LocalDate date, int studyPlanMinutes) {
    CheckIn checkIn = checkInRepository.findByUserIdAndCheckInDate(userId, date).orElse(null);
    // 체크인의 미달 원인은 "어제 못 채운 이유" 라서, 그날의 원인은 다음 날 체크인에 있습니다. (MissReasonService 와 같음)
    // 다음 날 체크인이 아직 없으면 비어 있고, 다음 날 밤 예약 작업이 이 날을 다시 만들 때 채워집니다.
    CheckIn nextCheckIn =
        checkInRepository.findByUserIdAndCheckInDate(userId, date.plusDays(1)).orElse(null);
    DailySignal signal = dailySignalRepository.findByUserIdAndSignalDate(userId, date).orElse(null);

    // 예상 순공: 체크인에 적은 값이 있으면 그 값, 없으면 그날 공부 계획 목표 시간의 합 (이슈 #34 바뀐 점)
    int expectedMinutes =
        checkIn != null && checkIn.getExpectedMinutes() != null
            ? checkIn.getExpectedMinutes()
            : studyPlanMinutes;

    return new DailySummary.Answers(
        checkIn == null ? null : checkIn.getCondition(),
        checkIn == null ? null : checkIn.getMood(),
        nextCheckIn == null ? null : nextCheckIn.getMissReason(),
        expectedMinutes,
        checkIn == null ? null : checkIn.getSuggestionKept(),
        signal == null ? null : signal.getSleepMinutes());
  }

  // 노는 계획으로 켠 기록들입니다. 계획 화면의 "실제" 와 같게, 시작한 날이 아니라 계획 번호로 찾습니다.
  private List<PlanSession> playSessionsOf(Long userId, List<PlanItem> playPlans) {
    if (playPlans.isEmpty()) {
      return List.of();
    }
    List<Long> planIds = new ArrayList<>();
    for (PlanItem plan : playPlans) {
      planIds.add(plan.id());
    }
    List<PlanSession> planSessions = new ArrayList<>();
    for (StudySession session : studySessionRepository.findByUserIdAndPlanIdIn(userId, planIds)) {
      planSessions.add(new PlanSession(session.getPlanId(), studyMillisOf(session)));
    }
    return planSessions;
  }

  // 과목별 순공(분)입니다. 과목 없이 공부한 시간은 과목 번호를 비운 줄 하나로 모읍니다.
  // 과목 번호 순서대로, 과목 없는 줄은 맨 뒤입니다. (같은 기록이면 몇 번을 만들어도 순서가 같게)
  //
  // 분은 "쌓아 가며 반올림한 값의 차이" 로 나눕니다. 그래야 줄들의 합이 순공(studyMinutes)과 꼭 같습니다.
  //   과목마다 따로 반올림하면: 30.4분 + 30.4분 → 30 + 30 = 60 인데, 순공은 60.8 → 61 이라 1분이 어긋납니다.
  //   쌓아 가며 반올림하면:     30.4 → 30 / 60.8 → 61 이라 첫 줄 30, 둘째 줄 61 − 30 = 31 → 합 61 ✅
  private static List<DailySummarySubject> subjectMinutesOf(List<StudySession> studySessions) {
    Map<Long, Long> millisBySubject = new HashMap<>();
    for (StudySession session : studySessions) {
      millisBySubject.merge(session.getSubjectId(), studyMillisOf(session), Long::sum);
    }

    List<Long> subjectIds = new ArrayList<>();
    for (Long subjectId : millisBySubject.keySet()) {
      if (subjectId != null) {
        subjectIds.add(subjectId);
      }
    }
    subjectIds.sort(null);
    if (millisBySubject.containsKey(null)) {
      subjectIds.add(null);
    }

    List<DailySummarySubject> subjects = new ArrayList<>();
    long accumulatedMillis = 0;
    int accumulatedMinutes = 0;
    for (Long subjectId : subjectIds) {
      accumulatedMillis += millisBySubject.get(subjectId);
      int roundedSoFar = StudyTime.toMinutes(accumulatedMillis);
      subjects.add(new DailySummarySubject(subjectId, roundedSoFar - accumulatedMinutes));
      accumulatedMinutes = roundedSoFar;
    }
    return subjects;
  }

  // 멈춘 이유마다 몇 번인지 셉니다. 이유를 고르지 않고 멈춘 기록은 세지 않습니다. 순서는 이유 종류의 순서입니다.
  private static List<DailySummaryStopReason> stopReasonsOf(List<StudySession> studySessions) {
    Map<MissReason, Integer> counts = new EnumMap<>(MissReason.class);
    for (StudySession session : studySessions) {
      if (session.getStopReason() != null) {
        counts.merge(session.getStopReason(), 1, Integer::sum);
      }
    }
    List<DailySummaryStopReason> stopReasons = new ArrayList<>();
    for (Map.Entry<MissReason, Integer> entry : counts.entrySet()) {
      stopReasons.add(new DailySummaryStopReason(entry.getKey(), entry.getValue()));
    }
    return stopReasons;
  }

  // 기록 하나의 순공(밀리초) = 끝 − 시작 − 일시정지한 시간 (StudyTime)
  private static long studyMillisOf(StudySession session) {
    return StudyTime.studyMillis(
        session.getStartedAt(), session.getEndedAt(), session.getPausedMs());
  }
}
