package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.ai.DayPlanService;
import com.ithopeschool.projectbe.domain.auth.TokenHasher;
import com.ithopeschool.projectbe.domain.bias.BiasQuestion;
import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import com.ithopeschool.projectbe.domain.bias.Weakness;
import com.ithopeschool.projectbe.domain.call.CallKeyClient.CallKey;
import com.ithopeschool.projectbe.domain.call.dto.CallSessionRequest;
import com.ithopeschool.projectbe.domain.call.dto.CallSessionResponse;
import com.ithopeschool.projectbe.domain.checkin.CheckIn;
import com.ithopeschool.projectbe.domain.checkin.CheckInRepository;
import com.ithopeschool.projectbe.domain.exam.Exam;
import com.ithopeschool.projectbe.domain.exam.ExamRepository;
import com.ithopeschool.projectbe.domain.gpt.GptUsageGuard;
import com.ithopeschool.projectbe.domain.risk.RiskLevel;
import com.ithopeschool.projectbe.domain.risk.RiskService;
import com.ithopeschool.projectbe.domain.safety.ExamPeriod;
import com.ithopeschool.projectbe.domain.safety.LowMoodStreak;
import com.ithopeschool.projectbe.domain.safety.SafetyBrake;
import com.ithopeschool.projectbe.domain.safety.SafetyService;
import com.ithopeschool.projectbe.domain.thinkingtest.StudyStyle;
import com.ithopeschool.projectbe.domain.thinkingtest.ThinkingGap;
import com.ithopeschool.projectbe.domain.thinkingtest.ThinkingTest;
import com.ithopeschool.projectbe.domain.thinkingtest.ThinkingTestRepository;
import com.ithopeschool.projectbe.domain.thinkingtest.ThinkingTestService;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

// AI 통화를 시작하는 로직입니다. (이슈 #27 · FE src/lib/realtimeCall.ts)
//
// 순서
//   1. 로그인한 사람인지 확인한다             (아니면 401)
//   2. 통화를 켤 수 있는 서버인지 본다         (내 컴퓨터에서는 꺼져 있습니다 → 503)
//   3. 오늘 끝낸 공부가 있는지 본다           (있으면 확인 통화. 없어도 통화는 걸 수 있습니다. CheckCall)
//      처음(2026-10-18)에는 공부가 없으면 403 CALL_LOCKED 였는데, 2026-10-22 에 이 조건을 뺐습니다.
//   4. 최근 2주의 통화 기록을 읽는다          (CallRecord)
//   5. 오늘 확인할 생각 습관 질문을 고른다     (BiasQuestion: 자주 보인 습관 → 어제 이유 → 시작 지연 → 가입 테스트)
//      가입 테스트는 서버에 저장된 결과(thinking_tests)를 먼저 쓰고, 없으면 FE 가 보낸 값을 씁니다.
//      저장된 결과가 있으면 최근 통화와 견줘 "생각과 실제가 달랐던 점" 도 지침에 넣습니다. (ThinkingGap)
//   6. 학생 카드와 지난 통화로 대본(지침)을 만든다 (CallScript: 기록에 따라 질문과 말투가 달라집니다)
//      학생 카드의 숫자는 서버 DB 의 그 사람 기록으로 계산합니다. (CallCardLoader, 이슈 #28)
//      서버에 계획·공부 기록이 아직 없으면(FE 가 기기에만 저장하던 학생) FE 가 보낸 카드를 씁니다.
//      오늘이 무너지기 쉬운 날이면 대본에 "표시" 를 넣습니다. (RiskService.levelOn: 위험 예측 이슈 #49. 저장하지 않고 지금 기록으로 계산)
//      대본 끝에 시험 · 목표 · 가입 심리검사를 "[기록에서 꺼낸 것]" 으로 붙입니다. 위로하는 통화에서는 붙이지 않습니다. (CallContext)
//      오늘 끝낸 공부가 있으면 "[방금 공부한 것]" 을 붙여 확인 통화로 시작합니다. 위로하는 통화에서는 붙이지 않습니다.
//      음성 통화는 코드가 고른 주제 하나만 깊게 이야기합니다. (CallTopic, 2026-10-25 #434 "통화도 마찬가지 — 계속 물어봄")
//        FE 카드의 조건부 모듈(callModule, BE #28)이 오면 그 모듈이 주제이고, 안전 브레이크 · 회복 모드면 위로하는 통화(0)로 합니다.
//        생각 습관이 오늘 주제가 아니면 질문을 묻지 않으니, 통화 기록에도 "물어본 질문" 을 남기지 않습니다.
//      음성 통화 대본 끝에는 계획 방(#431)과 같은 "[오늘 쓸 수 있는 시간]"(DayPlanService.todayTime)과
//      FE 가 보낸 오늘 채팅 이야기 "[다른 방에서 오늘 한 이야기]"(OtherRoomTalk.callSection)를 붙입니다. 학원 · 빈 시간 · 오늘 카드를 또
// 묻지 않게.
//   7. OpenAI 에서 그 대본이 묶인 임시 키를 받는다 (못 받으면 503)
//   8. 통화 기록을 한 줄 만들고, GPT 호출 기록에도 "통화 시작" 을 남긴 뒤, 임시 키와 기록 번호를 돌려준다
//
// 안전 브레이크가 켜졌으면, FE 가 보낸 관리 강도와 상관없이 위로하는 통화(0)로 합니다. (careLevelFor)
//   최근 7일 통화의 위기 표현 · 최근 7일 체크인 글의 위기 표현 · 기분 저하 6일 연속
// 시험 3일 전 ~ 2일 뒤는 0 이 아니라 FE 가 보낸 관리 강도를 2(넛지)까지로 낮춥니다. (이슈 #232)
// 안전 브레이크는 어떤 설정보다 먼저입니다. (CLAUDE.md 4장)
//
// ⚠️ 학생 카드와 대본, 임시 키, 지난 통화의 요약은 로그에 찍지 않습니다.
@Service
public class CallService {

