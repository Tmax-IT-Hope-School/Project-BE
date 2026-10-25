package com.ithopeschool.projectbe.domain.weeklyscore;

import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.quizresult.QuizResult;
import com.ithopeschool.projectbe.domain.quizresult.QuizResultRepository;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.session.StudyTime;
import com.ithopeschool.projectbe.domain.stats.StudyFacts;
import com.ithopeschool.projectbe.domain.stats.StudyFacts.FactValue;
import com.ithopeschool.projectbe.domain.stats.WeeklyFacts;
import com.ithopeschool.projectbe.domain.weeklyscore.dto.WeeklyScoreResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// "주간 점수" 로직입니다. 퀴즈 결과와 계획 · 타이머 기록을 읽어서 WeeklyScore 로 계산합니다. (이슈 #412)
// 점수를 저장하지 않고, 부를 때마다 기록으로 계산합니다. (새 테이블 없음)
//
// 정해 둔 규칙
//   1. 한 주는 월요일 ~ 일요일, 하루는 새벽 4시 기준입니다. (WeeklyFacts.mondayOf, DayRule)
//   2. 이번 주는 월요일부터 "오늘까지" 만, 지난 주들은 7일을 모두 계산합니다. (WeeklyFacts 와 같음)
//   3. 정답률은 그 주에 푼 퀴즈를 과목과 상관없이 모두 더해서 계산합니다.
//   4. 공부 달성률은 WeeklyFacts 가 계산한 "계획 대비 실제" 를 씁니다. 노는 계획과 노는 기록은 뺍니다.
//   5. 비교하는 지난주 점수도 같은 규칙으로 계산합니다.
//
// currentUserProvider.getCurrentUserId() 로 얻은 내 번호로만 읽습니다. 로그인하지 않으면 401 입니다.
@Service
@RequiredArgsConstructor
public class WeeklyScoreService {

  private static final int DAYS_PER_WEEK = 7;

  private final CurrentUserProvider currentUserProvider;
  private final QuizResultRepository quizResultRepository;
  private final StudyPlanRepository studyPlanRepository;
  private final StudySessionRepository studySessionRepository;
  // "오늘" 이 언제인지 시계에게 묻습니다. 테스트에서 시각을 고정할 수 있습니다.
  private final Clock clock;

  // weekStart(월요일)부터 한 주의 점수와 그 전 주 점수를 계산합니다.
  // 검사: 월요일이 아니면 400, 아직 오지 않은 주면 400
  @Transactional(readOnly = true)
  public WeeklyScoreResponse getWeeklyScore(LocalDate weekStart) {
    Long userId = currentUserProvider.getCurrentUserId();
    if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "주의 시작(월요일) 날짜를 보내 주세요");
    }
    LocalDate today = DayRule.dateOf(clock.instant());
    if (weekStart.isAfter(today)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "아직 오지 않은 주의 점수는 볼 수 없어요");
    }

    LocalDate lastMonday = weekStart.minusDays(DAYS_PER_WEEK);
    LocalDate sunday = weekStart.plusDays(DAYS_PER_WEEK - 1);
    // 이번 주면 오늘까지, 지난 주면 일요일까지 봅니다. (규칙 2)
    LocalDate end = today.isBefore(sunday) ? today : sunday;

    // 그 전 주 월요일부터 끝 날짜까지 한 번에 읽고, 주마다 나눠서 계산합니다.
    WeeklyFacts facts =
        WeeklyFacts.of(
            plansOf(userId, lastMonday, end),
            sessionsOf(userId, lastMonday, end),
            weekStart,
            today);
    List<QuizResult> quizzes =
        quizResultRepository.findByUserIdAndQuizDateBetweenOrderByQuizDateAscIdAsc(
            userId, lastMonday, end);

    WeeklyScore thisWeek = scoreOf(quizzes, weekStart, end, facts.thisWeek().planRate());
    WeeklyScore lastWeek =
        scoreOf(quizzes, lastMonday, weekStart.minusDays(1), facts.lastWeek().planRate());
    return WeeklyScoreResponse.of(weekStart, end.isBefore(sunday), thisWeek, lastWeek);
  }

  // from ~ to (두 날짜 모두 포함) 에 푼 퀴즈를 모두 더해서 점수를 계산합니다. (규칙 3)
  private static WeeklyScore scoreOf(
      List<QuizResult> quizzes, LocalDate from, LocalDate to, FactValue planRate) {
    int correctCount = 0;
    int totalCount = 0;
    for (QuizResult quiz : quizzes) {
      if (!quiz.getQuizDate().isBefore(from) && !quiz.getQuizDate().isAfter(to)) {
        correctCount += quiz.getCorrectCount();
        totalCount += quiz.getTotalCount();
      }
    }
    return WeeklyScore.of(correctCount, totalCount, planRate);
  }

  // from ~ to 의 공부 계획. 노는 계획은 뺍니다. (StudyFacts 와 같은 규칙)
  private List<StudyFacts.FactPlan> plansOf(Long userId, LocalDate from, LocalDate to) {
    List<StudyFacts.FactPlan> plans = new ArrayList<>();
    for (StudyPlan plan :
        studyPlanRepository.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
            userId, from, to)) {
      if (plan.getKind() == PlanKind.STUDY) {
        plans.add(
            new StudyFacts.FactPlan(
                plan.getId(), plan.getPlanDate(), plan.getTargetMinutes(), plan.getStartTime()));
      }
    }
    return plans;
  }

  // from ~ to 에 시작한 공부 기록. 노는 기록은 뺍니다. (StudyFacts 와 같은 규칙)
  private List<StudyFacts.FactSession> sessionsOf(Long userId, LocalDate from, LocalDate to) {
    List<StudyFacts.FactSession> sessions = new ArrayList<>();
    for (StudySession session :
        studySessionRepository.findStartedBetween(
            userId, DayRule.startOf(from), DayRule.startOf(to.plusDays(1)))) {
      if (session.getKind() != PlanKind.STUDY) {
        continue;
      }
      long studyMillis =
          StudyTime.studyMillis(
              session.getStartedAt(), session.getEndedAt(), session.getPausedMs());
      int targetMinutes = session.getTargetMinutes() == null ? 0 : session.getTargetMinutes();
      sessions.add(
          new StudyFacts.FactSession(
              session.getPlanId(), session.getStartedAt(), studyMillis, targetMinutes));
    }
    return sessions;
  }
}
