package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.bias.BiasQuestion;
import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import com.ithopeschool.projectbe.domain.call.dto.BiasHabitResponse;
import com.ithopeschool.projectbe.domain.call.dto.CallFinishRequest;
import com.ithopeschool.projectbe.domain.call.dto.CallFinishRequest.Line;
import com.ithopeschool.projectbe.domain.call.dto.CallFinishResponse;
import com.ithopeschool.projectbe.domain.call.dto.CallRecordResponse;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.checkin.CheckIn;
import com.ithopeschool.projectbe.domain.checkin.CheckInRepository;
import com.ithopeschool.projectbe.domain.checkin.CheckInSource;
import com.ithopeschool.projectbe.domain.gpt.GptUsageGuard;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.safety.SafetyService;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

// Service 단위 테스트: GPT(CallReviewClient)와 DB(CallRecordRepository)를 가짜로 바꿔서 마무리 로직만 확인합니다.
class CallRecordServiceTest {

  private static final Long USER_ID = 7L;
  private static final Long CALL_ID = 12L;
  private static final Instant NOW = Instant.parse("2026-10-14T11:05:00Z");

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final CallRecordRepository callRecordRepository = mock(CallRecordRepository.class);
  private final CheckInRepository checkInRepository = mock(CheckInRepository.class);
  private final CallReviewClient callReviewClient = mock(CallReviewClient.class);
  // GPT 호출 기록은 가짜로 둡니다.
  private final GptUsageGuard gptUsageGuard = mock(GptUsageGuard.class);

  private final SafetyService safetyService = mock(SafetyService.class);
  private final CallRecordService service =
      new CallRecordService(
          currentUserProvider,
          callRecordRepository,
          checkInRepository,
          callReviewClient,
          gptUsageGuard,
          Clock.fixed(NOW, ZoneOffset.UTC),
          safetyService);

  // 마무리 전의 12번 통화. "미래의 나 믿기" 를 확인하려던 통화입니다.
  private CallRecord call;

  private static final CallFinishRequest TRANSCRIPT =
      new CallFinishRequest(
          List.of(
              new Line("ai", "어제 약속 지켰어?"),
              new Line("me", "아니 폰 보다가 이따가 하면 된다고 생각했어"),
              new Line("ai", "내일의 너는 오늘의 너랑 뭐가 다를 것 같아?"),
              new Line("me", "음 별로 안 다를 것 같아")));

  private static final CallReview.Raw GPT_ANSWER =
      new CallReview.Raw(
          false,
          "폰을 보다가 늦게 시작했다고 함. 오늘은 폰을 서랍에 넣기로 함.",
          "FUTURE_SELF",
          "이따가 하면 된다고 함",
          "내일의 너는 오늘의 너랑 뭐가 다를 것 같아?",
          "phone",
          "학원에서 오면",
          "폰을 서랍에 넣기",
          false,
          3,
          90);

  @BeforeEach
  void setUp() {
    call =
        new CallRecord(
            USER_ID,
            LocalDate.of(2026, 10, 14),
            NOW.minusSeconds(300),
            new BiasQuestion(CognitiveBias.FUTURE_SELF, 0));
    ReflectionTestUtils.setField(call, "id", CALL_ID);

    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(callRecordRepository.findByIdAndUserId(CALL_ID, USER_ID)).willReturn(Optional.of(call));
    given(callReviewClient.isEnabled()).willReturn(true);
    given(callReviewClient.review(anyString(), anyString(), any()))
        .willReturn(Optional.of(GPT_ANSWER));
  }

  private static void assertError(Runnable action, ErrorCode code, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(code);
  }

