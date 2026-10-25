package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.call.CallCard.CardCheckIn;
import com.ithopeschool.projectbe.domain.call.CallCard.CardSession;
import com.ithopeschool.projectbe.domain.call.dto.CallSessionRequest;
import com.ithopeschool.projectbe.domain.checkin.CheckIn;
import com.ithopeschool.projectbe.domain.checkin.CheckInRepository;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.stats.StudyFacts.FactPlan;
import com.ithopeschool.projectbe.domain.stats.WeeklyFacts;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// AI 통화(·채팅)에 쓸 "학생 카드" 를 서버 DB 의 기록으로 만듭니다. (이슈 #28 "대본 속 숫자가 모두 DB 에서 계산한 값이다")
// 계산 규칙은 CallCard.of 에 있고, 이 파일은 DB 에서 그 사람의 기록을 꺼내 넘기기만 합니다.
//
// 누구의 기록인지는 부르는 쪽이 로그인 토큰으로 얻은 사용자 번호(CurrentUserProvider.getCurrentUserId())로 넘깁니다.
// 모든 조회에 그 번호가 들어가서, 다른 사람의 기록이 카드에 섞이지 않습니다.
//
// 꺼내는 기록
//   - 계획: 이번 주 월요일(어제가 더 이르면 어제)부터 오늘까지. 노는 계획은 뺍니다.
//   - 공부 기록: 최근 120일. 노는 기록은 뺍니다. (연속 공부 일수는 이 기간 안에서만 셉니다)
//   - 체크인: 오늘 포함 7일. 기분이 비어 있는 체크인도 "어제 못 채운 이유" 를 위해 넘깁니다.
//
// ⚠️ 서버에 계획도 공부 기록도 없으면 카드가 없습니다(null). 체크인만 있으면 "어제 0분" 처럼 틀린 숫자가 되기 때문입니다.
//    FE 의 계획·공부 기록이 아직 기기에만 있는 학생(FE #202 · #203 전)은 이때 FE 가 보낸 카드를 씁니다. (CallService)
// ⚠️ 공부하다 나간 기록 · 집중 모드를 푼 기록은 아직 서버에 없어서 카드에 넣지 않습니다(null). 부르는 쪽이 FE 값으로 채웁니다.
@Component
@RequiredArgsConstructor
public class CallCardLoader {

  // 연속 공부 일수를 셀 때 보는 공부 기록의 기간(일). 이보다 길게 이어진 연속은 이 기간까지만 셉니다.
  static final int SESSION_LOOKBACK_DAYS = 120;

  // 최근 기분을 볼 기간(일). 오늘을 넣어 7일입니다. (CallCard 규칙 6)
  static final int CHECK_IN_DAYS = 7;

  private final StudyPlanRepository studyPlanRepository;
  private final StudySessionRepository studySessionRepository;
  private final CheckInRepository checkInRepository;

  // 그 사람의 학생 카드. 서버에 계획도 공부 기록도 없으면 null 입니다.
  // today: 오늘 (새벽 4시 기준, DayRule) / now: 지금 시각
  public CallSessionRequest.Card load(Long userId, LocalDate today, Instant now) {
    LocalDate monday = WeeklyFacts.mondayOf(now);
    LocalDate yesterday = today.minusDays(1);
    LocalDate planFrom = yesterday.isBefore(monday) ? yesterday : monday;

    List<FactPlan> plans = new ArrayList<>();
    for (StudyPlan plan :
        studyPlanRepository.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
            userId, planFrom, today)) {
      if (plan.getKind() != PlanKind.PLAY) {
        plans.add(
            new FactPlan(
                plan.getId(), plan.getPlanDate(), plan.getTargetMinutes(), plan.getStartTime()));
      }
    }

