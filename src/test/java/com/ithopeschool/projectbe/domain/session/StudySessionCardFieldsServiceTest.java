package com.ithopeschool.projectbe.domain.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.CardRepository;
import com.ithopeschool.projectbe.domain.card.ChecklistItem;
import com.ithopeschool.projectbe.domain.care.Stage4Service;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.session.SessionTags.GoodReason;
import com.ithopeschool.projectbe.domain.session.SessionTags.PauseTag;
import com.ithopeschool.projectbe.domain.session.SessionTags.PlayTag;
import com.ithopeschool.projectbe.domain.session.SessionTags.WithWhat;
import com.ithopeschool.projectbe.domain.session.dto.SessionEndReportRequest;
import com.ithopeschool.projectbe.domain.session.dto.SessionMemoRequest;
import com.ithopeschool.projectbe.domain.session.dto.SessionPauseRequest;
import com.ithopeschool.projectbe.domain.session.dto.StudySessionCreateRequest;
import com.ithopeschool.projectbe.domain.session.dto.StudySessionResponse;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectColor;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

// 카드 기준으로 넓힌 공부 기록 칸(이슈 #224)의 저장 규칙을 확인합니다.
// 옛 칸의 규칙은 StudySessionServiceTest 가, 진짜 DB 에 딸린 테이블까지 들어가는지는 StudySessionCardFieldsFlowTest 가 봅니다.
class StudySessionCardFieldsServiceTest {

  private static final Long ME = 1L;
  private static final Long MY_CARD = 7L;
  private static final long MINUTE = 60_000;

  // 시계를 고정해 둔 "지금". 기록은 지금보다 앞선 19:00 ~ 20:00 (60분) 입니다.
  private static final Instant NOW = Instant.ofEpochMilli(1_790_010_000_000L);
  private static final long START = NOW.minusSeconds(3 * 3600).toEpochMilli();
  private static final long END = START + 60 * MINUTE;

  private StudySessionRepository studySessionRepository;
  private CardRepository cardRepository;
  private SubjectRepository subjectRepository;
  private Stage4Service stage4Service;
  private StudySessionService studySessionService;

  @BeforeEach
  void setUp() {
    CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    studySessionRepository = mock(StudySessionRepository.class);
    cardRepository = mock(CardRepository.class);
    subjectRepository = mock(SubjectRepository.class);
    // 기본은 관리 4단계가 아닙니다. (4단계 테스트에서만 켭니다)
    stage4Service = mock(Stage4Service.class);
    studySessionService =
        new StudySessionService(
            currentUserProvider,
            studySessionRepository,
            subjectRepository,
            mock(StudyPlanRepository.class),
            cardRepository,
            stage4Service,
            Clock.fixed(NOW, ZoneOffset.UTC));
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
    // 7번 카드만 내 것이고, 그 밖의 번호는 없거나 남의 것입니다.
    given(cardRepository.findByIdAndUserId(any(), any())).willReturn(Optional.empty());
    given(cardRepository.findByIdAndUserId(MY_CARD, ME))
        .willReturn(
            Optional.of(
                new Card(
                    ME,
                    "수학 문제집",
                    LocalDate.of(2026, 10, 12),
                    60,
                    List.of(new ChecklistItem("i1", "42쪽")),
                    NOW)));
    given(studySessionRepository.save(any()))
        .willAnswer(
            invocation -> {
              StudySession session = invocation.getArgument(0);
              ReflectionTestUtils.setField(session, "id", 100L);
              return session;
            });
  }

  // 카드로 켠 공부 기록 요청 (과목 없음). 카드 칸만 골라서 바꿔 씁니다.
  private static StudySessionCreateRequest cardSession(
      String mode,
      List<SessionPauseRequest> pauses,
      SessionEndReportRequest endReport,
      List<SessionMemoRequest> memos) {
    return new StudySessionCreateRequest(
        "", START, END, 0, null, null, 60, null, "study", "7", mode, pauses, true, endReport, memos,
        null, null);
  }

  private static SessionPauseRequest pause(long from, long to, String tag) {
    return new SessionPauseRequest(from, to, tag, null);
  }

  private void expectError(StudySessionCreateRequest request, ErrorCode code, String message) {
    assertThatThrownBy(() -> studySessionService.create(request))
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(code);
    verify(studySessionRepository, never()).save(any());
  }

  private StudySession savedSession() {
    ArgumentCaptor<StudySession> captor = ArgumentCaptor.forClass(StudySession.class);
    verify(studySessionRepository).save(captor.capture());
    return captor.getValue();
  }

  // ---- 일시정지 ----

