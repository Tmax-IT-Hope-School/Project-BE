package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.ai.DayPlanService;
import com.ithopeschool.projectbe.domain.call.dto.CallFinishRequest;
import com.ithopeschool.projectbe.domain.call.dto.CallFinishRequest.Line;
import com.ithopeschool.projectbe.domain.call.dto.CallSessionRequest;
import com.ithopeschool.projectbe.domain.call.dto.ChatAbout;
import com.ithopeschool.projectbe.domain.call.dto.ChatMessageRequest;
import com.ithopeschool.projectbe.domain.call.dto.ChatReplyResponse;
import com.ithopeschool.projectbe.domain.curriculum.CurriculumContext;
import com.ithopeschool.projectbe.domain.curriculum.CurriculumService;
import com.ithopeschool.projectbe.domain.gpt.GptUsageGuard;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.psychtest.PsychItem;
import com.ithopeschool.projectbe.domain.psychtest.PsychScale;
import com.ithopeschool.projectbe.domain.psychtest.PsychScore;
import com.ithopeschool.projectbe.domain.psychtest.PsychTest;
import com.ithopeschool.projectbe.domain.psychtest.PsychTestRepository;
import com.ithopeschool.projectbe.domain.safety.HelpLine;
import com.ithopeschool.projectbe.domain.safety.SafetyService;
import com.ithopeschool.projectbe.domain.school.SchoolTodayService;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

// Service 단위 테스트: GPT(ChatAiClient) · 대본 만들기(CallService) · DB 를 가짜로 바꿔서 채팅의 흐름만 확인합니다. (이슈 #238)
class ChatServiceTest {

  private static final Long USER_ID = 7L;
  private static final Long CHAT_ID = 12L;
  // 지금은 2026-10-17 오후 6시(한국 시간)
  private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
  private static final String SCRIPT = "채팅 대본";
  private static final CallSessionRequest START = new CallSessionRequest(2, 3, null, null, null);

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final ChatAiClient chatAiClient = mock(ChatAiClient.class);
  private final GptUsageGuard gptUsageGuard = mock(GptUsageGuard.class);
  private final CallService callService = mock(CallService.class);
  private final CallRecordService callRecordService = mock(CallRecordService.class);
  private final CallRecordRepository callRecordRepository = mock(CallRecordRepository.class);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  // 대본을 기억하는 곳은 진짜 코드를 씁니다.
  private final ChatScriptStore chatScriptStore = new ChatScriptStore(clock);
  private final SafetyService safetyService = mock(SafetyService.class);
  private final SchoolTodayService schoolTodayService = mock(SchoolTodayService.class);
  private final PsychTestRepository psychTestRepository = mock(PsychTestRepository.class);
  // 교육과정 · 성취기준 고르기는 가짜입니다. (진짜 고르기는 CurriculumServiceTest · CurriculumSearchTest)
  private final CurriculumService curriculumService = mock(CurriculumService.class);
  // 웹 검색은 진짜 코드를 씁니다. (켜져 있음)
  private final TutorWebSearch tutorWebSearch = new TutorWebSearch(true);
  // 계획 방의 [오늘 쓸 수 있는 시간] 재료는 가짜입니다. 따로 정하지 않으면 null 이라 칸이 붙지 않습니다. (진짜 계산은 DayPlanServiceTest)
  private final DayPlanService dayPlanService = mock(DayPlanService.class);
  private final ChatService service =
      new ChatService(
          currentUserProvider,
          chatAiClient,
          gptUsageGuard,
          callService,
          callRecordService,
          callRecordRepository,
          chatScriptStore,
          safetyService,
          schoolTodayService,
          psychTestRepository,
          curriculumService,
          tutorWebSearch,
          dayPlanService,
          clock);