  @Test
  @DisplayName("GPT 가 정리한 요약·생각 습관·약속을 저장하고, 결론(습관 이름·설명·해 볼 행동 포함)을 돌려준다")
  void savesTheReviewAndReturnsTheConclusion() {
    CallFinishResponse response = service.finish(CALL_ID, TRANSCRIPT);

    // 저장된 값
    assertThat(call.isFinished()).isTrue();
    assertThat(call.getFinishedAt()).isEqualTo(NOW);
    assertThat(call.getSummary()).startsWith("폰을 보다가 늦게 시작했다고 함.");
    assertThat(call.getFoundBias()).isEqualTo(CognitiveBias.FUTURE_SELF);
    assertThat(call.getFoundEvidence()).isEqualTo("이따가 하면 된다고 함");
    assertThat(call.getActualQuestion()).isEqualTo("내일의 너는 오늘의 너랑 뭐가 다를 것 같아?");
    assertThat(call.getMissReason()).isEqualTo(MissReason.PHONE);
    assertThat(call.getPromiseIf()).isEqualTo("학원에서 오면");
    assertThat(call.getPromiseKept()).isFalse();
    assertThat(call.isCrisisDetected()).isFalse();
    verify(callRecordRepository).save(call);

    // 돌려주는 결론
    assertThat(response.callId()).isEqualTo("12");
    assertThat(response.showHelp()).isFalse();
    assertThat(response.bias().key()).isEqualTo("FUTURE_SELF");
    assertThat(response.bias().label()).isEqualTo("미래의 나 믿기");
    assertThat(response.bias().solution()).isEqualTo(CognitiveBias.FUTURE_SELF.getSolution());
    assertThat(response.bias().evidence()).isEqualTo("이따가 하면 된다고 함");
    assertThat(response.missReason()).isEqualTo("phone");
    assertThat(response.mood()).isEqualTo(3);
    assertThat(response.expectedMinutes()).isEqualTo(90);
  }

  @Test
  @DisplayName("질문이 줄어든 통화(주제 하나, #434)는 정리 칸이 덜 채워져도 마무리되고, 빈 칸은 null 로 저장 · 응답한다")
  void finishesACallWithFewerAnswers() {
    // 지난 약속 이야기만 깊게 한 통화. 생각 습관 질문 · 어제 이유 · 기분 · 오늘 몇 분은 나오지 않았습니다.
    CallRecord promiseCall =
        new CallRecord(USER_ID, LocalDate.of(2026, 10, 14), NOW.minusSeconds(300), null);
    ReflectionTestUtils.setField(promiseCall, "id", CALL_ID);
    given(callRecordRepository.findByIdAndUserId(CALL_ID, USER_ID))
        .willReturn(Optional.of(promiseCall));
    given(callReviewClient.review(anyString(), anyString(), any()))
        .willReturn(
            Optional.of(
                new CallReview.Raw(
                    false,
                    "지난 약속을 해 봤다고 함.",
                    null,
                    null,
                    null,
                    null,
                    "학원에서 오면",
                    "폰을 가방에 넣는다",
                    true,
                    null,
                    null)));
    given(checkInRepository.findByUserIdAndCheckInDate(USER_ID, LocalDate.of(2026, 10, 14)))
        .willReturn(Optional.empty());

    CallFinishResponse response =
        service.finish(
            CALL_ID,
            new CallFinishRequest(
                List.of(
                    new Line("ai", "지난번에 학원 끝나면 폰을 가방에 넣기로 했잖아. 해 봤어?"),
                    new Line("me", "응 어제 해 봤어"))));

    assertThat(promiseCall.isFinished()).isTrue();
    assertThat(promiseCall.getSummary()).isEqualTo("지난 약속을 해 봤다고 함.");
    assertThat(promiseCall.getAskedBias()).isNull();
    assertThat(promiseCall.getFoundBias()).isNull();
    assertThat(promiseCall.getActualQuestion()).isNull();
    assertThat(promiseCall.getMissReason()).isNull();
    assertThat(promiseCall.getPromiseKept()).isTrue();
    assertThat(response.bias()).isNull();
    assertThat(response.missReason()).isNull();
    assertThat(response.mood()).isNull();
    assertThat(response.expectedMinutes()).isNull();
    assertThat(response.promiseIf()).isEqualTo("학원에서 오면");
    // 체크인에는 나온 값(약속)만 옮깁니다.
    ArgumentCaptor<CheckIn> saved = ArgumentCaptor.forClass(CheckIn.class);
    verify(checkInRepository).save(saved.capture());
    assertThat(saved.getValue().getPromiseThen()).isEqualTo("폰을 가방에 넣는다");
    assertThat(saved.getValue().getMood()).isNull();
    assertThat(saved.getValue().getMissReason()).isNull();
  }

  @Test
  @DisplayName("GPT 에는 정리 지침·자막·칸 모양을 건넨다")
  void passesThePromptToGpt() {
    service.finish(CALL_ID, TRANSCRIPT);

    verify(callReviewClient)
        .review(
            CallReviewPrompt.instructions(),
            CallReviewPrompt.input(TRANSCRIPT.lines()),
            CallReviewPrompt.schema());
  }