  @Test
  @DisplayName("pauses 를 보내면 횟수·시간은 보낸 값 대신 pauses 로 계산한다 (19:20~25 + 19:40~50 → 2번, 15분)")
  void pausesDecideCountAndMillis() {
    StudySessionResponse response =
        studySessionService.create(
            cardSession(
                null,
                List.of(
                    pause(START + 20 * MINUTE, START + 25 * MINUTE, "toilet"),
                    pause(START + 40 * MINUTE, START + 50 * MINUTE, "phone")),
                null,
                null));

    StudySession saved = savedSession();
    // 요청의 pauseCount 는 0 이었지만 pauses 로 다시 셉니다.
    assertThat(saved.getPauseCount()).isEqualTo(2);
    assertThat(saved.getPausedMs()).isEqualTo(15 * MINUTE);
    assertThat(saved.getPauses())
        .extracting(SessionPause::getTag)
        .containsExactly(PauseTag.TOILET, PauseTag.PHONE);
    assertThat(response.pauses()).hasSize(2);
    assertThat(response.pauses().get(1).tag()).isEqualTo("phone");
  }

  @Test
  @DisplayName("끝이 시작보다 빠른 일시정지는 0분으로 친다 (FE summarizePauses 와 같음)")
  void backwardPauseCountsZero() {
    studySessionService.create(
        cardSession(
            null, List.of(pause(START + 30 * MINUTE, START + 20 * MINUTE, "stuck")), null, null));

    assertThat(savedSession().getPauseCount()).isEqualTo(1);
    assertThat(savedSession().getPausedMs()).isZero();
  }

  @Test
  @DisplayName("일시정지가 기록 밖이거나 이유가 7가지가 아니면 400")
  void wrongPauses() {
    expectError(
        cardSession(null, List.of(pause(START - MINUTE, START + MINUTE, "toilet")), null, null),
        ErrorCode.VALIDATION_ERROR,
        "일시정지 시각은 기록의 시작과 끝 사이여야 해요");
    expectError(
        cardSession(null, List.of(pause(END - MINUTE, END + MINUTE, "toilet")), null, null),
        ErrorCode.VALIDATION_ERROR,
        "일시정지 시각은 기록의 시작과 끝 사이여야 해요");
    expectError(
        cardSession(null, List.of(pause(START, START + MINUTE, "nap")), null, null),
        ErrorCode.VALIDATION_ERROR,
        "일시정지 이유는 toilet, snack, phone, mindWander, stuck, external, other 중에서 골라 주세요");
  }

  // ---- 카드 · 과목 · 방법 ----

  @Test
  @DisplayName("카드로 켠 공부 기록은 과목이 없어도 저장되고, 방법 기본값은 normal · 중도 종료가 적힌다")
  void cardSessionWithoutSubject() {
    StudySessionResponse response = studySessionService.create(cardSession(null, null, null, null));

    StudySession saved = savedSession();
    assertThat(saved.getSubjectId()).isNull();
    assertThat(saved.getCardId()).isEqualTo(MY_CARD);
    assertThat(saved.getMode()).isEqualTo(SessionMode.NORMAL);
    assertThat(saved.getEndedEarly()).isTrue();
    assertThat(response.subjectId()).isEmpty();
    assertThat(response.cardId()).isEqualTo("7");
    assertThat(response.mode()).isEqualTo("normal");
    assertThat(response.endedEarly()).isTrue();
    // pauses 를 안 보냈고 횟수가 0 이라 빈 배열, 종료 팝업은 안 보내서 null
    assertThat(response.pauses()).isEmpty();
    assertThat(response.endReport()).isNull();
  }

  @Test
  @DisplayName("없는 카드 · 남의 카드면 404")
  void notMyCard() {
    StudySessionCreateRequest request =
        new StudySessionCreateRequest(
            "", START, END, 0, null, null, 60, null, "study", "99", null, null, null, null, null,
            null, null);

    expectError(request, ErrorCode.NOT_FOUND, "카드를 찾을 수 없어요");
  }

  @Test
  @DisplayName("오프라인은 저장되고, 사후 입력(selfReport)과 모르는 방법은 400")
  void modes() {
    studySessionService.create(cardSession("offline", null, null, null));
    assertThat(savedSession().getMode()).isEqualTo(SessionMode.OFFLINE);

    expectErrorWithoutSaveCheck(
        cardSession("selfReport", null, null, null), "사후 입력은 카드의 사후 입력(late-entry)으로 보내 주세요");
    expectErrorWithoutSaveCheck(
        cardSession("NORMAL", null, null, null), "세션 방법은 normal, offline 중에서 골라 주세요");
  }