  // 지난 통화를 볼 기간(일). 오늘을 넣어 최근 2주의 통화로 자주 보이는 생각 습관을 찾습니다.
  static final int RECENT_DAYS = 14;
  // 지침에 넣는 "테스트에서 많이 해당한 습관" 의 최대 개수 (FE 가 보내는 것과 같은 3개)
  static final int MAX_SELF_REPORTED = 3;
  // 오늘을 넣어 최근 이 기간(일) 안에 위기 표현이 감지된 통화가 있으면, 오늘 통화는 위로하는 통화로 합니다.
  static final int CRISIS_CARE_DAYS = 7;

  private final CurrentUserProvider currentUserProvider;
  private final CallKeyClient callKeyClient;
  // 통화 시작을 기록하고, 통화 한 번의 최대 길이를 알려 줍니다. (이슈 #33. 하루 한도는 2026-10-24 에 없앰)
  private final GptUsageGuard gptUsageGuard;
  private final TokenHasher tokenHasher;
  private final CallRecordRepository callRecordRepository;
  private final ThinkingTestRepository thinkingTestRepository;
  // 안전 브레이크를 서버의 기록으로 한 번 더 보려고 씁니다. (체크인의 글·기분, 시험 날짜)
  private final CheckInRepository checkInRepository;
  private final ExamRepository examRepository;
  // 학생 카드를 서버 DB 의 기록으로 만듭니다. (이슈 #28)
  private final CallCardLoader callCardLoader;
  private final Clock clock;
  private final SafetyService safetyService;
  // 오늘이 무너지기 쉬운 날인지 알려 줍니다. (이슈 #49)
  private final RiskService riskService;
  // 시험 · 목표 · 가입 심리검사를 서버 DB 에서 꺼냅니다. (CallContext)
  private final CallContextLoader callContextLoader;
  // 오늘 끝낸 공부 (있으면 확인 통화로 시작, 2026-10-18)
  private final CheckCallService checkCallService;
  // 음성 통화 대본의 [오늘 쓸 수 있는 시간] 칸 재료. 계획 방 채팅 · AI 하루 계획과 같은 계산입니다. (#434)
  private final DayPlanService dayPlanService;

  public CallService(
      CurrentUserProvider currentUserProvider,
      CallKeyClient callKeyClient,
      GptUsageGuard gptUsageGuard,
      TokenHasher tokenHasher,
      CallRecordRepository callRecordRepository,
      ThinkingTestRepository thinkingTestRepository,
      CheckInRepository checkInRepository,
      ExamRepository examRepository,
      CallCardLoader callCardLoader,
      Clock clock,
      SafetyService safetyService,
      RiskService riskService,
      CallContextLoader callContextLoader,
      CheckCallService checkCallService,
      DayPlanService dayPlanService) {
    this.currentUserProvider = currentUserProvider;
    this.callKeyClient = callKeyClient;
    this.gptUsageGuard = gptUsageGuard;
    this.tokenHasher = tokenHasher;
    this.callRecordRepository = callRecordRepository;
    this.thinkingTestRepository = thinkingTestRepository;
    this.checkInRepository = checkInRepository;
    this.examRepository = examRepository;
    this.callCardLoader = callCardLoader;
    this.clock = clock;
    this.safetyService = safetyService;
    this.riskService = riskService;
    this.callContextLoader = callContextLoader;
    this.checkCallService = checkCallService;
    this.dayPlanService = dayPlanService;
  }

