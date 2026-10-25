package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.bias.BiasQuestion;
import com.ithopeschool.projectbe.domain.call.dto.CallSessionRequest;
import com.ithopeschool.projectbe.domain.checkin.CheckInQuestions;
import java.util.Optional;

// 음성 통화 한 번에서 깊게 이야기할 "오늘 주제" 하나입니다. (2026-10-25 #434)
// 사용자: "통화도 마찬가지" — 깊게 대화가 안 되고 계속 물어봄.
// 전에는 [통화 순서] 가 인사 · 어제 → 지난 약속 → 어제 질문 → 생각 습관 → 오늘 질문 → 약속 을 차례로 물어서,
// 3분 안에 질문이 6~13개였습니다. 그래서 코드가 주제를 하나만 고르고, AI 는 그 주제를 2~3번 주고받으며 깊게 이야기합니다.
// (제품 원칙 "덜 묻고, 잰 숫자로 말한다")
//
// ■ 고르는 규칙 (pick) — 위에서부터 차례로 따지고, 먼저 맞는 줄에서 끝냅니다.
//   1) 위로하는 통화(기분이 낮음 · 관리 강도 0 · 안전 브레이크), 또는 FE 모듈이 안전 브레이크 · 회복 모드  →  COMFORT
//      안전이 먼저입니다. 힘든 날에 못 한 것 · 습관을 캐물으면 자책으로 이어질 수 있어서입니다. (CLAUDE.md 4장)
//   2) 오늘 끝낸 공부가 있음(확인 통화)                                 →  CHECK_STUDY
//      공부를 끝내고 건 통화는 방금 한 공부를 말로 설명하며 복습하는 것이 목적입니다. 다른 주제를 붙이면 둘 다 얕아집니다.
//      FE 모듈(3)보다 먼저인 이유: 왜곡 피드백 · 예측 공개는 주마다 조건이 켜져서, 모듈이 먼저면 복습 통화가 자주 밀립니다.
//   3) FE 가 보낸 조건부 모듈(CallModule, FE 가 우선순위로 골라 보냄)        →  그 모듈
//        review4 → REVIEW4 / goalCheck → GOAL_CHECK
//        distortionFeedback → DISTORTION (짚을 생각 습관 질문이 없으면 건너뜀)
//        predictReveal → PREDICT_REVEAL (맞혀 볼 이번 주 숫자가 없으면 건너뜀)
//   4) 가장 최근 통화에서 정한 약속이 있음                               →  PROMISE
//      학생이 직접 정한 문장이라 이어 가면 "어제를 기억하는 코치" 가 되고, 지켰는지(promiseKept)가 통화 정리에 남습니다.
//   5) 지난 통화 뒤로 아직 정리 안 한 카드가 있음 (unhandledCardCount > 0) →  UNHANDLED_CARDS
//      원인 · 나누기 · 쉬기 중 하나를 정해야 하는 카드라, 잰 습관(6 · 7)보다 먼저입니다.
//   6) 이번 주 평균 시작 지연이 15분 넘음                                →  LATE_START
//   7) 어제 계획을 못 채웠고, 공부하다 3번 이상 나갔다 옴                  →  AWAY
//      6 · 7 은 기기가 잰 숫자라서 생각 습관(해석)보다 먼저입니다. 이번 주 평균(6)이 어제 하루(7)보다 굳은 습관이라 먼저 봅니다.
//      7 은 계획을 다 채운 날에는 고르지 않습니다. 잘한 날에 나간 횟수를 꺼내면 탓하는 말이 되기 쉬워서입니다. (예전 awayQuestion 과 같음)
//   8) 오늘 확인할 생각 습관 질문이 있음 (BiasQuestion.pick 이 고름)       →  BIAS
//   9) 어디에도 안 맞음                                                  →  TODAY (오늘 계획)
//
// 생각 습관 칸([생각 습관])과 질문 기록(askedQuestionKey)은 BIAS · DISTORTION 일 때만 씁니다.
// 묻지 않은 질문이 "물어본 질문" 으로 남지 않게 하려는 것입니다. (usesBiasQuestion)
// 상담 · 과목 쌤 · 계획 방 채팅은 이 주제를 쓰지 않습니다. (채팅 대본은 그대로)
//
// 예시
//   기분이 많이 처짐                                  →  COMFORT
//   FE 모듈 recovery                                  →  COMFORT
//   오늘 미적분 40분 끝냄, 모듈 review4                →  CHECK_STUDY
//   모듈 review4, 지난 약속 있음                       →  REVIEW4
//   지난 약속 "만약 학원에서 오면 → 단어 20개"        →  PROMISE
//   약속 없음, 정리 안 한 카드 2장                    →  UNHANDLED_CARDS
//   약속 없음, 이번 주 평균 23분 늦게 시작             →  LATE_START
//   약속 없음, 어제 79% · 5번 나갔다 옴                →  AWAY
//   약속 없음, 어제 이유 '폰·SNS' (→ 미래의 나 믿기)   →  BIAS
//   기록이 없는 첫 통화                               →  TODAY
public enum CallTopic {
  COMFORT,
  CHECK_STUDY,
  REVIEW4,
  GOAL_CHECK,
  DISTORTION,
  PREDICT_REVEAL,
  PROMISE,
  UNHANDLED_CARDS,
  LATE_START,
  AWAY,
  BIAS,
  TODAY;