  // 앞에서 이미 한 번 저장한 테스트에서 씁니다. (never().save 검사를 하지 않음)
  private void expectErrorWithoutSaveCheck(StudySessionCreateRequest request, String message) {
    assertThatThrownBy(() -> studySessionService.create(request))
        .isInstanceOf(ApiException.class)
        .hasMessage(message);
  }

  // ---- 종료 팝업 · 메모 ----

  @Test
  @DisplayName("종료 팝업과 메모를 보내면 그대로 적는다 (빈 증빙 글은 없는 것)")
  void endReportAndMemos() {
    SessionEndReportRequest report =
        new SessionEndReportRequest(
            Map.of("i1", START + 30 * MINUTE),
            "tooMuch",
            "music",
            new SessionEndReportRequest.Proof("", "42~45쪽 풀었음"),
            List.of("luck", "focus"));

    StudySessionResponse response =
        studySessionService.create(
            cardSession(
                null,
                null,
                report,
                List.of(new SessionMemoRequest(START + 10 * MINUTE, "엄마한테 문자"))));

    StudySession saved = savedSession();
    assertThat(saved.getEndReported()).isTrue();
    assertThat(saved.getCheckedItems())
        .containsEntry("i1", Instant.ofEpochMilli(START + 30 * MINUTE));
    assertThat(saved.getWithWhat()).isEqualTo(WithWhat.MUSIC);
    assertThat(saved.getProofLink()).isNull();
    assertThat(saved.getProofSummary()).isEqualTo("42~45쪽 풀었음");
    assertThat(saved.getGoodReasons()).containsExactlyInAnyOrder(GoodReason.FOCUS, GoodReason.LUCK);
    assertThat(saved.getMemos()).extracting(SessionMemo::getText).containsExactly("엄마한테 문자");

    assertThat(response.endReport().checked()).containsEntry("i1", START + 30 * MINUTE);
    assertThat(response.endReport().shortfallReason()).isEqualTo("tooMuch");
    assertThat(response.endReport().proof())
        .isEqualTo(new StudySessionResponse.ProofResponse(null, "42~45쪽 풀었음"));
    // 잘 된 이유는 FE 버튼 순서대로 돌려줍니다.
    assertThat(response.endReport().goodReasons()).containsExactly("focus", "luck");
    assertThat(response.memos()).hasSize(1);
  }

  @Test
  @DisplayName("빈 종료 팝업({})도 '답했음' 으로 남는다 (체크 0개)")
  void emptyEndReport() {
    StudySessionResponse response =
        studySessionService.create(
            cardSession(
                null, null, new SessionEndReportRequest(null, null, null, null, null), null));

    assertThat(response.endReport().checked()).isEmpty();
    assertThat(response.endReport().proof()).isNull();
    assertThat(response.endReport().goodReasons()).isNull();
  }

  @Test
  @DisplayName("종료 팝업의 값이 틀리면 400 (못 한 이유 · 같이 한 것 · 잘 된 이유 · 체크 시각 · 항목 id)")
  void wrongEndReport() {
    expectError(
        cardSession(null, null, new SessionEndReportRequest(null, "lazy", null, null, null), null),
        ErrorCode.VALIDATION_ERROR,
        "못 한 이유는 tooMuch, stuck, phone, mindWander, tired, external, etc 중에서 골라 주세요");
    expectError(
        cardSession(null, null, new SessionEndReportRequest(null, null, "tv", null, null), null),
        ErrorCode.VALIDATION_ERROR,
        "같이 한 것은 music, video, none 중에서 골라 주세요");
    expectError(
        cardSession(
            null,
            null,
            new SessionEndReportRequest(null, null, null, null, List.of("smart")),
            null),
        ErrorCode.VALIDATION_ERROR,
        "잘 된 이유는 focus, goodPlan, condition, environment, helped, luck 중에서 골라 주세요");
    expectError(
        cardSession(
            null,
            null,
            new SessionEndReportRequest(
                Map.of("i1", NOW.toEpochMilli() + MINUTE), null, null, null, null),
            null),
        ErrorCode.VALIDATION_ERROR,
        "체크한 시각이 올바르지 않아요");
    expectError(
        cardSession(
            null,
            null,
            new SessionEndReportRequest(Map.of("x".repeat(41), START), null, null, null, null),
            null),
        ErrorCode.VALIDATION_ERROR,
        "체크한 항목의 id 는 1 ~ 40자로 보내 주세요");
    expectError(
        cardSession(null, null, null, List.of(new SessionMemoRequest(0L, "1970년"))),
        ErrorCode.VALIDATION_ERROR,
        "메모를 쓴 시각이 올바르지 않아요");
  }