  @Test
  @DisplayName("채팅 중에 이미 위기 표현을 감지해 보호 조치를 했으면, 마무리 때 보호 조치를 다시 하지 않는다")
  void crisisMarkedDuringChatIsNotActivatedTwice() {
    call.markCrisis();
    CallFinishRequest crisis =
        new CallFinishRequest(List.of(new Line("ai", "요즘 어때?"), new Line("me", "그냥 다 사라지고 싶어")));

    CallFinishResponse response = service.finish(CALL_ID, crisis);

    assertThat(response.showHelp()).isTrue();
    assertThat(call.isFinished()).isTrue();
    verify(safetyService, never()).activate(any());
  }

  @Test
  @DisplayName("학생 말에 위기 표현이 있으면 GPT 를 부르지 않고, 내용 없이 '감지됨' 만 남기고 도움 안내를 돌려준다")
  void crisisWordsSkipGpt() {
    CallFinishRequest crisis =
        new CallFinishRequest(List.of(new Line("ai", "요즘 어때?"), new Line("me", "그냥 다 사라지고 싶어")));

    CallFinishResponse response = service.finish(CALL_ID, crisis);

    assertThat(response.showHelp()).isTrue();
    verify(safetyService).activate(USER_ID);
    assertThat(response.summary()).isNull();
    assertThat(response.bias()).isNull();
    assertThat(call.isCrisisDetected()).isTrue();
    assertThat(call.getSummary()).isNull();
    verify(callReviewClient, never()).review(anyString(), anyString(), any());
    verify(callRecordRepository).save(call);
    // 내용을 남기지 않으므로 체크인도 만들지 않습니다
    verifyNoInteractions(checkInRepository);
  }

  @Test
  @DisplayName("AI 가 한 말에 위기 단어가 있는 것은 감지로 보지 않는다 (학생 말만 본다)")
  void aiLinesAreNotCheckedForCrisisWords() {
    CallFinishRequest request =
        new CallFinishRequest(
            List.of(new Line("ai", "자해 같은 생각이 들면 꼭 말해 줘"), new Line("me", "응 괜찮아")));

    assertThat(service.finish(CALL_ID, request).showHelp()).isFalse();
  }

  @Test
  @DisplayName("GPT 가 위기 표현이라고 하면 단어 목록에 없어도 감지로 본다 (놓치는 쪽이 더 위험)")
  void gptCrisisAlsoCounts() {
    given(callReviewClient.review(anyString(), anyString(), any()))
        .willReturn(
            Optional.of(
                new CallReview.Raw(
                    true, "요약", "FUTURE_SELF", "근거", null, null, null, null, null, null, null)));

    CallFinishResponse response = service.finish(CALL_ID, TRANSCRIPT);

    assertThat(response.showHelp()).isTrue();
    verify(safetyService).activate(USER_ID);
    assertThat(call.isCrisisDetected()).isTrue();
    assertThat(call.getFoundBias()).isNull();
  }

  @Test
  @DisplayName("통화에서 나온 기분·이유·예상 순공·약속을 통화한 날의 체크인(source: call)으로 새로 남긴다")
  void createsCheckInFromTheCall() {
    given(checkInRepository.findByUserIdAndCheckInDate(USER_ID, LocalDate.of(2026, 10, 14)))
        .willReturn(Optional.empty());

    service.finish(CALL_ID, TRANSCRIPT);

    ArgumentCaptor<CheckIn> saved = ArgumentCaptor.forClass(CheckIn.class);
    verify(checkInRepository).save(saved.capture());
    CheckIn checkIn = saved.getValue();
    assertThat(checkIn.getUserId()).isEqualTo(USER_ID);
    assertThat(checkIn.getCheckInDate()).isEqualTo(LocalDate.of(2026, 10, 14));
    assertThat(checkIn.getSource()).isEqualTo(CheckInSource.CALL);
    assertThat(checkIn.getMood()).isEqualTo(3);
    assertThat(checkIn.getMissReason()).isEqualTo(MissReason.PHONE);
    assertThat(checkIn.getExpectedMinutes()).isEqualTo(90);
    assertThat(checkIn.getPromiseIf()).isEqualTo("학원에서 오면");
    assertThat(checkIn.getPromiseThen()).isEqualTo("폰을 서랍에 넣기");
  }

