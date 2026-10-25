package com.ithopeschool.projectbe.domain.call.dto;

import com.ithopeschool.projectbe.domain.call.CallModule;
import com.ithopeschool.projectbe.domain.call.ChatRoom;
import com.ithopeschool.projectbe.domain.call.OtherRoomTalk;
import com.ithopeschool.projectbe.domain.care.MissReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

// AI 통화를 시작할 때 FE 가 보내는 모양입니다. (FE src/lib/realtimeCall.ts, src/lib/callCard.ts)
//
// 통화는 "쌓인 기록" 을 보고 질문과 말투가 달라져야 합니다. 그런데 기록이 아직 브라우저에만 있어서,
// FE 가 계산한 숫자를 "학생 카드" 한 장으로 모아 같이 보냅니다. 서버는 이 카드로 AI 의 지침을 만듭니다. (CallScript)
//
// ⚠️ 카드에는 숫자와 정해진 분류값만 담습니다. 계획 내용·일기·이름 같은 글 원문은 받지 않습니다.
//    그래서 모든 값의 범위와 모양을 좁게 정해 두었습니다. (이 값들이 AI 의 지침에 들어가기 때문입니다)
//    예외: 공부 신호(StudySignals)의 "틀린 확인 문제 주제" 는 30자 · 3개짜리 글입니다. 이유는 StudySignals 주석.
//    예외: 다른 방 이야기(otherRooms)는 학생이 오늘 채팅 방에서 쓴 말 몇 줄입니다. 이유는 아래 칸 주석. (#434)
public record CallSessionRequest(
    // 오늘의 관리 강도. 0 회복 · 1 당근 · 2 넛지 · 3 심리적 채찍 · 4 강제적 채찍(말투는 3 과 같게 씁니다)
    @NotNull(message = "오늘의 관리 강도(careLevel)를 보내 주세요")
        @Min(value = 0, message = "careLevel 은 0 ~ 4 예요")
        @Max(value = 4, message = "careLevel 은 0 ~ 4 예요")
        Integer careLevel,
    // 내가 허용한 독설 수위 (1 ~ 5)
    @NotNull(message = "독설 허용 수위(maxHarshness)를 보내 주세요")
        @Min(value = 1, message = "maxHarshness 는 1 ~ 5 예요")
        @Max(value = 5, message = "maxHarshness 는 1 ~ 5 예요")
        Integer maxHarshness,
    // 학생 카드. 기록이 하나도 없는 사람은 null 로 보내도 됩니다. (그러면 처음 만난 사람처럼 통화합니다)
    @Valid Card card,
    // 가입할 때 한 "생각 습관 테스트" 에서 많이 고른 생각 습관들. 많이 고른 것부터, 최대 3개 (FE src/lib/thinkingTest.ts)
    // 이름은 생각 습관 목록(CognitiveBias)과 같은 글자입니다. 예: ["PLANNING_FALLACY", "FUTURE_SELF"]
    // 테스트를 안 했으면 null 이나 빈 목록입니다. 통화 기록이 쌓이기 전에 첫 질문을 고르는 데 씁니다. (BiasQuestion)
    @Size(max = 3, message = "selfReportedBiases 는 3개까지 보낼 수 있어요")
        List<
                @NotNull(message = "selfReportedBiases 에 빈 값이 있어요")
                @Pattern(regexp = BIAS_NAMES, message = "selfReportedBiases 는 정해진 생각 습관 이름이어야 해요")
                String>
            selfReportedBiases,
    // 생각 습관 테스트 마지막에 고른 "내가 생각하는 나의 약점" 칩 이름들 (Weakness). 안 골랐으면 null 이나 빈 목록
    // 예: ["GAME", "SLEEP"]. "기타" 에 직접 쓴 글은 받지 않습니다.
    @Size(max = 11, message = "selfReportedWeaknesses 는 11개까지 보낼 수 있어요")
        List<
                @NotNull(message = "selfReportedWeaknesses 에 빈 값이 있어요")
                @Pattern(
                    regexp = WEAKNESS_NAMES,
                    message = "selfReportedWeaknesses 는 정해진 약점 이름이어야 해요")
                String>
            selfReportedWeaknesses,
    // 상담 채팅을 "무엇에 대한 대화인지" 와 함께 열 때만 씁니다. (2026-10-18 "코치랑 이야기하기", ChatAbout)
    // 음성 통화(POST /realtime/session)는 이 칸을 쓰지 않습니다. 없으면 null 입니다.
    @Valid ChatAbout about,
    // 상담 채팅의 방: COACH(상담, 기본) · PLANNER(계획) · TUTOR(과목 쌤). 없으면 상담입니다. (2026-10-18, ChatRoom)
    // 예전 이름 GOAL_MATE(목표 메이트)도 받아서 PLANNER 로 봅니다. 음성 통화는 이 칸을 쓰지 않습니다.
    @Pattern(regexp = ROOMS, message = "방(room)은 COACH · PLANNER · TUTOR 중 하나예요") String room,
    // 공부 신호: 기기에만 있는 최근 7일 집중도 · 폰 든 횟수 · 확인 문제 결과 (2026-10-18, StudySignals)
    // 통화와 채팅 둘 다 씁니다. 없으면 null 이고, 그러면 대본에 [공부 신호] 칸이 없습니다.
    @Valid StudySignals studySignals,
    // 다른 방 이야기 (2026-10-25 #434, 음성 통화만): 학생이 오늘 채팅 방에서 쓴 말 몇 줄. "방 이름: 말" 모양입니다.
    //   ["계획: 오늘 2시간만 할래", "과목 쌤: 곱의 미분법 알려 줘"]
    // 오늘 채팅에서 정한 것을 통화에서 또 묻지 않게, 통화 대본 끝 [다른 방에서 오늘 한 이야기] 칸에 넣습니다. (OtherRoomTalk.callSection)
    // 모양은 채팅 말 보내기(ChatMessageRequest.otherRooms)와 같습니다. 12줄 · 한 줄 200자까지 받고, 쓸 때는 6줄 · 80자로 줄입니다.
    // 저장 · 로그 안 함. 위기 표현이 있는 줄은 넣지 않습니다. 채팅 열기(POST /chats)는 이 칸을 쓰지 않습니다. 없으면 null
    @Size(max = 12, message = "다른 방 이야기(otherRooms)는 12줄까지 보낼 수 있어요")
        List<
                @Size(
                    max = OtherRoomTalk.MAX_REQUEST_LINE_LENGTH,
                    message = "다른 방 이야기 한 줄은 200자 이하로 보내 주세요")
                String>
            otherRooms) {

  // 받을 수 있는 방 이름들입니다. ChatRoom 에 방을 더하면 여기에도 더해야 합니다. (ChatRoomTest 가 확인)
  // 맨 끝의 GOAL_MATE 는 예전 이름이라 받기만 하고 PLANNER 로 바꿉니다. (ChatRoom.from)
  public static final String ROOMS = "COACH|PLANNER|TUTOR|" + ChatRoom.OLD_GOAL_MATE;

  // 주제 · 방 없이 여는 예전 모양 (통화 · 코치 채팅 · 테스트에서 씀)
  public CallSessionRequest(
      Integer careLevel,
      Integer maxHarshness,
      Card card,
      List<String> selfReportedBiases,
      List<String> selfReportedWeaknesses) {
    this(
        careLevel,
        maxHarshness,
        card,
        selfReportedBiases,
        selfReportedWeaknesses,
        null,
        null,
        null);
  }

  // 공부 신호 없이 주제 · 방만 주는 모양 (채팅 · 테스트에서 씀)
  public CallSessionRequest(
      Integer careLevel,
      Integer maxHarshness,
      Card card,
      List<String> selfReportedBiases,
      List<String> selfReportedWeaknesses,
      ChatAbout about,
      String room) {
    this(
        careLevel,
        maxHarshness,
        card,
        selfReportedBiases,
        selfReportedWeaknesses,
        about,
        room,
        null);
  }

  // 다른 방 이야기 없이 보내는 모양 (채팅 · 테스트에서 씀, #434 전의 모양)
  public CallSessionRequest(
      Integer careLevel,
      Integer maxHarshness,
      Card card,
      List<String> selfReportedBiases,
      List<String> selfReportedWeaknesses,
      ChatAbout about,
      String room,
      StudySignals studySignals) {
    this(
        careLevel,
        maxHarshness,
        card,
        selfReportedBiases,
        selfReportedWeaknesses,
        about,
        room,
        studySignals,
        null);
  }

  // 받을 수 있는 생각 습관 이름들입니다. CognitiveBias 에 습관을 더하면 여기에도 더해야 합니다. (CallControllerTest 가 확인)
  // 받을 수 있는 약점 이름들입니다. Weakness 에 칩을 더하면 여기에도 더해야 합니다. (CallControllerTest 가 확인)
  public static final String WEAKNESS_NAMES =
      "GAME|PHONE|VIDEO|SLEEP|LAZY|DELAY|FOCUS|FRIENDS|TIRED|MOOD|PLANNING";

  public static final String BIAS_NAMES =
      "PLANNING_FALLACY|FUTURE_SELF|WHAT_THE_HELL|EMOTIONAL_REASONING"
          + "|EXTERNAL_BLAME|ALL_OR_NOTHING|MORAL_LICENSING|OVERGENERALIZING";

  // 학생 카드: 통화에서 쓸 "사실" 들입니다. 모르는 값은 null 로 보냅니다. (0 과 "모름" 은 다릅니다)
  public record Card(
      // 어제의 순공시간(분)
      @Min(value = 0, message = "yesterdayStudyMinutes 는 0 ~ 1440 이에요")
          @Max(value = 1440, message = "yesterdayStudyMinutes 는 0 ~ 1440 이에요")
          Integer yesterdayStudyMinutes,
      // 어제 계획의 목표 시간 합계(분). 계획이 없었으면 0
      @Min(value = 0, message = "yesterdayPlanMinutes 는 0 ~ 1440 이에요")
          @Max(value = 1440, message = "yesterdayPlanMinutes 는 0 ~ 1440 이에요")
          Integer yesterdayPlanMinutes,
      // 어제 계획을 못 채운 이유로 고른 것. FE 의 MissReason 과 같은 글자입니다. 안 골랐으면 null
      @Pattern(
              regexp = "tired|event|phone|tooMuch|unwilling|sick|procrastinated|etc",
              message = "yesterdayMissReason 은 정해진 값 중 하나여야 해요")
          String yesterdayMissReason,
      // 오늘 계획의 개수와 목표 시간 합계(분)
      @Min(value = 0, message = "todayPlanCount 는 0 ~ 50 이에요")
          @Max(value = 50, message = "todayPlanCount 는 0 ~ 50 이에요")
          Integer todayPlanCount,
      @Min(value = 0, message = "todayPlanMinutes 는 0 ~ 1440 이에요")
          @Max(value = 1440, message = "todayPlanMinutes 는 0 ~ 1440 이에요")
          Integer todayPlanMinutes,
      // 오늘 첫 계획의 시작 시각 'HH:MM'. 시각을 정하지 않았으면 null
      @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "todayFirstStart 는 'HH:MM' 모양이어야 해요")
          String todayFirstStart,
      // 이번 주: 계획 대비 실제(%), 완주율(%), 계획한 시각보다 평균 몇 분 늦게 시작했는지
      @Min(value = 0, message = "weekPlanRate 는 0 ~ 100 이에요")
          @Max(value = 100, message = "weekPlanRate 는 0 ~ 100 이에요")
          Integer weekPlanRate,
      @Min(value = 0, message = "weekCompletionRate 는 0 ~ 100 이에요")
          @Max(value = 100, message = "weekCompletionRate 는 0 ~ 100 이에요")
          Integer weekCompletionRate,
      @Min(value = 0, message = "weekStartDelayMinutes 는 0 ~ 600 이에요")
          @Max(value = 600, message = "weekStartDelayMinutes 는 0 ~ 600 이에요")
          Integer weekStartDelayMinutes,
      // 최근 기분(1 많이 처짐 ~ 5 아주 좋음). 오래된 날부터 최근 날 순서로, 최대 7개
      @Size(max = 7, message = "recentMoods 는 7개까지 보낼 수 있어요")
          List<
                  @NotNull(message = "recentMoods 에 빈 값이 있어요")
                  @Min(value = 1, message = "기분은 1 ~ 5 예요")
                  @Max(value = 5, message = "기분은 1 ~ 5 예요") Integer>
              recentMoods,
      // 연속으로 공부한 날 수
      @Min(value = 0, message = "streakDays 는 0 ~ 3650 이에요")
          @Max(value = 3650, message = "streakDays 는 0 ~ 3650 이에요")
          Integer streakDays,
      // 어제 공부하다가 다른 탭·앱으로 나갔다 온 횟수 (FE 의 "딴짓 감지")
      @Min(value = 0, message = "yesterdayAwayCount 는 0 ~ 200 이에요")
          @Max(value = 200, message = "yesterdayAwayCount 는 0 ~ 200 이에요")
          Integer yesterdayAwayCount,
      // 어제 가장 자주 나간 시간대 (0 ~ 23시). 나간 적이 없으면 null
      @Min(value = 0, message = "yesterdayAwayPeakHour 는 0 ~ 23 이에요")
          @Max(value = 23, message = "yesterdayAwayPeakHour 는 0 ~ 23 이에요")
          Integer yesterdayAwayPeakHour,
      // 어제 집중 모드를 스스로 푼 횟수 (설정·안전 브레이크 때문에 저절로 풀린 것은 세지 않습니다)
      @Min(value = 0, message = "yesterdayUnlockCount 는 0 ~ 50 이에요")
          @Max(value = 50, message = "yesterdayUnlockCount 는 0 ~ 50 이에요")
          Integer yesterdayUnlockCount,
      // ---- 지난 통화 이후 (FE src/lib/callCard.ts 의 SinceLastCallFields, FE #414 · BE #28. 받기 시작
      // 2026-10-25 #434) ----
      // 위의 칸과 달리 범위를 벗어나거나 모르는 글자면 400 대신 null(모름)로 바꿔 받습니다. (아래 compact constructor)
      // FE 가 이미 보내던 칸이라, 모양이 조금 달라도 통화 자체가 막히지 않게 하려는 것입니다. 대본에는 아는 값만 들어갑니다.
      // 지난 통화가 끝난 뒤 몇 시간 (0 ~ 8760). 통화한 적이 없으면 null → FE 는 최근 24시간으로 셉니다
      Integer hoursSinceLastCall,
      // 지난 통화 이후 실행한 카드 수 (0 ~ 50)
      Integer doneCardCount,
      // 실행한 카드의 평균 실행률 (0 ~ 100). 실행한 카드가 없으면 null
      Integer cardRunRate,
      // 미루거나 못 한 카드 수 (0 ~ 50)
      Integer missedCardCount,
      // 그중 아직 미루기 · 실패를 고르지 않은 카드 수 (0 ~ 50). 통화에서 원인 · 나누기 · 쉬기를 정합니다
      Integer unhandledCardCount,
      // 미실행 원인 1위. MissReason 글자(tired · event · phone · tooMuch · unwilling · sick ·
      // procrastinated · etc)
      String mainMissCause,
      // 다음 통화까지(앞으로 24시간) 할 카드 수 (0 ~ 50)
      Integer nextCardCount,
      // 그중 절대(미룰 수 없는) 카드 수 (0 ~ 50)
      Integer nextAbsoluteCardCount,
      // 오늘 감정 태그를 안 골랐으면 true (결산 전에 기분을 한 번 묻습니다)
      Boolean emotionTagsMissing,
      // 이번 통화에 넣을 조건부 모듈 하나 (CallModule 의 글자). 없으면 null
      String callModule) {

    // 지난 통화 뒤 시간 · 카드 수 · 실행률의 범위
    public static final int MAX_HOURS_SINCE_LAST_CALL = 8760;
    public static final int MAX_CARD_COUNT = 50;

    // "지난 통화 이후" 칸을 정리합니다. 범위 밖 숫자 · 모르는 글자는 null(모름)입니다. 위의 옛 칸은 그대로 둡니다.
    //   doneCardCount 51 → null / cardRunRate -1 → null / mainMissCause "lazy" → null / callModule
    // "quiz" → null
    public Card {
      hoursSinceLastCall = inRangeOrNull(hoursSinceLastCall, MAX_HOURS_SINCE_LAST_CALL);
      doneCardCount = inRangeOrNull(doneCardCount, MAX_CARD_COUNT);
      cardRunRate = inRangeOrNull(cardRunRate, 100);
      missedCardCount = inRangeOrNull(missedCardCount, MAX_CARD_COUNT);
      unhandledCardCount = inRangeOrNull(unhandledCardCount, MAX_CARD_COUNT);
      mainMissCause = MissReason.fromValue(mainMissCause) == null ? null : mainMissCause;
      nextCardCount = inRangeOrNull(nextCardCount, MAX_CARD_COUNT);
      nextAbsoluteCardCount = inRangeOrNull(nextAbsoluteCardCount, MAX_CARD_COUNT);
      callModule = CallModule.from(callModule).isPresent() ? callModule : null;
    }

    // "지난 통화 이후" 칸 없이 만드는 모양 (서버 카드 · 테스트 · #434 전의 FE). 새 칸은 모두 null(모름)입니다.
    public Card(
        Integer yesterdayStudyMinutes,
        Integer yesterdayPlanMinutes,
        String yesterdayMissReason,
        Integer todayPlanCount,
        Integer todayPlanMinutes,
        String todayFirstStart,
        Integer weekPlanRate,
        Integer weekCompletionRate,
        Integer weekStartDelayMinutes,
        List<Integer> recentMoods,
        Integer streakDays,
        Integer yesterdayAwayCount,
        Integer yesterdayAwayPeakHour,
        Integer yesterdayUnlockCount) {
      this(
          yesterdayStudyMinutes,
          yesterdayPlanMinutes,
          yesterdayMissReason,
          todayPlanCount,
          todayPlanMinutes,
          todayFirstStart,
          weekPlanRate,
          weekCompletionRate,
          weekStartDelayMinutes,
          recentMoods,
          streakDays,
          yesterdayAwayCount,
          yesterdayAwayPeakHour,
          yesterdayUnlockCount,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null);
    }

    // 0 ~ max 안이면 그대로, 아니면 null(모름)
    private static Integer inRangeOrNull(Integer value, int max) {
      return value == null || value < 0 || value > max ? null : value;
    }
  }
}