  // ---- 사후 입력 (#225) ----

  @Test
  @DisplayName("사후 입력 세션은 방법 selfReport · 일시정지 0번 · 목표는 카드 길이로 저장되고, 증빙을 같이 적는다")
  void selfReport() {
    StudySessionResponse response =
        studySessionService.createSelfReport(
            MY_CARD,
            60,
            Instant.ofEpochMilli(START),
            Instant.ofEpochMilli(START + 50 * MINUTE),
            new SessionEndReportRequest(
                null,
                null,
                null,
                new SessionEndReportRequest.Proof("https://x.com/a", null),
                null));

    StudySession saved = savedSession();
    assertThat(saved.getMode()).isEqualTo(SessionMode.SELF_REPORT);
    assertThat(saved.getCardId()).isEqualTo(MY_CARD);
    assertThat(saved.getSubjectId()).isNull();
    assertThat(saved.getPauseCount()).isZero();
    assertThat(saved.getTargetMinutes()).isEqualTo(60);
    assertThat(response.mode()).isEqualTo("selfReport");
    assertThat(response.pauses()).isEmpty();
    assertThat(response.endReport().proof().link()).isEqualTo("https://x.com/a");
  }

  @Test
  @DisplayName("사후 입력도 남의 카드면 404, 이미 있는 기록과 겹치면 409, 1분 미만이면 400")
  void selfReportChecks() {
    assertThatThrownBy(
            () ->
                studySessionService.createSelfReport(
                    99L, 60, Instant.ofEpochMilli(START), Instant.ofEpochMilli(END), null))
        .isInstanceOf(ApiException.class)
        .hasMessage("카드를 찾을 수 없어요");
    assertThatThrownBy(
            () ->
                studySessionService.createSelfReport(
                    MY_CARD,
                    60,
                    Instant.ofEpochMilli(START),
                    Instant.ofEpochMilli(START + 30_000),
                    null))
        .isInstanceOf(ApiException.class)
        .hasMessage("1분 미만의 기록은 저장하지 않아요");
    given(
            studySessionRepository.existsByUserIdAndStartedAtLessThanAndEndedAtGreaterThan(
                any(), any(), any()))
        .willReturn(true);
    assertThatThrownBy(
            () ->
                studySessionService.createSelfReport(
                    MY_CARD, 60, Instant.ofEpochMilli(START), Instant.ofEpochMilli(END), null))
        .isInstanceOf(ApiException.class)
        .hasMessage("이미 저장된 기록과 시간이 겹쳐요");
    verify(studySessionRepository, never()).save(any());
  }

  // ---- 카드의 과목 (#404) ----

  // 과목 3번을 붙인 내 카드 7번으로 바꿔 둡니다.
  private void givenMyCardHasSubject3() {
    Card card =
        new Card(
            ME,
            "수학 문제집",
            LocalDate.of(2026, 10, 12),
            60,
            List.of(new ChecklistItem("i1", "42쪽")),
            NOW);
    card.changeSubjectId(3L);
    given(cardRepository.findByIdAndUserId(MY_CARD, ME)).willReturn(Optional.of(card));
  }

  // 카드 7번으로 켠 기록 요청. 과목 · 종류만 바꿔 씁니다.
  private static StudySessionCreateRequest cardSessionWith(String subjectId, String kind) {
    return new StudySessionCreateRequest(
        subjectId, START, END, 0, null, null, 60, null, kind, "7", null, null, null, null, null,
        null, null);
  }

  @Test
  @DisplayName("과목 없이 카드로 켠 공부 기록은 카드의 과목으로 저장한다 (과목별 시간이 '기타' 로 묶이지 않게)")
  void cardSubjectFillsEmptySubject() {
    givenMyCardHasSubject3();

    StudySessionResponse response = studySessionService.create(cardSessionWith("", "study"));

    assertThat(savedSession().getSubjectId()).isEqualTo(3L);
    assertThat(response.subjectId()).isEqualTo("3");
  }

  @Test
  @DisplayName("과목을 보냈으면 카드의 과목보다 보낸 과목이 먼저다")
  void sentSubjectWins() {
    givenMyCardHasSubject3();
    given(subjectRepository.findByIdAndUserId(4L, ME))
        .willReturn(Optional.of(new Subject(ME, "영어", SubjectColor.RED)));

    studySessionService.create(cardSessionWith("4", "study"));

    assertThat(savedSession().getSubjectId()).isEqualTo(4L);
  }