  @Test
  @DisplayName("그날 직접 한 체크인이 있으면 학생이 고른 값은 두고 빈 칸만 채워 저장한다")
  void fillsBlanksOfTheExistingCheckIn() {
    CheckIn manual =
        new CheckIn(
            USER_ID,
            LocalDate.of(2026, 10, 14),
            CheckInSource.MANUAL,
            4,
            5,
            null,
            null,
            null,
            null,
            null,
            null);
    given(checkInRepository.findByUserIdAndCheckInDate(USER_ID, LocalDate.of(2026, 10, 14)))
        .willReturn(Optional.of(manual));

    service.finish(CALL_ID, TRANSCRIPT);

    verify(checkInRepository).save(manual);
    assertThat(manual.getSource()).isEqualTo(CheckInSource.MANUAL);
    // 학생이 고른 기분 5 는 통화의 3 으로 바뀌지 않습니다
    assertThat(manual.getMood()).isEqualTo(5);
    assertThat(manual.getMissReason()).isEqualTo(MissReason.PHONE);
    assertThat(manual.getExpectedMinutes()).isEqualTo(90);
  }

  @Test
  @DisplayName("GPT 가 위기 표현이라고 한 통화는 기분·이유가 정리돼 있어도 체크인을 만들지 않는다")
  void gptCrisisMakesNoCheckIn() {
    given(callReviewClient.review(anyString(), anyString(), any()))
        .willReturn(
            Optional.of(
                new CallReview.Raw(
                    true, "요약", null, null, null, "phone", "학원에서 오면", "폰을 서랍에 넣기", null, 1, 60)));

    service.finish(CALL_ID, TRANSCRIPT);

    verifyNoInteractions(checkInRepository);
  }

  @Test
  @DisplayName("학생이 한마디도 안 했으면 GPT 를 부르지 않고 빈 기록으로 마무리한다")
  void silentCallIsFinishedWithoutGpt() {
    CallFinishRequest silent =
        new CallFinishRequest(List.of(new Line("ai", "여보세요?"), new Line("me", "   ")));

    CallFinishResponse response = service.finish(CALL_ID, silent);

    assertThat(call.isFinished()).isTrue();
    assertThat(response.showHelp()).isFalse();
    assertThat(response.summary()).isNull();
    verify(callReviewClient, never()).review(anyString(), anyString(), any());
    // 옮길 값이 없어서 체크인을 만들지 않습니다
    verifyNoInteractions(checkInRepository);
  }

  @Test
  @DisplayName("없는 번호이거나 남의 통화면 404")
  void notMyCall() {
    given(callRecordRepository.findByIdAndUserId(99L, USER_ID)).willReturn(Optional.empty());

    assertError(() -> service.finish(99L, TRANSCRIPT), ErrorCode.NOT_FOUND, "통화 기록을 찾을 수 없어요");
  }

  @Test
  @DisplayName("이미 마무리한 통화를 또 보내면 409 이고, GPT 를 부르지 않는다")
  void alreadyFinished() {
    call.finishWithCrisis(NOW.minusSeconds(10));

    assertError(() -> service.finish(CALL_ID, TRANSCRIPT), ErrorCode.CONFLICT, "이미 마무리한 통화예요");
    verify(callReviewClient, never()).review(anyString(), anyString(), any());
  }

  @Test
  @DisplayName("AI 가 꺼진 서버(내 컴퓨터)면 503 이고, 기록은 마무리하지 않는다 (다시 시도할 수 있게)")
  void unavailableWhenOff() {
    given(callReviewClient.isEnabled()).willReturn(false);

    assertError(
        () -> service.finish(CALL_ID, TRANSCRIPT),
        ErrorCode.SERVICE_UNAVAILABLE,
        "AI 정리가 아직 켜져 있지 않아요");
    assertThat(call.isFinished()).isFalse();
  }

  @Test
  @DisplayName("GPT 가 실패하면 503 이고, 기록은 마무리하지 않는다 (다시 시도할 수 있게)")
  void gptFailureCanBeRetried() {
    given(callReviewClient.review(anyString(), anyString(), any())).willReturn(Optional.empty());

    assertError(
        () -> service.finish(CALL_ID, TRANSCRIPT),
        ErrorCode.SERVICE_UNAVAILABLE,
        "통화를 정리하지 못했어요. 잠시 후 다시 시도해 주세요");
    assertThat(call.isFinished()).isFalse();
    verify(callRecordRepository, never()).save(any());
  }

  // ---- GPT 비용 지키기 (이슈 #33) ----

  @Test
  @DisplayName("마무리하면 이 통화의 시작~끝 시각을 GPT 호출 기록에 남기고, 요약은 '통화 요약' 종류로 기록한다")
  void recordsCallTimeAndReview() {
    service.finish(CALL_ID, TRANSCRIPT);

    // 시작은 5분 전, 끝은 지금
    verify(gptUsageGuard).recordCallFinish(USER_ID, CALL_ID, NOW.minusSeconds(300), NOW);
    verify(gptUsageGuard).recordText(USER_ID, GptUsageKind.CALL_REVIEW);
  }