    List<CardSession> sessions = new ArrayList<>();
    for (StudySession session :
        studySessionRepository.findStartedBetween(
            userId,
            DayRule.startOf(today.minusDays(SESSION_LOOKBACK_DAYS - 1)),
            DayRule.startOf(today.plusDays(1)))) {
      if (session.getKind() != PlanKind.PLAY) {
        sessions.add(
            new CardSession(
                session.getPlanId(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getPausedMs(),
                session.getPauseCount(),
                session.getTargetMinutes() == null ? 0 : session.getTargetMinutes()));
      }
    }

    // 서버에 공부 쪽 기록이 하나도 없으면 카드를 만들지 않습니다. (위 ⚠️)
    if (plans.isEmpty() && sessions.isEmpty()) {
      return null;
    }

    List<CardCheckIn> checkIns = new ArrayList<>();
    for (CheckIn checkIn :
        checkInRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(
            userId, today.minusDays(CHECK_IN_DAYS - 1), today)) {
      // 기분이 비어 있으면 0 으로 넘깁니다. CallCard 는 1 ~ 5 가 아닌 기분을 "최근 기분" 에서 빼고, 못 채운 이유만 씁니다.
      int mood = checkIn.getMood() == null ? 0 : checkIn.getMood();
      checkIns.add(new CardCheckIn(checkIn.getCheckInDate(), mood, checkIn.getMissReason()));
    }

    CallSessionRequest.Card card =
        CallCard.of(plans, sessions, checkIns, List.of(), List.of(), today, now);
    // 나간 기록 · 집중 모드는 서버에 없어서 "모름" 으로 둡니다. (0 이면 "한 번도 안 나갔다" 가 되어 버립니다)
    return withFocusFrom(card, null);
  }

  // 서버 카드에, 서버에 없는 값(나간 횟수 · 가장 자주 나간 시간대 · 집중 모드를 푼 횟수)만 FE 카드에서 옮겨 담습니다.
  // FE 카드가 없으면 그 세 칸은 비어 있습니다(모름).
  // "지난 통화 이후" 칸(결산 · 카드 · 조건부 모듈, #28)도 FE 가 기기의 카드 기록으로 계산해서 FE 카드 값을 옮깁니다. (#434)
  public static CallSessionRequest.Card withFocusFrom(
      CallSessionRequest.Card serverCard, CallSessionRequest.Card feCard) {
    if (feCard == null) {
      return new CallSessionRequest.Card(
          serverCard.yesterdayStudyMinutes(),
          serverCard.yesterdayPlanMinutes(),
          serverCard.yesterdayMissReason(),
          serverCard.todayPlanCount(),
          serverCard.todayPlanMinutes(),
          serverCard.todayFirstStart(),
          serverCard.weekPlanRate(),
          serverCard.weekCompletionRate(),
          serverCard.weekStartDelayMinutes(),
          serverCard.recentMoods(),
          serverCard.streakDays(),
          null,
          null,
          null);
    }
    return new CallSessionRequest.Card(
        serverCard.yesterdayStudyMinutes(),
        serverCard.yesterdayPlanMinutes(),
        serverCard.yesterdayMissReason(),
        serverCard.todayPlanCount(),
        serverCard.todayPlanMinutes(),
        serverCard.todayFirstStart(),
        serverCard.weekPlanRate(),
        serverCard.weekCompletionRate(),
        serverCard.weekStartDelayMinutes(),
        serverCard.recentMoods(),
        serverCard.streakDays(),
        feCard.yesterdayAwayCount(),
        feCard.yesterdayAwayPeakHour(),
        feCard.yesterdayUnlockCount(),
        feCard.hoursSinceLastCall(),
        feCard.doneCardCount(),
        feCard.cardRunRate(),
        feCard.missedCardCount(),
        feCard.unhandledCardCount(),
        feCard.mainMissCause(),
        feCard.nextCardCount(),
        feCard.nextAbsoluteCardCount(),
        feCard.emotionTagsMissing(),
        feCard.callModule());
  }
}