  public CallSessionResponse startSession(CallSessionRequest request) {
    // 로그인하지 않은 요청은 여기서 401 이 나갑니다. 아무나 통화를 걸어 비용을 쓰게 두면 안 됩니다.
    Long userId = currentUserProvider.getCurrentUserId();

    if (!callKeyClient.isEnabled()) {
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "AI 통화가 아직 켜져 있지 않아요");
    }
    Instant now = clock.instant();
    LocalDate today = DayRule.dateOf(now);
    // 오늘 끝낸 공부. 없으면 null 이고, 그러면 [방금 공부한 것] 없이 보통 통화로 시작합니다.
    CheckCall.Studied studied = checkCallService.gateOf(userId, today).studied();
    Script script = prepareScript(userId, request, now, today, CallChannel.VOICE, studied);
    String instructions = script.instructions();
    BiasQuestion biasQuestion = script.biasQuestion();
    // OpenAI 에는 "어느 사용자의 요청인지" 를 알아볼 수 없는 글자로만 알려 줍니다. (번호를 그대로 보내지 않습니다)
    String safetyIdentifier = tokenHasher.hash("starflow-user-" + userId);

    Optional<CallKey> key = callKeyClient.createKey(instructions, safetyIdentifier);
    if (key.isEmpty()) {
      // 통화가 시작되지 않았으니 통화 기록도, GPT 호출 기록도 만들지 않습니다.
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "AI 와 연결하지 못했어요. 잠시 후 다시 시도해 주세요");
    }

    CallRecord record = callRecordRepository.save(new CallRecord(userId, today, now, biasQuestion));
    gptUsageGuard.recordCallStart(userId, record.getId());
    return new CallSessionResponse(
        key.get().value(),
        key.get().expiresAtMillis(),
        // FE 는 이 시간이 되면 통화를 끊습니다. (통화 한 번 최대 길이, 기본 300초. #434 에서 180 → 300)
        gptUsageGuard.maxCallSeconds(),
        String.valueOf(record.getId()));
  }

  // 대본(지침)과 오늘 확인할 생각 습관 질문입니다. 음성 통화와 상담 채팅(ChatService, 이슈 #238)이 같이 씁니다.
  record Script(String instructions, BiasQuestion biasQuestion) {}

  // 위 순서의 4 ~ 6 번입니다. 안전 브레이크 · 학생 카드(DB) · 생각 습관 테스트 · 지난 통화로 대본을 만듭니다.
  // channel 이 CHAT 이면 같은 대본의 글 버전입니다. (CallScript.chatInstructions)
  Script prepareScript(
      Long userId, CallSessionRequest request, Instant now, LocalDate today, CallChannel channel) {
    return prepareScript(userId, request, now, today, channel, null);
  }

  // studied 가 있으면 대본 끝에 "[방금 공부한 것]" 을 붙여 확인 통화로 시작합니다. (음성 통화, 위로하는 통화는 빼고)
  Script prepareScript(
      Long userId,
      CallSessionRequest request,
      Instant now,
      LocalDate today,
      CallChannel channel,
      CheckCall.Studied studied) {
    return prepareScript(userId, request, now, today, channel, studied, null);
  }

  // 상담 채팅(ChatService)의 대본입니다. 방(room)에 따라 대본이 조금 달라집니다. (2026-10-25 #431)
  // 계획 방(PLANNER)이면 [생각 습관] · [통화 순서] 를 넣지 않고(CallScript.chatInstructions),
  // 생각 습관 질문도 고르지 않습니다(biasQuestion 은 null). 묻지 않은 질문이 "물어본 질문" 으로 기록에 남지 않게 하려는 것입니다.
  Script prepareChatScript(
      Long userId, CallSessionRequest request, Instant now, LocalDate today, ChatRoom room) {
    return prepareScript(userId, request, now, today, CallChannel.CHAT, null, room);
  }

  // room 은 상담 채팅의 방입니다. 음성 통화 · 방을 모르는 채팅은 null 이고, 그러면 상담 방과 같습니다.
  private Script prepareScript(
      Long userId,
      CallSessionRequest request,
      Instant now,
      LocalDate today,
      CallChannel channel,
      CheckCall.Studied studied,
      ChatRoom room) {
    // 최근 2주의 내 통화 기록 (최근 것이 앞)
    List<CallRecord> recentCalls =
        callRecordRepository.findByUserIdAndCallDateGreaterThanEqualOrderByStartedAtDescIdDesc(
            userId, today.minusDays(RECENT_DAYS - 1));

    // 안전 브레이크가 켜졌으면 위로하는 통화로 합니다. (관리 강도 0 이면 말투·순서가 모두 위로하는 쪽으로 바뀝니다)
    // 시험 기간이면 채찍 말투(3~4)를 빼고 넛지(2)까지만 씁니다.
    int careLevel = careLevelFor(userId, recentCalls, now, today, request.careLevel());
    // 학생 카드: 서버 DB 의 기록이 먼저입니다. 나간 기록 · 집중 모드처럼 서버에 없는 값만 FE 카드에서 가져옵니다.
    CallSessionRequest.Card serverCard = callCardLoader.load(userId, today, now);
    CallSessionRequest.Card card =
        serverCard == null
            ? request.card()
            : CallCardLoader.withFocusFrom(serverCard, request.card());
    // 음성 통화: FE 가 조건부 모듈로 안전 브레이크 · 회복 모드를 보내면 위로하는 통화(0)로 합니다. 채팅은 그대로입니다. (#28 · #434)
    if (channel == CallChannel.VOICE) {
      careLevel = CallScript.voiceCareLevel(careLevel, card);
    }

    // 생각 습관 테스트 결과. 서버에 저장된 것이 있으면 그것을, 없으면 FE 가 보낸 것을 씁니다.
    CallScript.SelfReport selfReport = selfReportOf(userId, request, recentCalls);

    // 계획 방은 생각 습관 질문을 하지 않아서 고르지 않습니다. (#431)
    BiasQuestion biasQuestion =
        room == ChatRoom.PLANNER
            ? null
            : BiasQuestion.pick(
                CallScript.isSupportive(careLevel, card),
                card == null ? null : card.yesterdayMissReason(),
                card == null ? null : card.weekStartDelayMinutes(),
                foundBiasesOf(recentCalls),
                askedQuestionKeysOf(recentCalls),
                selfReport.biases());
    // 오늘이 무너지기 쉬운 날이면 대본에 "표시" 로 넘깁니다. 이유(수면 등)는 넘기지 않고 위험 정도만 넘깁니다.
    RiskLevel riskLevel = riskService.levelOn(userId, today);
    List<CallScript.PastCall> pastCalls = pastCallsOf(recentCalls);
    boolean supportive = CallScript.isSupportive(careLevel, card);
    String instructions;
    if (channel == CallChannel.CHAT) {
      instructions =
          CallScript.chatInstructions(
              careLevel,
              request.maxHarshness(),
              card,
              biasQuestion,
              pastCalls,
              selfReport,
              riskLevel,
              room == null ? ChatRoom.COACH : room);
    } else {
      // 확인 통화인지는 [방금 공부한 것] 을 붙이는지와 같습니다. (위로하는 통화에는 붙이지 않음 — 아래)
      boolean checkCall = studied != null && !supportive;
      instructions =
          CallScript.instructions(
              careLevel,
              request.maxHarshness(),
              card,
              biasQuestion,
              pastCalls,
              selfReport,
              riskLevel,
              new CallScript.VoiceCall(gptUsageGuard.maxCallSeconds(), checkCall));
      // 생각 습관이 오늘 주제가 아니면 대본에 그 질문이 없으니, 통화 기록에 "물어본 질문" 으로 남기지 않습니다. (#434)
      CallTopic topic = CallScript.voiceTopic(careLevel, card, biasQuestion, pastCalls, checkCall);
      if (!topic.usesBiasQuestion()) {
        biasQuestion = null;
      }
    }
    // 위로하는 통화에서는 시험 · 점수 이야기가 부담이 될 수 있어서 기록 칸을 붙이지 않습니다.
    if (!supportive) {
      instructions += CallContext.section(callContextLoader.load(userId, today));
      instructions += CallScript.studiedSection(studied);
      // 기기에서 잰 집중도 · 폰 든 횟수 · 확인 문제 결과 (서버에 없는 값이라 늘 FE 값, 2026-10-18)
      instructions += CallScript.studySignalsSection(request.studySignals());
    }
    // 음성 통화: 오늘 쓸 수 있는 시간 · 오늘 채팅 이야기 (#434)
    // 위로하는 통화에도 붙입니다. 계획 방(#431)처럼 대본의 위로 규칙("학생이 원할 때만 아주 작게")이 먼저라서입니다.
    // 둘 다 저장 · 로그 안 함. 재료가 없으면 빈 글자라 대본이 바뀌지 않습니다.
    if (channel == CallChannel.VOICE) {
      instructions += PlannerTime.section(dayPlanService.todayTime());
      instructions += OtherRoomTalk.callSection(request.otherRooms());
    }
    return new Script(instructions, biasQuestion);
  }

  // 통화 지침에 넣을 "생각" 쪽 자료를 모읍니다.
  //   서버에 저장된 테스트가 있으면: 많이 해당한 습관(최대 3개) · 약점 칩 · 최근 2주 통화와 견줘 달랐던 점
  //   없으면: FE 가 요청에 실어 보낸 습관 · 약점 칩 (견줄 것은 없음)
  // 견준 결과는 통화가 3번 이상 쌓였을 때만 넣습니다. 그 전에는 "달랐다" 고 말하기 이릅니다. (ThinkingGap.MIN_CALLS)
  private CallScript.SelfReport selfReportOf(
      Long userId, CallSessionRequest request, List<CallRecord> recentCalls) {
    Optional<ThinkingTest> saved = thinkingTestRepository.findFirstByUserIdOrderByIdDesc(userId);
    if (saved.isEmpty()) {
      return new CallScript.SelfReport(
          selfReportedOf(request.selfReportedBiases()),
          weaknessesOf(request.selfReportedWeaknesses()),
          List.of());
    }
    ThinkingTest test = saved.get();
    List<CognitiveBias> top = StudyStyle.topHabitsOf(test.getScores());
    List<Weakness> weaknesses = new ArrayList<>();
    for (Weakness weakness : Weakness.values()) {
      if (test.getWeaknesses().contains(weakness)) {
        weaknesses.add(weakness);
      }
    }
    ThinkingGap gap = ThinkingTestService.gapOf(test, recentCalls, RECENT_DAYS);
    return new CallScript.SelfReport(
        top.subList(0, Math.min(MAX_SELF_REPORTED, top.size())),
        weaknesses,
        gap.enoughData() ? gap.differenceSentences() : List.of());
  }

  // 가입 테스트에서 많이 고른 습관 이름들을 목록의 습관으로 바꿉니다. 안 보냈으면 빈 목록입니다.
  // (이름은 요청을 받을 때 이미 검사했습니다. CallSessionRequest.BIAS_NAMES)
  static List<CognitiveBias> selfReportedOf(List<String> names) {
    List<CognitiveBias> biases = new ArrayList<>();
    if (names == null) {
      return biases;
    }
    for (String name : names) {
      biases.add(CognitiveBias.valueOf(name));
    }
    return biases;
  }

  // 가입 테스트에서 고른 약점 칩 이름들을 Weakness 로 바꿉니다. 안 보냈으면 빈 목록입니다. (이름은 이미 검사했습니다)
  static List<Weakness> weaknessesOf(List<String> names) {
    List<Weakness> weaknesses = new ArrayList<>();
    if (names == null) {
      return weaknesses;
    }
    for (String name : names) {
      weaknesses.add(Weakness.valueOf(name));
    }
    return weaknesses;
  }

  // 이번 통화의 관리 강도를 서버의 기록으로 한 번 더 정합니다. (이슈 #28 "안전 브레이크가 걸린 날은 채찍 말투가 빠진다")
  // 안전 브레이크가 켜졌으면 0 입니다.
  //   1) 보호 조치 중이다 (SafetyService.isActive) · 오늘을 넣어 최근 7일 안에 위기 표현이 감지된 통화가 있다 (hasRecentCrisis)
  //   2) SafetyBrake 의 위기 표현 · 기분 저하 중 하나라도 걸린다 (이슈 #56 · #59)
  //      글은 최근 7일 체크인의 기타 이유 · 약속, 기분은 같은 체크인의 기분을 봅니다.
  // 시험 기간(서버에 저장한 시험 날짜로 3일 전 ~ 2일 뒤)이면 requested 를 2 까지로 낮춥니다. (이슈 #58 · #232)
  // 그 밖에는 FE 가 보낸 requested 그대로입니다.
  //
  //   FE 3, 최근 통화에 위기 표현  →  0
  //   FE 3, 시험 이틀 전          →  2
  //   FE 1, 시험 이틀 전          →  1
  //   FE 3, 아무 일 없음          →  3
  //
  // FE 도 같은 규칙으로 관리 강도를 정해 보내지만, 기기에만 있는 기록으로 판정해서 서버가 한 번 더 봅니다.
  // 체크인 · 시험이 아직 서버에 없는 학생은 1) 만 걸립니다. (기록이 없는 날은 기분 저하로 세지 않습니다)
  private int careLevelFor(
      Long userId, List<CallRecord> recentCalls, Instant now, LocalDate today, int requested) {
    // 저장된 보호 조치와 서버 기록 기반 판정을 모두 유지합니다.
    if (safetyService.isActive(userId) || hasRecentCrisis(recentCalls, today)) {
      return 0;
    }
    List<String> texts = new ArrayList<>();
    List<LowMoodStreak.MoodRecord> moods = new ArrayList<>();
    for (CheckIn checkIn :
        checkInRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(
            userId, today.minusDays(CRISIS_CARE_DAYS - 1), today)) {
      texts.add(checkIn.getMissReasonNote());
      texts.add(checkIn.getPromiseIf());
      texts.add(checkIn.getPromiseThen());
      if (checkIn.getMood() != null) {
        moods.add(new LowMoodStreak.MoodRecord(checkIn.getCheckInDate(), checkIn.getMood()));
      }
    }
    List<LocalDate> examDates = new ArrayList<>();
    for (Exam exam : examRepository.findByUserIdOrderByExamDateDescIdDesc(userId)) {
      examDates.add(exam.getExamDate());
    }
    // 관리 강도 계산(CareLevel.of)에 넘기는 것과 같은 기준입니다. (SafetyBrake 맨 위 설명)
    SafetyBrake brake = SafetyBrake.check(now, texts, moods, examDates);
    if (brake.stopWhip() || brake.recoveryMode()) {
      return 0;
    }
    return ExamPeriod.capCareLevel(requested, brake.examPeriod());
  }

  // 오늘을 넣어 최근 7일 안에 위기 표현이 감지된 통화가 있으면 true 입니다.
  static boolean hasRecentCrisis(List<CallRecord> recentCalls, LocalDate today) {
    LocalDate from = today.minusDays(CRISIS_CARE_DAYS - 1);
    for (CallRecord call : recentCalls) {
      if (call.isCrisisDetected() && !call.getCallDate().isBefore(from)) {
        return true;
      }
    }
    return false;
  }

  // 지난 통화에서 보인 생각 습관들 (최근 것이 앞). 위기 표현이 있던 통화는 내용을 남기지 않아서 저절로 빠집니다.
  static List<CognitiveBias> foundBiasesOf(List<CallRecord> recentCalls) {
    List<CognitiveBias> found = new ArrayList<>();
    for (CallRecord call : recentCalls) {
      if (call.getFoundBias() != null) {
        found.add(call.getFoundBias());
      }
    }
    return found;
  }

  // 지난 통화에서 고른 질문의 이름들 (최근 것이 앞). 같은 질문이 연달아 나오지 않게 하는 데 씁니다.
  static List<String> askedQuestionKeysOf(List<CallRecord> recentCalls) {
    List<String> keys = new ArrayList<>();
    for (CallRecord call : recentCalls) {
      if (call.getAskedQuestionKey() != null) {
        keys.add(call.getAskedQuestionKey());
      }
    }
    return keys;
  }

  // 지침에 적을 지난 통화들 (최근 것이 앞). 마무리한 통화만 넣고, 위기 표현이 있던 통화는 넣지 않습니다.
  static List<CallScript.PastCall> pastCallsOf(List<CallRecord> recentCalls) {
    List<CallScript.PastCall> pastCalls = new ArrayList<>();
    for (CallRecord call : recentCalls) {
      if (!call.isFinished() || call.isCrisisDetected()) {
        continue;
      }
      pastCalls.add(
          new CallScript.PastCall(
              call.getCallDate(),
              call.getSummary(),
              call.getFoundBias(),
              call.getFoundEvidence(),
              CallSummary.promiseOf(call.getPromiseIf(), call.getPromiseThen()),
              call.getPromiseKept()));
    }
    return pastCalls;
  }
}