  @Test
  @DisplayName("위기 표현이 있거나 학생이 말을 안 해서 GPT 를 부르지 않으면, 글 호출 기록을 남기지 않는다")
  void noReviewNoRecord() {
    service.finish(CALL_ID, new CallFinishRequest(List.of(new Line("ai", "안녕, 오늘 어땠어?"))));

    verify(gptUsageGuard, never()).recordText(any(), any());
  }

  @Test
  @DisplayName("로그인하지 않았으면 401 이고, 기록을 찾지도 않는다")
  void requiresLogin() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    assertError(() -> service.finish(CALL_ID, TRANSCRIPT), ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
    verify(callRecordRepository, never()).findByIdAndUserId(any(), any());
  }

  // ---- 내 통화 기록 · 자주 보이는 습관 ----

  // 그날 마무리한 통화 하나. found 가 null 이면 습관이 보이지 않은 통화입니다.
  private static CallRecord finishedCall(
      long id, LocalDate date, CognitiveBias found, String evidence) {
    CallRecord record = new CallRecord(USER_ID, date, NOW, null);
    ReflectionTestUtils.setField(record, "id", id);
    record.finish(NOW, "요약 " + id, found, evidence, null, MissReason.TOO_MUCH, "A", "B", true);
    return record;
  }

  @Test
  @DisplayName("내 통화 기록: 오늘을 넣어 최근 30일, 마무리한 통화만, 위기 표현이 감지된 통화는 빼고 돌려준다")
  void recentCalls() {
    CallRecord crisis = new CallRecord(USER_ID, LocalDate.of(2026, 10, 13), NOW, null);
    crisis.finishWithCrisis(NOW);
    CallRecord unfinished = new CallRecord(USER_ID, LocalDate.of(2026, 10, 14), NOW, null);
    given(
            callRecordRepository.findByUserIdAndCallDateGreaterThanEqualOrderByStartedAtDescIdDesc(
                USER_ID, LocalDate.of(2026, 9, 15)))
        .willReturn(
            List.of(
                unfinished,
                finishedCall(5L, LocalDate.of(2026, 10, 14), CognitiveBias.PLANNING_FALLACY, "근거"),
                crisis,
                finishedCall(3L, LocalDate.of(2026, 10, 12), null, null)));

    List<CallRecordResponse> calls = service.getRecentCalls();

    assertThat(calls).extracting(CallRecordResponse::callId).containsExactly("5", "3");
    assertThat(calls.get(0).bias().label()).isEqualTo("계획 오류");
    assertThat(calls.get(0).bias().evidence()).isEqualTo("근거");
    assertThat(calls.get(0).missReason()).isEqualTo("tooMuch");
    assertThat(calls.get(0).promiseKept()).isTrue();
    assertThat(calls.get(1).bias()).isNull();
  }

  @Test
  @DisplayName("자주 보이는 습관: 최근 14일 통화에서 서로 다른 3일 이상 보인 습관만 돌려준다")
  void habits() {
    given(
            callRecordRepository.findByUserIdAndCallDateGreaterThanEqualOrderByStartedAtDescIdDesc(
                USER_ID, LocalDate.of(2026, 10, 1)))
        .willReturn(
            List.of(
                finishedCall(
                    4L, LocalDate.of(2026, 10, 13), CognitiveBias.PLANNING_FALLACY, "금방 한다고 함"),
                finishedCall(3L, LocalDate.of(2026, 10, 10), CognitiveBias.PLANNING_FALLACY, null),
                finishedCall(2L, LocalDate.of(2026, 10, 9), CognitiveBias.FUTURE_SELF, null),
                finishedCall(1L, LocalDate.of(2026, 10, 5), CognitiveBias.PLANNING_FALLACY, null)));

    List<BiasHabitResponse> habits = service.getHabits();

    assertThat(habits).hasSize(1);
    assertThat(habits.get(0).key()).isEqualTo("PLANNING_FALLACY");
    assertThat(habits.get(0).days()).isEqualTo(3);
    assertThat(habits.get(0).lastEvidence()).isEqualTo("금방 한다고 함");
    assertThat(habits.get(0).solution()).isEqualTo(CognitiveBias.PLANNING_FALLACY.getSolution());
  }

  @Test
  @DisplayName("기록 보기도 로그인하지 않았으면 401 이다")
  void listsRequireLogin() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    assertError(service::getRecentCalls, ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
    assertError(service::getHabits, ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
  }
}