  @BeforeEach
  void loggedInAndAiOn() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(chatAiClient.isEnabled()).willReturn(true);
    // 성취기준 칸은 따로 정하지 않으면 빈 글자(맞는 줄 없음)입니다.
    given(curriculumService.standardsSection(any(), any())).willReturn("");
    given(callService.prepareChatScript(eq(USER_ID), any(), eq(NOW), eq(TODAY), any()))
        .willReturn(new CallService.Script(SCRIPT, null));
    given(callRecordRepository.save(any()))
        .willAnswer(
            invocation -> {
              CallRecord record = invocation.getArgument(0);
              ReflectionTestUtils.setField(record, "id", CHAT_ID);
              return record;
            });
  }

  private static ChatMessageRequest said(String... studentTexts) {
    List<Line> lines = new ArrayList<>();
    lines.add(new Line("ai", "어제 어땠어?"));
    for (String text : studentTexts) {
      lines.add(new Line("me", text));
    }
    return new ChatMessageRequest(lines);
  }

  // 말 보내기의 지시문은 끝에 직전 AI 답의 [이미 한 말] 칸이 붙습니다. (RepeatCheck.section)
  private static String alreadySaid(String script, ChatMessageRequest request) {
    return script + RepeatCheck.section(request.lines());
  }

  // 열려 있는 내 채팅 하나를 DB 에 둡니다.
  private CallRecord givenOpenChat() {
    CallRecord chat = new CallRecord(USER_ID, TODAY, NOW, null, CallChannel.CHAT);
    ReflectionTestUtils.setField(chat, "id", CHAT_ID);
    given(callRecordRepository.findByIdAndUserId(CHAT_ID, USER_ID)).willReturn(Optional.of(chat));
    chatScriptStore.save(CHAT_ID, USER_ID, SCRIPT);
    return chat;
  }

  private static void assertError(Runnable action, ErrorCode code, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting("errorCode")
        .isEqualTo(code);
  }

  // ---- 채팅 열기 ----

  @Test
  @DisplayName("오늘 학교 정보가 있으면 대본 끝에 [오늘 학교] 칸을 붙여 AI 에 건네고 기억한다")
  void startAddsSchoolLines() {
    List<String> schoolLines = List.of("- 오늘 중식: 흑미밥, 나주곰탕");
    given(schoolTodayService.scriptLines(2, 1)).willReturn(schoolLines);
    String withSchool = SCRIPT + CallScript.schoolSection(schoolLines);
    given(chatAiClient.turn(withSchool, List.of()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("오늘 급식 나주곰탕이네!")));

    ChatReplyResponse response = service.start(START, 2, 1);

    assertThat(response.reply()).isEqualTo("오늘 급식 나주곰탕이네!");
    assertThat(chatScriptStore.find(CHAT_ID, USER_ID)).contains(withSchool);
  }

  @Test
  @DisplayName("성향 검사가 있으면 대본에 [학생 성향] 칸을 붙인다 (시작 저항이 높으면 '5분만')")
  void startAddsPsychSection() {
    Map<PsychItem, Integer> answers = new EnumMap<>(PsychItem.class);
    for (PsychItem item : PsychItem.values()) {
      // 시작 저항 문장만 7점, 나머지는 4점(보통)
      answers.put(item, item.scale() == PsychScale.START_RESISTANCE ? 7 : 4);
    }
    given(psychTestRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
        .willReturn(Optional.of(new PsychTest(USER_ID, NOW, answers)));
    String withPsych = SCRIPT + CallScript.psychSection(PsychScore.scoresOf(answers));
    given(chatAiClient.turn(withPsych, List.of()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("5분만 먼저 해 볼까?")));

    ChatReplyResponse response = service.start(START);

    assertThat(response.reply()).isEqualTo("5분만 먼저 해 볼까?");
    assertThat(withPsych).contains("[학생 성향]").contains("5분만");
  }

  @Test
  @DisplayName("성향 검사 답이 거의 다 같으면 점수 대신 '대화로 하나만 물어봐' 칸을 붙인다")
  void startAddsUnsurePsychSection() {
    Map<PsychItem, Integer> answers = new EnumMap<>(PsychItem.class);
    for (PsychItem item : PsychItem.values()) {
      // 시작 저항이 7 이어도, 35개가 모두 7 이면 믿기 어렵습니다.
      answers.put(item, 7);
    }
    given(psychTestRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
        .willReturn(Optional.of(new PsychTest(USER_ID, NOW, answers)));
    String withUnsure = SCRIPT + CallScript.unsurePsychSection();
    given(chatAiClient.turn(withUnsure, List.of()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("요즘 시작하기 어때?")));

    ChatReplyResponse response = service.start(START);

    assertThat(response.reply()).isEqualTo("요즘 시작하기 어때?");
    assertThat(withUnsure).contains("[학생 성향]").contains("믿기 어려워").doesNotContain("5분만");
  }

  @Test
  @DisplayName("채팅을 열면 통화와 같은 대본(채팅 버전)으로 AI 의 첫 인사를 받고, 채팅 기록을 만든다")
  void startsAChat() {
    given(chatAiClient.turn(SCRIPT, List.of()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("안녕! 어제는 어땠어?")));

    ChatReplyResponse response = service.start(START);

    assertThat(response.chatId()).isEqualTo("12");
    assertThat(response.reply()).isEqualTo("안녕! 어제는 어땠어?");
    assertThat(response.showHelp()).isFalse();
    ArgumentCaptor<CallRecord> saved = ArgumentCaptor.forClass(CallRecord.class);
    verify(callRecordRepository).save(saved.capture());
    assertThat(saved.getValue().getChannel()).isEqualTo(CallChannel.CHAT);
    assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
    // 다음 말부터 같은 대본을 씁니다.
    assertThat(chatScriptStore.find(CHAT_ID, USER_ID)).contains(SCRIPT);
  }

  @Test
  @DisplayName("AI 가 꺼져 있으면 503 이고 채팅 기록을 만들지 않는다")
  void startNeedsAi() {
    given(chatAiClient.isEnabled()).willReturn(false);
    assertError(() -> service.start(START), ErrorCode.SERVICE_UNAVAILABLE, "AI 상담이 아직 켜져 있지 않아요");

    verify(callRecordRepository, never()).save(any());
  }

  @Test
  @DisplayName("인사 없이 열면 AI 를 부르지 않고 호출 기록도 남기지 않으며, 채팅 기록과 대본만 만든다 (채팅 수정 1)")
  void startsWithoutGreeting() {
    ChatReplyResponse response = service.start(START, null, null, false);

    assertThat(response.chatId()).isEqualTo("12");
    assertThat(response.reply()).isNull();
    assertThat(response.showHelp()).isFalse();
    verify(chatAiClient, never()).turn(any(), anyList());
    verify(gptUsageGuard, never()).recordText(any(), any());
    verify(callRecordRepository).save(any());
    // 이어지는 말 보내기는 같은 대본을 씁니다.
    assertThat(chatScriptStore.find(CHAT_ID, USER_ID)).contains(SCRIPT);
  }

  @Test
  @DisplayName("인사 없이 열어도 AI 가 꺼져 있으면 503 이고 채팅 기록을 만들지 않는다")
  void startWithoutGreetingNeedsAi() {
    given(chatAiClient.isEnabled()).willReturn(false);

    assertError(
        () -> service.start(START, null, null, false),
        ErrorCode.SERVICE_UNAVAILABLE,
        "AI 상담이 아직 켜져 있지 않아요");
    verify(callRecordRepository, never()).save(any());
  }

  @Test
  @DisplayName("인사 없이 여는 채팅은 오늘 30번 열었으면 429 이고 채팅 기록을 만들지 않는다 (음성 통화는 세지 않음, 2026-10-22 QA)")
  void startWithoutGreetingHasDailyOpenLimit() {
    List<CallRecord> today = new ArrayList<>();
    for (int i = 0; i < ChatService.MAX_CHAT_OPENS_PER_DAY; i++) {
      today.add(new CallRecord(USER_ID, TODAY, NOW, null, CallChannel.CHAT));
    }
    // 음성 통화 기록은 채팅 여는 횟수에 넣지 않습니다.
    today.add(new CallRecord(USER_ID, TODAY, NOW, null, CallChannel.VOICE));
    given(
            callRecordRepository.findByUserIdAndCallDateGreaterThanEqualOrderByStartedAtDescIdDesc(
                USER_ID, TODAY))
        .willReturn(today);

    assertError(
        () -> service.start(START, null, null, false),
        ErrorCode.TOO_MANY_REQUESTS,
        "오늘 채팅을 너무 많이 열었어요. 내일 다시 이야기해요");
    verify(callRecordRepository, never()).save(any());

    // 29번이면 엽니다.
    today.remove(0);
    assertThat(service.start(START, null, null, false).chatId()).isEqualTo("12");
  }

  @Test
  @DisplayName("AI 의 첫 인사를 못 받으면 503 이고 채팅 기록을 만들지 않는다")
  void startFailsWithoutFirstReply() {
    given(chatAiClient.turn(any(), anyList())).willReturn(Optional.empty());

    assertError(
        () -> service.start(START),
        ErrorCode.SERVICE_UNAVAILABLE,
        "AI 와 연결하지 못했어요. 잠시 후 다시 시도해 주세요");
    verify(callRecordRepository, never()).save(any());
  }

  // ---- 말 보내기 ----

  @Test
  @DisplayName("말을 보내면 지금까지의 대화와 기억해 둔 대본으로 AI 의 다음 말을 받는다")
  void sendsAMessage() {
    givenOpenChat();
    ChatMessageRequest request = said("폰 보다가 늦게 시작했어");
    given(chatAiClient.turn(alreadySaid(SCRIPT, request), request.lines()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("그때 폰은 어디 있었어?")));

    ChatReplyResponse response = service.send(CHAT_ID, request);

    assertThat(response.reply()).isEqualTo("그때 폰은 어디 있었어?");
    assertThat(response.showHelp()).isFalse();
  }

  @Test
  @DisplayName("다른 방 이야기를 같이 보내면 그 한 번의 답에만 [다른 방에서 오늘 한 이야기] 칸을 붙이고, 기억해 둔 대본은 그대로다")
  void sendsWithOtherRooms() {
    givenOpenChat();
    List<String> otherRooms = List.of("상담: 미적분 극한이 헷갈려", "과목 쌤: 곱의 미분법 알려 줘");
    ChatMessageRequest request =
        new ChatMessageRequest(said("오늘 계획 짜 줘").lines(), null, otherRooms);
    String withOtherRooms =
        SCRIPT + OtherRoomTalk.chatSection(otherRooms) + RepeatCheck.section(request.lines());
    given(chatAiClient.turn(withOtherRooms, request.lines()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("미적분 극한부터 넣어 볼까?")));

    ChatReplyResponse response = service.send(CHAT_ID, request);

    assertThat(response.reply()).isEqualTo("미적분 극한부터 넣어 볼까?");
    assertThat(withOtherRooms).contains("[다른 방에서 오늘 한 이야기]").contains("- 상담: 미적분 극한이 헷갈려");
    // 다음 말에는 그때 FE 가 보낸 다른 방 이야기를 새로 붙입니다. 기억해 둔 대본에는 남기지 않습니다.
    assertThat(chatScriptStore.find(CHAT_ID, USER_ID)).contains(SCRIPT);
  }

  @Test
  @DisplayName("학생의 말에 위기 표현이 있으면 GPT 를 부르지 않고 도움 안내로 답하고, 채팅은 끝내지 않는다")
  void crisisKeepsTheChatOpen() {
    CallRecord chat = givenOpenChat();

    ChatReplyResponse response = service.send(CHAT_ID, said("그냥 다 그만두고 죽고 싶어"));

    assertThat(response.showHelp()).isTrue();
    assertThat(response.reply()).isEqualTo(ChatService.CHAT_CRISIS_REPLY);
    assertThat(response.helpLines()).isEqualTo(HelpLine.ALL);
    verify(chatAiClient, never()).reply(any(), anyList());
    verify(gptUsageGuard, never()).recordText(any(), any());
    // "위기 표현이 감지됨" 표시는 남기지만 채팅은 끝내지 않습니다. 그 뒤 7일 동안 통화·채팅이 위로하는 쪽이 됩니다.
    assertThat(chat.isCrisisDetected()).isTrue();
    assertThat(chat.isFinished()).isFalse();
    verify(callRecordRepository).save(chat);
    // 남은 대화에서 공부 이야기를 꺼내지 않게 대본에 한 칸이 붙습니다.
    assertThat(chatScriptStore.find(CHAT_ID, USER_ID))
        .contains(SCRIPT + ChatService.CRISIS_FOLLOW_UP);
    // 통화 마무리와 같은 보호 조치(관리 한도 0 · 회복 메시지)를 합니다.
    verify(safetyService).activate(USER_ID);
  }

  @Test
  @DisplayName("안내한 뒤에는 앞의 말로 다시 걸리지 않고 AI 와 이어서 이야기한다 (보호 조치는 한 번만)")
  void afterCrisisTheChatContinues() {
    CallRecord chat = givenOpenChat();
    service.send(CHAT_ID, said("죽고 싶어"));

    ChatMessageRequest next = said("죽고 싶어", "그냥 오늘 너무 힘들었어");
    given(chatAiClient.turn(alreadySaid(SCRIPT + ChatService.CRISIS_FOLLOW_UP, next), next.lines()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("많이 힘들었구나. 무슨 일이 있었어?")));
    ChatReplyResponse response = service.send(CHAT_ID, next);

    assertThat(response.showHelp()).isFalse();
    assertThat(response.reply()).isEqualTo("많이 힘들었구나. 무슨 일이 있었어?");
    assertThat(chat.isFinished()).isFalse();

    // 마지막 말에 또 위기 표현이 있으면 다시 안내하지만, 보호 조치는 다시 하지 않습니다.
    assertThat(service.send(CHAT_ID, said("죽고 싶어", "괜찮아", "사라지고 싶어")).showHelp()).isTrue();
    verify(safetyService, times(1)).activate(USER_ID);
  }

  @Test
  @DisplayName("앞에서 한 말에 위기 표현이 있어도 잡는다 (마지막 줄만 보지 않음)")
  void crisisInAnEarlierLineIsCaught() {
    givenOpenChat();

    ChatReplyResponse response = service.send(CHAT_ID, said("사라지고 싶었어", "그래도 오늘은 했어"));

    assertThat(response.showHelp()).isTrue();
  }

  @Test
  @DisplayName("AI 의 답에 깎아내리는 말이 섞이면 정해 둔 말로 바꾼다")
  void bannedExpressionIsReplaced() {
    givenOpenChat();
    given(chatAiClient.turn(any(), anyList()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("이것도 못 하다니 한심해.")));

    assertThat(service.send(CHAT_ID, said("못 했어")).reply()).isEqualTo(ChatService.SAFE_REPLY);
  }

  @Test
  @DisplayName("마지막 줄이 학생의 말이 아니면 400")
  void lastLineMustBeTheStudent() {
    givenOpenChat();

    assertError(
        () -> service.send(CHAT_ID, new ChatMessageRequest(List.of(new Line("ai", "안녕")))),
        ErrorCode.VALIDATION_ERROR,
        "마지막 줄은 학생(me)의 말이어야 해요");
  }

  @Test
  @DisplayName("내 채팅이 아니거나 음성 통화 번호면 404, 이미 끝난 채팅이면 409")
  void onlyMyOpenChats() {
    given(callRecordRepository.findByIdAndUserId(CHAT_ID, USER_ID)).willReturn(Optional.empty());
    assertError(() -> service.send(CHAT_ID, said("응")), ErrorCode.NOT_FOUND, "채팅을 찾을 수 없어요");

    CallRecord voice = new CallRecord(USER_ID, TODAY, NOW, null);
    given(callRecordRepository.findByIdAndUserId(CHAT_ID, USER_ID)).willReturn(Optional.of(voice));
    assertError(() -> service.send(CHAT_ID, said("응")), ErrorCode.NOT_FOUND, "채팅을 찾을 수 없어요");

    CallRecord ended = new CallRecord(USER_ID, TODAY, NOW, null, CallChannel.CHAT);
    ended.finishWithCrisis(NOW);
    given(callRecordRepository.findByIdAndUserId(CHAT_ID, USER_ID)).willReturn(Optional.of(ended));
    assertError(() -> service.send(CHAT_ID, said("응")), ErrorCode.CONFLICT, "이미 끝난 채팅이에요");
  }

  @Test
  @DisplayName("서버가 다시 켜져 대본을 잊었으면 409 (새로 시작)")
  void forgottenScriptMeansStartAgain() {
    givenOpenChat();
    chatScriptStore.remove(CHAT_ID);

    assertError(
        () -> service.send(CHAT_ID, said("응")), ErrorCode.CONFLICT, "채팅이 오래되어 끝났어요. 새로 시작해 주세요");
  }

  // ---- 끝내기 ----

  @Test
  @DisplayName("끝내기는 통화 마무리와 똑같이 정리한다 (CallRecordService.finish)")
  void finishUsesTheCallFinish() {
    givenOpenChat();
    CallFinishRequest request = new CallFinishRequest(said("폰 때문이었어").lines());

    service.finish(CHAT_ID, request);

    verify(callRecordService).finish(CHAT_ID, request);
    assertThat(chatScriptStore.find(CHAT_ID, USER_ID)).isEmpty();
  }

  @Test
  @DisplayName("AI 가 준 빠른 답 · 행동을 검사해서 넘긴다 (빈 것 · 20자 넘는 것 · 겹치는 것은 빼고 3개까지)")
  void sendsCleanedButtons() {
    givenOpenChat();
    ChatMessageRequest request = said("오늘 좀 피곤해");
    given(chatAiClient.turn(alreadySaid(SCRIPT, request), request.lines()))
        .willReturn(
            Optional.of(
                new ChatAiClient.ChatTurn(
                    "그럼 10분만 해 볼까?",
                    List.of(
                        "좋아, 10분",
                        " ",
                        "좋아, 10분",
                        "이건 스무 글자가 넘는 아주 아주 긴 빠른 답이야",
                        "오늘은 쉴래",
                        "그래도 다 할래",
                        "네 번째"),
                    new ChatReplyResponse.Action("shrink_today", 30, null))));

    ChatReplyResponse response = service.send(CHAT_ID, request);

    assertThat(response.reply()).isEqualTo("그럼 10분만 해 볼까?");
    assertThat(response.quickReplies()).containsExactly("좋아, 10분", "오늘은 쉴래", "그래도 다 할래");
    assertThat(response.action()).isEqualTo(new ChatReplyResponse.Action("shrink_today", 30, null));
  }

  @Test
  @DisplayName("행동 검사: 줄이기는 5~60분만, 카드는 제목이 있어야 하고 분이 이상하면 25분, 모르는 종류는 버림")
  void cleansActions() {
    assertThat(ChatService.cleanAction(new ChatReplyResponse.Action("start_timer", 99, "x"), TODAY))
        .isEqualTo(new ChatReplyResponse.Action("start_timer", null, null));
    assertThat(
            ChatService.cleanAction(new ChatReplyResponse.Action("shrink_today", 90, null), TODAY))
        .isNull();
    assertThat(
            ChatService.cleanAction(new ChatReplyResponse.Action("create_card", 0, "영어 단어"), TODAY))
        .isEqualTo(new ChatReplyResponse.Action("create_card", 50, "영어 단어"));
    // 30분보다 짧은 공부 카드는 만들지 않는다 (StudyBlock) · 120분이 끝
    assertThat(
            ChatService.cleanAction(
                new ChatReplyResponse.Action("create_card", 10, "영어 단어"), TODAY))
        .isEqualTo(new ChatReplyResponse.Action("create_card", 30, "영어 단어"));
    assertThat(
            ChatService.cleanAction(
                new ChatReplyResponse.Action("create_card", 200, "영어 단어"), TODAY))
        .isEqualTo(new ChatReplyResponse.Action("create_card", 120, "영어 단어"));
    assertThat(
            ChatService.cleanAction(new ChatReplyResponse.Action("shrink_today", 10, null), TODAY))
        .isNull();
    assertThat(ChatService.cleanAction(new ChatReplyResponse.Action("create_card", 30, " "), TODAY))
        .isNull();
    assertThat(
            ChatService.cleanAction(new ChatReplyResponse.Action("delete_all", null, null), TODAY))
        .isNull();
    assertThat(ChatService.cleanAction(null, TODAY)).isNull();
  }

  @Test
  @DisplayName("행동 검사: 긴 계획은 목표가 있어야 하고 마감 날은 내일 ~ 30일 뒤만 (밖이면 date 만 비움)")
  void cleansMakePlan() {
    assertThat(
            ChatService.cleanAction(
                new ChatReplyResponse.Action("make_plan", 40, " 중간고사 미적분 ", "2026-11-02"), TODAY))
        .isEqualTo(new ChatReplyResponse.Action("make_plan", null, "중간고사 미적분", "2026-11-02"));
    // 내일과 30일 뒤는 됨
    assertThat(
            ChatService.cleanAction(
                    new ChatReplyResponse.Action("make_plan", null, "수행평가", "2026-10-18"), TODAY)
                .date())
        .isEqualTo("2026-10-18");
    assertThat(
            ChatService.cleanAction(
                    new ChatReplyResponse.Action("make_plan", null, "수행평가", "2026-11-16"), TODAY)
                .date())
        .isEqualTo("2026-11-16");
    // 오늘 · 31일 뒤 · 모양이 틀림 · 없음 → 버튼은 두고 date 만 null
    for (String date : new String[] {"2026-10-17", "2026-11-17", "11월 2일", null}) {
      assertThat(
              ChatService.cleanAction(
                  new ChatReplyResponse.Action("make_plan", null, "수행평가", date), TODAY))
          .isEqualTo(new ChatReplyResponse.Action("make_plan", null, "수행평가", null));
    }
    // 목표가 비었거나 30자를 넘으면 버튼 없음
    assertThat(
            ChatService.cleanAction(
                new ChatReplyResponse.Action("make_plan", null, " ", "2026-11-02"), TODAY))
        .isNull();
    assertThat(
            ChatService.cleanAction(
                new ChatReplyResponse.Action("make_plan", null, "가".repeat(31), "2026-11-02"),
                TODAY))
        .isNull();
  }

  @Test
  @DisplayName("행동 검사: 확인 문제는 주제 1~30자, 분 · 날짜는 버림")
  void cleansStartQuiz() {
    assertThat(
            ChatService.cleanAction(
                new ChatReplyResponse.Action("start_quiz", 30, " 수열의 극한 ", "2026-11-02"), TODAY))
        .isEqualTo(new ChatReplyResponse.Action("start_quiz", null, "수열의 극한"));
    assertThat(
            ChatService.cleanAction(
                new ChatReplyResponse.Action("start_quiz", null, "가".repeat(30)), TODAY))
        .isNotNull();
    assertThat(
            ChatService.cleanAction(
                new ChatReplyResponse.Action("start_quiz", null, "가".repeat(31)), TODAY))
        .isNull();
    assertThat(
            ChatService.cleanAction(new ChatReplyResponse.Action("start_quiz", null, null), TODAY))
        .isNull();
  }

  @Test
  @DisplayName("행동 검사: 하루 계획(day_plan)은 총 분 20~600 · 먼저 할 것 30자 · 시작 시각 HH:mm, 틀린 칸만 null (#431)")
  void cleansDayPlan() {
    // 맞는 값은 그대로, 먼저 할 것은 앞뒤 공백 · 줄바꿈을 정리, date 는 늘 null
    assertThat(
            ChatService.cleanAction(
                new ChatReplyResponse.Action("day_plan", 120, " 수학\n먼저 ", "2026-11-02", "21:00"),
                TODAY))
        .isEqualTo(new ChatReplyResponse.Action("day_plan", 120, "수학 먼저", null, "21:00"));
    // 범위 밖 분 · 모양이 틀린 시각 · 빈 제목은 그 칸만 null 이고, 버튼은 남는다
    assertThat(
            ChatService.cleanAction(
                new ChatReplyResponse.Action("day_plan", 900, " ", null, "9시"), TODAY))
        .isEqualTo(new ChatReplyResponse.Action("day_plan", null, null, null, null));
    assertThat(
            ChatService.cleanAction(
                    new ChatReplyResponse.Action("day_plan", 19, null, null, "25:00"), TODAY)
                .minutes())
        .isNull();
    assertThat(
            ChatService.cleanAction(
                    new ChatReplyResponse.Action("day_plan", 20, null, null, null), TODAY)
                .minutes())
        .isEqualTo(20);
    // 먼저 할 것: 30자가 넘으면 30자로 자르고, 깎아내리는 말이 있으면 null
    assertThat(
            ChatService.cleanAction(
                    new ChatReplyResponse.Action("day_plan", null, "가".repeat(40), null, null),
                    TODAY)
                .title())
        .isEqualTo("가".repeat(30));
    assertThat(
            ChatService.cleanAction(
                    new ChatReplyResponse.Action("day_plan", null, "한심해도 수학", null, null), TODAY)
                .title())
        .isNull();
  }

  @Test
  @DisplayName("시작 시각은 00:00 ~ 23:59 의 HH:mm 만 받는다")
  void startTimeShape() {
    assertThat(ChatService.startTimeOf(" 09:30 ")).isEqualTo("09:30");
    assertThat(ChatService.startTimeOf("23:59")).isEqualTo("23:59");
    assertThat(ChatService.startTimeOf("9:30")).isNull();
    assertThat(ChatService.startTimeOf("24:00")).isNull();
    assertThat(ChatService.startTimeOf("21:60")).isNull();
    assertThat(ChatService.startTimeOf(null)).isNull();
  }

  @Test
  @DisplayName("day_plan 이 아닌 행동은 startTime 을 버린다")
  void otherActionsDropStartTime() {
    assertThat(
            ChatService.cleanAction(
                new ChatReplyResponse.Action("start_timer", null, null, null, "21:00"), TODAY))
        .isEqualTo(new ChatReplyResponse.Action("start_timer", null, null, null, null));
    assertThat(
            ChatService.cleanAction(
                new ChatReplyResponse.Action("make_plan", null, "중간고사", "2026-11-02", "21:00"),
                TODAY))
        .isEqualTo(new ChatReplyResponse.Action("make_plan", null, "중간고사", "2026-11-02", null));
  }

  // ---- 코치랑 이야기하기 (주제가 있는 채팅, 2026-10-18) ----

  private static CallSessionRequest startAbout(String kind, String... facts) {
    return new CallSessionRequest(
        2, 3, null, null, null, new ChatAbout(kind, List.of(facts)), null);
  }

  private static CallSessionRequest startIn(String room, ChatAbout about) {
    return new CallSessionRequest(2, 3, null, null, null, about, room);
  }

  @Test
  @DisplayName("주제와 함께 열면 대본 끝에 [이번 대화의 주제] 칸을 붙이고, 주제를 기억하고, 그 주제의 이유 값만 돌려준다")
  void startWithTopic() {
    List<String> facts = List.of("카드: 미적분 3단원 40분", "집중도 82%, 폰 3번 들었음");
    String withTopic = SCRIPT + CallScript.topicSection(ChatTopic.SESSION_REVIEW, facts);
    given(chatAiClient.turn(withTopic, List.of()))
        .willReturn(
            Optional.of(
                new ChatAiClient.ChatTurn(
                    "40분 했네! 폰을 3번 들었던데 어땠어?",
                    List.of("폰이 신경 쓰였어"),
                    null,
                    List.of("phone", "sick", "phone"))));

    ChatReplyResponse response =
        service.start(startAbout("SESSION_REVIEW", facts.get(0), facts.get(1)));

    assertThat(response.reply()).isEqualTo("40분 했네! 폰을 3번 들었던데 어땠어?");
    // "sick" 은 후기의 이유 값이 아니라서, 두 번째 "phone" 은 겹쳐서 빠집니다.
    assertThat(response.tags()).containsExactly("phone");
    assertThat(chatScriptStore.find(CHAT_ID, USER_ID)).contains(withTopic);
    assertThat(chatScriptStore.findTopic(CHAT_ID, USER_ID)).contains(ChatTopic.SESSION_REVIEW);
  }

  @Test
  @DisplayName("주제로 연 채팅은 말을 주고받을 때도 그 주제의 이유 값만 돌려준다")
  void sendKeepsTopicTags() {
    givenOpenChat();
    chatScriptStore.save(CHAT_ID, USER_ID, SCRIPT, ChatTopic.CARD_FAIL);
    ChatMessageRequest request = said("어제 미루다가 그냥 잤어");
    given(chatAiClient.turn(alreadySaid(SCRIPT, request), request.lines()))
        .willReturn(
            Optional.of(
                new ChatAiClient.ChatTurn(
                    "미루다 보니 늦어졌구나. 이유: 미루다 보니 · 피곤 — 맞아?",
                    List.of(),
                    null,
                    List.of("procrastinated", "stuck", "tired", "phone", "sick"))));

    ChatReplyResponse response = service.send(CHAT_ID, request);

    // "stuck" 은 못 한 카드의 이유 값이 아니라서 빠지고, 3개까지만 남습니다.
    assertThat(response.tags()).containsExactly("procrastinated", "tired", "phone");
  }

  @Test
  @DisplayName("주제 없이 연 채팅 · 서버가 주제를 잊은 채팅은 이유 값이 늘 빈 목록이다")
  void noTopicMeansNoTags() {
    givenOpenChat();
    ChatMessageRequest request = said("폰 봤어");
    given(chatAiClient.turn(alreadySaid(SCRIPT, request), request.lines()))
        .willReturn(
            Optional.of(new ChatAiClient.ChatTurn("그랬구나", List.of(), null, List.of("phone"))));

    assertThat(service.send(CHAT_ID, request).tags()).isEmpty();
  }

  @Test
  @DisplayName("주제의 사실 줄에 위기 표현이 있으면 AI 를 부르지 않고 안내로 열고, 보호 조치를 한 번 한다")
  void startWithCrisisInTopic() {
    ChatReplyResponse response = service.start(startAbout("QUIZ_WRONG", "문제: 죽고 싶다는 말의 뜻은?"));

    assertThat(response.showHelp()).isTrue();
    assertThat(response.reply()).isEqualTo(ChatService.CHAT_CRISIS_REPLY);
    assertThat(response.chatId()).isEqualTo(String.valueOf(CHAT_ID));
    verify(chatAiClient, never()).turn(any(), anyList());
    verify(gptUsageGuard, never()).recordText(any(), any());
    verify(safetyService).activate(USER_ID);
    ArgumentCaptor<CallRecord> saved = ArgumentCaptor.forClass(CallRecord.class);
    verify(callRecordRepository).save(saved.capture());
    assertThat(saved.getValue().isCrisisDetected()).isTrue();
    // 이어지는 대화는 주제 없이 "들어 주기만" 하는 대본으로 갑니다.
    assertThat(chatScriptStore.find(CHAT_ID, USER_ID))
        .contains(SCRIPT + ChatService.CRISIS_FOLLOW_UP);
    assertThat(chatScriptStore.findTopic(CHAT_ID, USER_ID)).isEmpty();
  }

  @Test
  @DisplayName("이유 값 검사: 그 주제의 값만, 겹치지 않게, 3개까지. 주제가 없거나 이유 값을 안 쓰는 주제면 빈 목록")
  void cleansTags() {
    assertThat(
            ChatService.cleanTags(
                List.of("phone", "PHONE", " tired ", "sick", "phone"), ChatTopic.SESSION_REVIEW))
        .containsExactly("phone", "tired");
    assertThat(ChatService.cleanTags(List.of("phone"), ChatTopic.WEEKLY_REPORT)).isEmpty();
    assertThat(ChatService.cleanTags(List.of("phone"), null)).isEmpty();
    assertThat(ChatService.cleanTags(null, ChatTopic.CARD_FAIL)).isEmpty();
    List<String> withNull = new ArrayList<>();
    withNull.add(null);
    withNull.add("sick");
    assertThat(ChatService.cleanTags(withNull, ChatTopic.CARD_FAIL)).containsExactly("sick");
  }

  @Test
  @DisplayName("AI 의 말에 깎아내리는 말이 있으면 이유 값도 같이 뺀다")
  void bannedReplyDropsTags() {
    ChatAiClient.ChatTurn turn =
        new ChatAiClient.ChatTurn("이것도 못 하다니 한심해.", List.of(), null, List.of("phone"));

    assertThat(ChatService.cleanTurn(turn, TODAY, ChatTopic.SESSION_REVIEW).tags()).isEmpty();
  }

  // ---- 방 3개: 상담 · 계획 · 과목 쌤 (2026-10-18) ----

  @Test
  @DisplayName("과목 쌤 방은 대본 끝에 [이번 방: 과목 쌤] 칸을 붙이고, 학생 성향 칸은 붙이지 않고, 방을 기억한다")
  void startInTutorRoom() {
    Map<PsychItem, Integer> answers = new EnumMap<>(PsychItem.class);
    for (PsychItem item : PsychItem.values()) {
      answers.put(item, item.scale() == PsychScale.START_RESISTANCE ? 7 : 4);
    }
    given(psychTestRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
        .willReturn(Optional.of(new PsychTest(USER_ID, NOW, answers)));
    given(curriculumService.contextOf(USER_ID, null, List.of()))
        .willReturn(Optional.of(CURRICULUM));
    String tutorScript =
        SCRIPT + ChatRoom.TUTOR.section() + CurriculumService.tutorSection(CURRICULUM);
    given(chatAiClient.turnForTutor(tutorScript, List.of(), null, true))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("어떤 문제가 막혔어?")));

    ChatReplyResponse response = service.start(startIn("TUTOR", null));

    assertThat(response.reply()).isEqualTo("어떤 문제가 막혔어?");
    assertThat(chatScriptStore.find(CHAT_ID, USER_ID)).contains(tutorScript);
    assertThat(tutorScript).doesNotContain("[학생 성향]").contains("[교육과정]").contains("[찾아보기]");
    assertThat(chatScriptStore.findRoom(CHAT_ID, USER_ID)).contains(ChatRoom.TUTOR);
    assertThat(chatScriptStore.findCurriculum(CHAT_ID, USER_ID)).contains(CURRICULUM);
    verify(chatAiClient, never()).turn(any(), anyList());
  }

  @Test
  @DisplayName("계획 방에 이번 주 숫자를 주제로 열면 [이번 방] 칸 뒤에 [이번 대화의 주제] 칸이 붙는다")
  void startInPlannerRoomWithWeeklyReport() {
    List<String> facts = List.of("이번 주 순공 6시간 10분, 계획의 72%");
    String script =
        SCRIPT
            + ChatRoom.PLANNER.section()
            + CallScript.topicSection(ChatTopic.WEEKLY_REPORT, facts);
    given(chatAiClient.turnForPlanner(script, List.of(), null))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("이번 주 72% 했네. 다음 주엔 뭘 바꿔 볼까?")));

    ChatReplyResponse response =
        service.start(startIn("PLANNER", new ChatAbout("WEEKLY_REPORT", facts)));

    assertThat(response.reply()).isEqualTo("이번 주 72% 했네. 다음 주엔 뭘 바꿔 볼까?");
    assertThat(chatScriptStore.findRoom(CHAT_ID, USER_ID)).contains(ChatRoom.PLANNER);
    assertThat(chatScriptStore.findTopic(CHAT_ID, USER_ID)).contains(ChatTopic.WEEKLY_REPORT);
  }

  @Test
  @DisplayName("예전 방 이름 GOAL_MATE 로 열어도 계획 방(PLANNER)으로 열리고 계획 방 대본을 쓴다")
  void startWithOldGoalMateNameOpensPlanner() {
    String script = SCRIPT + ChatRoom.PLANNER.section();
    given(chatAiClient.turnForPlanner(script, List.of(), null))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("오늘 할 일부터 말해 줄래?")));

    ChatReplyResponse response = service.start(startIn("GOAL_MATE", null));

    assertThat(response.reply()).isEqualTo("오늘 할 일부터 말해 줄래?");
    assertThat(chatScriptStore.find(CHAT_ID, USER_ID)).contains(script);
    assertThat(chatScriptStore.findRoom(CHAT_ID, USER_ID)).contains(ChatRoom.PLANNER);
  }

  @Test
  @DisplayName("계획 방은 방을 대본 만들기에 넘기고, 계획 방의 답 모양으로 받아 day_plan 버튼을 돌려준다 (#431)")
  void plannerRoomUsesPlannerTurn() {
    String script = SCRIPT + ChatRoom.PLANNER.section();
    ChatReplyResponse.Action dayPlan =
        new ChatReplyResponse.Action("day_plan", 120, "수학 먼저", null, "21:00");
    given(chatAiClient.turnForPlanner(script, List.of(), null))
        .willReturn(Optional.of(new ChatAiClient.ChatTurn("이렇게 짜 볼게.", List.of(), dayPlan)));

    ChatReplyResponse response = service.start(startIn("PLANNER", null));

    assertThat(response.action()).isEqualTo(dayPlan);
    verify(callService)
        .prepareChatScript(eq(USER_ID), any(), eq(NOW), eq(TODAY), eq(ChatRoom.PLANNER));
    verify(chatAiClient, never()).turn(any(), anyList());
  }

  // 계획 방의 [오늘 쓸 수 있는 시간] 재료 (고정 일정 학원 하나, 빈 시간 하나, 카드 없음)
  private static final DayPlanService.TodayTime TODAY_TIME =
      new DayPlanService.TodayTime(
          LocalTime.of(7, 0),
          LocalTime.of(23, 0),
          120,
          List.of(new DayPlanService.TodayTime.Slot("수학 학원", "18:00", "21:00", 180)),
          null,
          List.of(),
          List.of(new DayPlanService.TodayTime.Slot(null, "21:10", "23:00", 110)));

  @Test
  @DisplayName("계획 방을 열면 [오늘 쓸 수 있는 시간] 칸을 첫 답에만 붙이고, 기억해 두는 대본에는 넣지 않는다 (#431)")
  void plannerStartAddsTodayTimeOnlyToTheAnswer() {
    given(dayPlanService.todayTime()).willReturn(TODAY_TIME);
    String stored = SCRIPT + ChatRoom.PLANNER.section();
    String withTime = stored + PlannerTime.section(TODAY_TIME);
    given(chatAiClient.turnForPlanner(withTime, List.of(), null))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("21:10 부터 수학 50분 어때?")));

    ChatReplyResponse response = service.start(startIn("PLANNER", null));

    assertThat(response.reply()).isEqualTo("21:10 부터 수학 50분 어때?");
    assertThat(withTime).contains("[오늘 쓸 수 있는 시간]", "「수학 학원」 18:00 ~ 21:00", "21:10 ~ 23:00");
    assertThat(chatScriptStore.find(CHAT_ID, USER_ID)).contains(stored);
  }

  @Test
  @DisplayName("다른 방은 [오늘 쓸 수 있는 시간] 칸을 만들지 않는다")
  void otherRoomsDoNotLoadTodayTime() {
    given(chatAiClient.turn(any(), anyList()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("안녕")));

    service.start(startIn("COACH", null));

    verify(dayPlanService, never()).todayTime();
  }

  @Test
  @DisplayName("계획 방에 말을 보내면 [오늘 쓸 수 있는 시간] 을 새로 만들어 그 답에만 붙인다 (방금 만든 카드도 보이게)")
  void plannerSendAddsFreshTodayTime() {
    givenOpenChat();
    chatScriptStore.save(CHAT_ID, USER_ID, SCRIPT, null, ChatRoom.PLANNER);
    given(dayPlanService.todayTime()).willReturn(TODAY_TIME);
    given(chatAiClient.turnForPlanner(any(), anyList(), any()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("좋아, 이렇게 짜 볼게.")));
    ChatMessageRequest request = said("2시간만, 9시부터 수학 먼저");

    service.send(CHAT_ID, request);

    ArgumentCaptor<String> instructions = ArgumentCaptor.forClass(String.class);
    verify(chatAiClient).turnForPlanner(instructions.capture(), eq(request.lines()), eq(null));
    assertThat(instructions.getValue())
        .isEqualTo(alreadySaid(SCRIPT + PlannerTime.section(TODAY_TIME), request));
    assertThat(chatScriptStore.find(CHAT_ID, USER_ID)).contains(SCRIPT);
  }

  @Test
  @DisplayName("위기 표현이 나온 계획 방 채팅에는 [오늘 쓸 수 있는 시간] 을 붙이지 않는다 (들어 주기만)")
  void crisisPlannerChatSkipsTodayTime() {
    CallRecord chat = givenOpenChat();
    chat.markCrisis();
    chatScriptStore.save(CHAT_ID, USER_ID, SCRIPT, null, ChatRoom.PLANNER);
    given(chatAiClient.turnForPlanner(any(), anyList(), any()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("응, 듣고 있어.")));

    service.send(CHAT_ID, said("그냥 얘기하고 싶어"));

    verify(dayPlanService, never()).todayTime();
  }

  @Test
  @DisplayName("방을 고르지 않으면 코치 방이고 대본은 지금과 같다 (주제와 방이 안 맞아도 그대로 연다)")
  void startWithoutRoomIsCoach() {
    List<String> facts = List.of("문제: lim 1/n / 학생 답: 1 / 정답: 0");
    String script = SCRIPT + CallScript.topicSection(ChatTopic.QUIZ_WRONG, facts);
    given(chatAiClient.turn(script, List.of()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("왜 1 이라고 생각했어?")));

    service.start(startIn(null, new ChatAbout("QUIZ_WRONG", facts)));

    assertThat(chatScriptStore.find(CHAT_ID, USER_ID)).contains(script);
    assertThat(chatScriptStore.findRoom(CHAT_ID, USER_ID)).contains(ChatRoom.COACH);
  }

  // ---- 사진 질문 (2026-10-18) ----

  // 진짜로 읽을 수 있는 아주 작은 사진 글자 (내용은 상관없고, base64 로 풀리기만 하면 됩니다)
  private static final String IMAGE =
      "data:image/jpeg;base64,"
          + Base64.getEncoder().encodeToString("사진".getBytes(StandardCharsets.UTF_8));

  @Test
  @DisplayName("사진이 붙은 말은 그 한 번만 사진과 함께 보내고, 과목 쌤 방의 사진 규칙을 잠깐 붙인다 (기억한 대본은 그대로)")
  void sendWithImageInTutorRoom() {
    givenOpenChat();
    chatScriptStore.save(CHAT_ID, USER_ID, SCRIPT, null, ChatRoom.TUTOR);
    ChatMessageRequest request = new ChatMessageRequest(said("이 문제 어디서 틀렸어?").lines(), IMAGE);
    given(
            chatAiClient.turnForTutor(
                alreadySaid(SCRIPT + ChatRoom.TUTOR.imageRule(), request),
                request.lines(),
                IMAGE,
                true))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("3번 문제 맞지? 어디까지 풀었어?")));

    ChatReplyResponse response = service.send(CHAT_ID, request);

    assertThat(response.reply()).isEqualTo("3번 문제 맞지? 어디까지 풀었어?");
    verify(chatAiClient, never()).turn(any(), anyList());
    verify(chatAiClient, never()).turnWithImage(any(), anyList(), any());
    assertThat(chatScriptStore.find(CHAT_ID, USER_ID)).contains(SCRIPT);
  }

  @Test
  @DisplayName("방을 잊은 채팅에 사진이 오면 코치 방의 사진 규칙을 쓴다")
  void sendWithImageWhenRoomIsLost() {
    givenOpenChat();
    ChatMessageRequest request = new ChatMessageRequest(said("이거 내 계획표야").lines(), IMAGE);
    given(
            chatAiClient.turnWithImage(
                alreadySaid(SCRIPT + ChatRoom.COACH.imageRule(), request), request.lines(), IMAGE))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("계획표 봤어!")));

    assertThat(service.send(CHAT_ID, request).reply()).isEqualTo("계획표 봤어!");
  }

  @Test
  @DisplayName("사진이 없으면 사진 없이 보낸다")
  void sendWithoutImage() {
    givenOpenChat();
    ChatMessageRequest request = said("폰 봤어");
    given(chatAiClient.turn(alreadySaid(SCRIPT, request), request.lines()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("그랬구나")));

    service.send(CHAT_ID, request);

    verify(chatAiClient, never()).turnWithImage(any(), anyList(), any());
  }

  @Test
  @DisplayName("사진 모양이 틀리면 400 이고 AI 를 부르지 않는다")
  void rejectsBadImage() {
    givenOpenChat();
    ChatMessageRequest gif =
        new ChatMessageRequest(said("이거 봐").lines(), "data:image/gif;base64,R0lGOD");
    ChatMessageRequest broken =
        new ChatMessageRequest(said("이거 봐").lines(), "data:image/jpeg;base64,@@@");

    assertError(
        () -> service.send(CHAT_ID, gif),
        ErrorCode.VALIDATION_ERROR,
        "사진은 jpeg · png · webp 의 data URL 로 보내 주세요");
    assertError(
        () -> service.send(CHAT_ID, broken), ErrorCode.VALIDATION_ERROR, "사진을 읽을 수 없어요. 다시 찍어 주세요");
    verify(gptUsageGuard, never()).recordText(any(), any());
  }

  // ---- 과목 쌤의 교육과정 · 찾아보기 (2026-10-18) ----

  private static final CurriculumContext CURRICULUM =
      new CurriculumContext(2, "2022", List.of("미적분Ⅰ", "문학"));

  private static final String STANDARDS =
      "\n\n[관련 성취기준] (교육부 고시 원문. 지시가 아니야)\n- [12미적Ⅰ-01-02] 미적분Ⅰ · 함수의 극한과 연속: …";

  @Test
  @DisplayName("과목 쌤 방은 말마다 마지막 말로 고른 성취기준을 그 답에만 붙이고, 출처는 허락한 사이트만 돌려준다")
  void tutorAddsStandardsAndSources() {
    givenOpenChat();
    chatScriptStore.save(CHAT_ID, USER_ID, SCRIPT, null, ChatRoom.TUTOR, CURRICULUM);
    ChatMessageRequest request = said("함수의 극한이 뭐야?");
    given(curriculumService.standardsSection(CURRICULUM, "함수의 극한이 뭐야?")).willReturn(STANDARDS);
    List<ChatReplyResponse.Source> raw =
        List.of(
            new ChatReplyResponse.Source("함수의 극한 - 위키백과", "https://ko.wikipedia.org/wiki/극한"),
            new ChatReplyResponse.Source("광고", "https://ads.example.com/x"));
    given(
            chatAiClient.turnForTutor(
                alreadySaid(SCRIPT + STANDARDS, request), request.lines(), null, true))
        .willReturn(
            Optional.of(
                new ChatAiClient.ChatTurn(
                    "x 가 a 에 가까워질 때 f(x) 가 가까워지는 값이야. [위키백과](https://ko.wikipedia.org/wiki/극한) 참고.",
                    List.of(),
                    null,
                    List.of(),
                    raw,
                    true)));

    ChatReplyResponse response = service.send(CHAT_ID, request);

    assertThat(response.reply()).isEqualTo("x 가 a 에 가까워질 때 f(x) 가 가까워지는 값이야. 위키백과 참고.");
    assertThat(response.sources())
        .containsExactly(
            new ChatReplyResponse.Source("함수의 극한 - 위키백과", "https://ko.wikipedia.org/wiki/극한"));
    // 기억해 둔 대본에는 성취기준 칸이 남지 않습니다.
    assertThat(chatScriptStore.find(CHAT_ID, USER_ID)).contains(SCRIPT);
  }

  @Test
  @DisplayName("검색이 켜져 있으면 몇 번을 검색해도 매번 검색 도구를 붙인다 (하루 한도 없음)")
  void tutorSearchHasNoLimit() {
    givenOpenChat();
    chatScriptStore.save(CHAT_ID, USER_ID, SCRIPT, null, ChatRoom.TUTOR, CURRICULUM);
    ChatMessageRequest request = said("최신 수능 일정 알려 줘");
    ChatAiClient.ChatTurn searched =
        new ChatAiClient.ChatTurn("찾아봤어.", List.of(), null, List.of(), List.of(), true);
    given(chatAiClient.turnForTutor(alreadySaid(SCRIPT, request), request.lines(), null, true))
        .willReturn(Optional.of(searched));

    for (int i = 0; i < 6; i++) {
      service.send(CHAT_ID, request);
    }

    verify(chatAiClient, times(6))
        .turnForTutor(alreadySaid(SCRIPT, request), request.lines(), null, true);
    verify(chatAiClient, never())
        .turnForTutor(alreadySaid(SCRIPT, request), request.lines(), null, false);
  }

  @Test
  @DisplayName("과목 쌤 방을 틀린 문제 주제로 열면 그 사실 줄로 성취기준을 골라 첫 답에 붙인다")
  void tutorStartWithQuizWrongUsesFacts() {
    List<String> facts = List.of("문제: lim (x→1) (x^2-1)/(x-1) 의 값은?", "학생 답: 0 / 정답: 2");
    given(curriculumService.contextOf(USER_ID, null, List.of()))
        .willReturn(Optional.of(CURRICULUM));
    given(curriculumService.standardsSection(CURRICULUM, String.join(" ", facts)))
        .willReturn(STANDARDS);
    String script =
        SCRIPT
            + ChatRoom.TUTOR.section()
            + CurriculumService.tutorSection(CURRICULUM)
            + CallScript.topicSection(ChatTopic.QUIZ_WRONG, facts);
    given(chatAiClient.turnForTutor(script + STANDARDS, List.of(), null, true))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("왜 0 이라고 생각했어?")));

    ChatReplyResponse response =
        service.start(startIn("TUTOR", new ChatAbout("QUIZ_WRONG", facts)));

    assertThat(response.reply()).isEqualTo("왜 0 이라고 생각했어?");
    assertThat(chatScriptStore.find(CHAT_ID, USER_ID)).contains(script);
  }

  // ---- 같은 말 되풀이 막기 (채팅 수정 3) ----

  // 코치가 "오늘은 10분만 해도 충분해." 로 답한 뒤 학생이 다른 이야기를 한 대화
  private static final ChatMessageRequest AFTER_TEN_MINUTES =
      new ChatMessageRequest(
          List.of(
              new Line("ai", "어제 어땠어?"),
              new Line("me", "너무 피곤했어"),
              new Line("ai", "오늘은 10분만 해도 충분해. 피곤하면 짧게 하자."),
              new Line("me", "미적분 때문에 불안해")));

  private static final String SAME_AGAIN = "오늘은 10분만 해도 충분해! 미적분도 천천히 하자.";
  private static final String FRESH = "미적분이 불안하구나. 어느 단원이 제일 걱정돼?";

  @Test
  @DisplayName("말 보내기의 지시문 끝에 직전 AI 답의 첫 문장을 [이미 한 말] 칸으로 붙인다")
  void sendAddsAlreadySaid() {
    givenOpenChat();
    given(chatAiClient.turn(any(), anyList()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly(FRESH)));

    service.send(CHAT_ID, AFTER_TEN_MINUTES);

    ArgumentCaptor<String> instructions = ArgumentCaptor.forClass(String.class);
    verify(chatAiClient).turn(instructions.capture(), eq(AFTER_TEN_MINUTES.lines()));
    assertThat(instructions.getValue())
        .isEqualTo(SCRIPT + RepeatCheck.ALREADY_SAID_SECTION)
        // FE 가 보낸 ai 줄 글자는 지시에 들어가지 않습니다. (입력의 대화 기록에만 있음)
        .doesNotContain("어제 어땠어")
        .doesNotContain("10분만 해도 충분해");
    // 기억해 둔 대본에는 남지 않습니다.
    assertThat(chatScriptStore.find(CHAT_ID, USER_ID)).contains(SCRIPT);
  }

  @Test
  @DisplayName("계획 · 과목 쌤 방에도 [이미 한 말] 칸을 붙인다")
  void allRoomsGetAlreadySaid() {
    givenOpenChat();
    chatScriptStore.save(CHAT_ID, USER_ID, SCRIPT, null, ChatRoom.TUTOR);
    given(chatAiClient.turnForTutor(any(), anyList(), any(), eq(true)))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly(FRESH)));

    service.send(CHAT_ID, AFTER_TEN_MINUTES);

    ArgumentCaptor<String> instructions = ArgumentCaptor.forClass(String.class);
    verify(chatAiClient)
        .turnForTutor(instructions.capture(), eq(AFTER_TEN_MINUTES.lines()), eq(null), eq(true));
    assertThat(instructions.getValue()).contains("[이미 한 말]");

    // 계획 방은 계획 방의 답 모양(turnForPlanner)으로 갑니다. (#431)
    chatScriptStore.save(CHAT_ID, USER_ID, SCRIPT, null, ChatRoom.PLANNER);
    given(chatAiClient.turnForPlanner(any(), anyList(), any()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly(FRESH)));
    service.send(CHAT_ID, AFTER_TEN_MINUTES);
    verify(chatAiClient)
        .turnForPlanner(instructions.capture(), eq(AFTER_TEN_MINUTES.lines()), eq(null));
    assertThat(instructions.getValue()).contains("[이미 한 말]");
    verify(chatAiClient, never()).turn(any(), anyList());
  }

  @Test
  @DisplayName("새 답이 직전 AI 답과 같은 구절이면 호출 기록을 하나 더 남기고 딱 한 번 다시 받는다")
  void repeatAsksOnceMore() {
    givenOpenChat();
    given(chatAiClient.turn(any(), anyList()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly(SAME_AGAIN)))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly(FRESH)));

    ChatReplyResponse response = service.send(CHAT_ID, AFTER_TEN_MINUTES);

    assertThat(response.reply()).isEqualTo(FRESH);
    // 처음 한 번(checkAiReady) + 다시 받기 한 번
    verify(gptUsageGuard, times(2)).recordText(USER_ID, GptUsageKind.CHAT);
    // 다시 받을 때만 첫 답의 첫 문장을 알려 주는 줄이 지시 끝에 붙습니다.
    ArgumentCaptor<String> instructions = ArgumentCaptor.forClass(String.class);
    verify(chatAiClient, times(2)).turn(instructions.capture(), anyList());
    assertThat(instructions.getAllValues())
        .containsExactly(
            SCRIPT + RepeatCheck.ALREADY_SAID_SECTION,
            SCRIPT + RepeatCheck.ALREADY_SAID_SECTION + RepeatCheck.retryHint(SAME_AGAIN));
    assertThat(instructions.getAllValues().get(1)).contains("\"오늘은 10분만 해도 충분해!\"");
  }

  @Test
  @DisplayName("다시 받다가 AI 와 연결하지 못하면 에러 대신 첫 답을 돌려준다 (2026-10-22 QA)")
  void repeatRetryFailureKeepsFirstReply() {
    givenOpenChat();
    given(chatAiClient.turn(any(), anyList()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly(SAME_AGAIN)))
        .willReturn(Optional.empty());

    ChatReplyResponse response = service.send(CHAT_ID, AFTER_TEN_MINUTES);

    assertThat(response.reply()).isEqualTo(SAME_AGAIN);
    verify(chatAiClient, times(2)).turn(any(), anyList());
  }

  @Test
  @DisplayName("첫 답이 정해 둔 말(SAFE_REPLY)로 바뀐 반복이면 다시 받기 줄 없이 다시 받는다")
  void safeReplyRepeatHasNoHint() {
    givenOpenChat();
    ChatMessageRequest request =
        new ChatMessageRequest(
            List.of(new Line("ai", ChatService.SAFE_REPLY), new Line("me", "그냥 하기 싫어")));
    given(chatAiClient.turn(any(), anyList()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("이것도 못 하다니 한심해.")))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly(FRESH)));

    assertThat(service.send(CHAT_ID, request).reply()).isEqualTo(FRESH);
    ArgumentCaptor<String> instructions = ArgumentCaptor.forClass(String.class);
    verify(chatAiClient, times(2)).turn(instructions.capture(), anyList());
    assertThat(instructions.getAllValues().get(1))
        .isEqualTo(SCRIPT + RepeatCheck.ALREADY_SAID_SECTION)
        .doesNotContain("[다시 답하기]");
  }

  @Test
  @DisplayName("반복이 아니면 다시 부르지 않는다")
  void noRepeatAsksOnce() {
    givenOpenChat();
    given(chatAiClient.turn(any(), anyList()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly(FRESH)));

    assertThat(service.send(CHAT_ID, AFTER_TEN_MINUTES).reply()).isEqualTo(FRESH);
    verify(chatAiClient, times(1)).turn(any(), anyList());
    verify(gptUsageGuard, times(1)).recordText(USER_ID, GptUsageKind.CHAT);
  }

  @Test
  @DisplayName("두 번째 답도 반복이면 그대로 쓴다 (세 번째는 부르지 않음)")
  void secondRepeatIsKept() {
    givenOpenChat();
    String sameTwice = "오늘은 10분만 해도 충분해. 진짜야.";
    given(chatAiClient.turn(any(), anyList()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly(SAME_AGAIN)))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly(sameTwice)));

    assertThat(service.send(CHAT_ID, AFTER_TEN_MINUTES).reply()).isEqualTo(sameTwice);
    verify(chatAiClient, times(2)).turn(any(), anyList());
  }

  @Test
  @DisplayName("다시 받은 답도 깎아내리는 말 검사를 거친다")
  void retriedReplyIsStillChecked() {
    givenOpenChat();
    given(chatAiClient.turn(any(), anyList()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly(SAME_AGAIN)))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("이것도 못 하다니 한심해.")));

    assertThat(service.send(CHAT_ID, AFTER_TEN_MINUTES).reply()).isEqualTo(ChatService.SAFE_REPLY);
  }

  @Test
  @DisplayName("위기 표현이 있으면 반복 검사 없이 정해 둔 안내로 답한다 (AI 를 부르지 않음)")
  void crisisComesBeforeRepeatCheck() {
    givenOpenChat();
    ChatMessageRequest request =
        new ChatMessageRequest(
            List.of(new Line("ai", ChatService.CHAT_CRISIS_REPLY), new Line("me", "죽고 싶어")));

    assertThat(service.send(CHAT_ID, request).reply()).isEqualTo(ChatService.CHAT_CRISIS_REPLY);
    verify(chatAiClient, never()).turn(any(), anyList());
  }

  @Test
  @DisplayName("말 안의 링크 표시는 글만 남긴다")
  void withoutLinks() {
    assertThat(ChatService.withoutLinks("[EBS](https://ebs.co.kr/a) 에서 봤어")).isEqualTo("EBS 에서 봤어");
    assertThat(ChatService.withoutLinks("자세한 건 (https://ko.wikipedia.org/wiki/수열) 참고"))
        .isEqualTo("자세한 건  참고");
    assertThat(ChatService.withoutLinks("링크 없는 말")).isEqualTo("링크 없는 말");
  }
}