  @Test
  @DisplayName("카드에 과목이 없으면 지금처럼 과목 없이 저장하고, 노는 기록은 카드에 과목이 있어도 과목을 두지 않는다")
  void noCardSubjectOrPlayStaysEmpty() {
    // setUp 의 7번 카드는 과목이 없습니다.
    studySessionService.create(cardSessionWith("", "study"));
    assertThat(savedSession().getSubjectId()).isNull();

    givenMyCardHasSubject3();
    StudySessionResponse play = studySessionService.create(cardSessionWith(null, "play"));
    assertThat(play.subjectId()).isEmpty();
  }

  @Test
  @DisplayName("사후 입력도 카드의 과목으로 저장한다")
  void selfReportUsesCardSubject() {
    givenMyCardHasSubject3();

    studySessionService.createSelfReport(
        MY_CARD, 60, Instant.ofEpochMilli(START), Instant.ofEpochMilli(START + 50 * MINUTE), null);

    assertThat(savedSession().getSubjectId()).isEqualTo(3L);
  }

  // ---- 노는 기록 ----

  @Test
  @DisplayName("노는 종류 · 초과 휴식은 노는 기록에만 저장하고, 공부 기록에 보내면 무시한다")
  void playFields() {
    StudySessionCreateRequest play =
        new StudySessionCreateRequest(
            null, START, END, 0, null, null, 60, null, "play", null, null, null, null, null, null,
            "game", 120_000L);
    StudySessionResponse response = studySessionService.create(play);
    assertThat(response.playTag()).isEqualTo("game");
    assertThat(response.overRestMs()).isEqualTo(120_000L);
    assertThat(savedSession().getPlayTag()).isEqualTo(PlayTag.GAME);

    StudySessionCreateRequest study =
        new StudySessionCreateRequest(
            "", START, END, 0, null, null, 60, null, "study", "7", null, null, null, null, null,
            "game", 120_000L);
    StudySessionResponse studyResponse = studySessionService.create(study);
    assertThat(studyResponse.playTag()).isNull();
    assertThat(studyResponse.overRestMs()).isNull();
  }

  @Test
  @DisplayName("노는 기록의 종류가 6가지가 아니면 400")
  void wrongPlayTag() {
    expectError(
        new StudySessionCreateRequest(
            null, START, END, 0, null, null, 60, null, "play", null, null, null, null, null, null,
            "netflix", null),
        ErrorCode.VALIDATION_ERROR,
        "노는 종류는 youtube, game, sns, webtoon, rest, etc 중에서 골라 주세요");
  }

  // ---- 관리 4단계 (#234) ----

  private static SessionEndReportRequest proofReport(String summary) {
    return new SessionEndReportRequest(
        null, null, null, new SessionEndReportRequest.Proof(null, summary), null);
  }

  @Test
  @DisplayName("4단계가 아니면 증빙 필요로 남기지 않고, 4단계를 풀려고 하지도 않는다")
  void notStage4() {
    studySessionService.create(cardSession(null, null, proofReport("42~45쪽 풀었음"), null));

    assertThat(savedSession().isProofRequired()).isFalse();
    verify(stage4Service, never()).releaseIfProven(any(), anyBoolean());
  }

  @Test
  @DisplayName("4단계에서 증빙 없이 끝낸 세션은 증빙 필요로 남고, 4단계는 풀리지 않는다 (증빙 없음으로 넘김)")
  void stage4WithoutProof() {
    given(stage4Service.isOn(ME)).willReturn(true);

    studySessionService.create(cardSession(null, null, null, null));

    StudySession saved = savedSession();
    assertThat(saved.isProofRequired()).isTrue();
    assertThat(saved.hasProof()).isFalse();
    verify(stage4Service).releaseIfProven(ME, false);
  }

  @Test
  @DisplayName("4단계에서 증빙이 있는 카드 실행을 저장하면 4단계를 풀어 달라고 한다 (사후 입력도 같음)")
  void stage4WithProofReleases() {
    given(stage4Service.isOn(ME)).willReturn(true);

    studySessionService.create(cardSession(null, null, proofReport("42~45쪽 풀었음"), null));
    assertThat(savedSession().isProofRequired()).isTrue();
    verify(stage4Service).releaseIfProven(ME, true);

    studySessionService.createSelfReport(
        MY_CARD,
        60,
        Instant.ofEpochMilli(END + 10 * MINUTE),
        Instant.ofEpochMilli(END + 40 * MINUTE),
        proofReport("43쪽 풀었음"));
    verify(stage4Service, times(2)).releaseIfProven(ME, true);
  }
}