  // supportive: 위로하는 통화인지 (CallScript.isSupportive)
  // checkCall: 오늘 끝낸 공부가 있어서 [방금 공부한 것] 을 붙이는 통화인지
  // card: 학생 카드. 기록이 없으면 null (조건부 모듈 callModule 도 카드에 있습니다)
  // lastPromise: 가장 최근 통화에서 정한 약속 "만약 ~ → ~" (CallScript.lastPromise). 없으면 null
  // biasQuestion: 오늘 확인할 생각 습관 질문 (BiasQuestion.pick). 없으면 null
  public static CallTopic pick(
      boolean supportive,
      boolean checkCall,
      CallSessionRequest.Card card,
      String lastPromise,
      BiasQuestion biasQuestion) {
    Optional<CallModule> module = moduleOf(card);
    if (supportive || module.map(CallModule::isComfort).orElse(false)) {
      return COMFORT;
    }
    if (checkCall) {
      return CHECK_STUDY;
    }
    if (module.isPresent()) {
      CallTopic fromModule = topicOfModule(module.get(), card, biasQuestion);
      if (fromModule != null) {
        return fromModule;
      }
    }
    if (lastPromise != null) {
      return PROMISE;
    }
    if (card != null && card.unhandledCardCount() != null && card.unhandledCardCount() > 0) {
      return UNHANDLED_CARDS;
    }
    if (startsLate(card)) {
      return LATE_START;
    }
    if (awayManyOnMissedDay(card)) {
      return AWAY;
    }
    if (biasQuestion != null) {
      return BIAS;
    }
    return TODAY;
  }

  // 이 주제에서 생각 습관 질문을 하는지. 하는 날만 [생각 습관] 칸을 넣고 통화 기록에 질문을 남깁니다.
  public boolean usesBiasQuestion() {
    return this == BIAS || this == DISTORTION;
  }

  // 학생 카드의 조건부 모듈. 카드가 없거나 모듈이 없으면 빈 값입니다.
  static Optional<CallModule> moduleOf(CallSessionRequest.Card card) {
    return card == null ? Optional.empty() : CallModule.from(card.callModule());
  }

  // 위로 쪽이 아닌 모듈의 주제. 그 모듈을 할 재료가 없으면 null 이고, 그러면 아래 서버 규칙으로 고릅니다.
  static CallTopic topicOfModule(
      CallModule module, CallSessionRequest.Card card, BiasQuestion biasQuestion) {
    return switch (module) {
      case REVIEW4 -> REVIEW4;
      case GOAL_CHECK -> GOAL_CHECK;
      case DISTORTION_FEEDBACK -> biasQuestion == null ? null : DISTORTION;
      case PREDICT_REVEAL -> CallScript.predictNumberOf(card) == null ? null : PREDICT_REVEAL;
      // 안전 브레이크 · 회복 모드는 pick 의 맨 앞에서 COMFORT 로 갑니다.
      case SAFETY_BRAKE, RECOVERY -> COMFORT;
    };
  }

  // 이번 주 평균 시작 지연이 15분을 넘는지 (CallScript.LATE_START_MINUTES, FE 주간 코치와 같은 기준)
  static boolean startsLate(CallSessionRequest.Card card) {
    return card != null
        && card.weekStartDelayMinutes() != null
        && card.weekStartDelayMinutes() > CallScript.LATE_START_MINUTES;
  }

  // 어제 계획을 못 채웠고, 공부하다 3번 이상 나갔다 왔는지 (CallScript.MANY_AWAY_COUNT)
  // "못 채운 날" 의 기준은 체크인 질문과 같은 함수입니다. (CheckInQuestions.isMissedDay)
  static boolean awayManyOnMissedDay(CallSessionRequest.Card card) {
    if (card == null
        || card.yesterdayAwayCount() == null
        || card.yesterdayAwayCount() < CallScript.MANY_AWAY_COUNT) {
      return false;
    }
    Integer rate = CallScript.yesterdayRate(card);
    return rate != null && CheckInQuestions.isMissedDay(rate);
  }
}
