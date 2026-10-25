package com.ithopeschool.projectbe.domain.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.card.dto.CardAlertRequest;
import com.ithopeschool.projectbe.domain.card.dto.CardCreateRequest;
import com.ithopeschool.projectbe.domain.card.dto.CardResponse;
import com.ithopeschool.projectbe.domain.card.dto.CardUpdateRequest;
import com.ithopeschool.projectbe.domain.card.dto.ChecklistItemRequest;
import com.ithopeschool.projectbe.domain.fixedblock.FixedBlock;
import com.ithopeschool.projectbe.domain.fixedblock.FixedBlockRepository;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswer;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswerRepository;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectColor;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// 카드 로직의 규칙을 하나씩 확인합니다. DB 는 가짜(Repository 를 흉내 낸 것)로 바꾸고 "어떻게 물었는지" 를 봅니다.
// 진짜 DB 에서 내 것만 나오는지는 CardRepositoryTest 가 확인합니다.
class CardServiceTest {

  // 지금 요청한 사람(나)의 번호
  private static final Long ME = 1L;
  // 2026-10-12 는 월요일입니다.
  private static final LocalDate MON_OCT_12 = LocalDate.of(2026, 10, 12);
  private static final Instant NOW = Instant.parse("2026-10-11T03:00:00Z");

  private CurrentUserProvider currentUserProvider;
  private CardRepository cardRepository;
  private OnboardingAnswerRepository onboardingAnswerRepository;
  private FixedBlockRepository fixedBlockRepository;
  private SubjectRepository subjectRepository;
  private CardService cardService;

  @BeforeEach
  void setUp() {
    currentUserProvider = mock(CurrentUserProvider.class);
    cardRepository = mock(CardRepository.class);
    // 설문(취침 · 기상)과 고정 일정은 기본으로 없습니다. 그러면 수면 보호는 00:00 ~ 06:00 입니다. (이슈 #223)
    onboardingAnswerRepository = mock(OnboardingAnswerRepository.class);
    fixedBlockRepository = mock(FixedBlockRepository.class);
    // 내 과목은 3번 · 4번뿐이고, 그 밖의 번호는 없거나 남의 과목입니다. (이슈 #404)
    subjectRepository = mock(SubjectRepository.class);
    given(subjectRepository.findByIdAndUserId(any(), any())).willReturn(Optional.empty());
    given(subjectRepository.findByIdAndUserId(3L, ME))
        .willReturn(Optional.of(new Subject(ME, "수학", SubjectColor.BLUE)));
    given(subjectRepository.findByIdAndUserId(4L, ME))
        .willReturn(Optional.of(new Subject(ME, "영어", SubjectColor.RED)));
    cardService =
        new CardService(
            currentUserProvider,
            cardRepository,
            Clock.fixed(NOW, ZoneId.of("UTC")),
            onboardingAnswerRepository,
            fixedBlockRepository,
            subjectRepository);
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
    // 저장하면 DB 가 번호를 매겨 준 것처럼 돌려줍니다.
    given(cardRepository.save(any()))
        .willAnswer(invocation -> withId(invocation.getArgument(0), 100L));
    given(cardRepository.findByIdAndUserId(any(), any())).willReturn(Optional.empty());
  }

  private static Card withId(Card card, long id) {
    ReflectionTestUtils.setField(card, "id", id);
    return card;
  }

  // DB 에 이미 있는 내 카드 (체크리스트 i1 · i2)
  private static Card myCard(long id, String title, LocalDate date, List<Integer> repeatDays) {
    Card card =
        new Card(
            ME,
            title,
            date,
            60,
            List.of(new ChecklistItem("i1", "42쪽"), new ChecklistItem("i2", "43쪽")),
            NOW);
    card.changeRepeatDays(repeatDays);
    return withId(card, id);
  }

  private static ChecklistItemRequest item(String id, String text) {
    return new ChecklistItemRequest(id, text);
  }

  // 꼭 있어야 하는 값만 채운 만들기 요청 (나머지는 null)
  private static CardCreateRequest create(List<ChecklistItemRequest> checklist) {
    return new CardCreateRequest(
        "수학 문제집",
        MON_OCT_12,
        null,
        "19:00",
        60,
        checklist,
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

  // 바꿀 칸이 하나도 없는 바꾸기 요청. 테스트마다 필요한 칸만 채워서 씁니다.
  private static CardUpdateRequest emptyUpdate() {
    return new CardUpdateRequest(
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
  }

  private static void assertError(Runnable action, ErrorCode code, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(code);
  }

  // ---- 로그인 ----

  @Test
  @DisplayName("로그인하지 않았으면 네 가지 모두 401 이고, DB 는 건드리지 않는다")
  void everythingRequiresLogin() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));
    String message = "로그인이 필요해요";

    assertError(() -> cardService.getCards(MON_OCT_12), ErrorCode.UNAUTHORIZED, message);
    assertError(
        () -> cardService.create(create(List.of(item(null, "42쪽")))),
        ErrorCode.UNAUTHORIZED,
        message);
    assertError(() -> cardService.update(7L, emptyUpdate()), ErrorCode.UNAUTHORIZED, message);
    assertError(() -> cardService.delete(7L), ErrorCode.UNAUTHORIZED, message);
    verify(cardRepository, never()).save(any());
  }

  // ---- 목록 ----

  @Test
  @DisplayName("날짜를 보내면 그날 하는 카드만, 시작 시각 순 → 시각 없는 카드는 뒤 → 같으면 번호 순")
  void cardsOnDate() {
    Card evening = myCard(1L, "저녁 1회성", MON_OCT_12, List.of());
    evening.changeStartTime(LocalTime.of(19, 0));
    Card noTime = myCard(2L, "시각 없는 옛 카드", MON_OCT_12, List.of());
    Card morningRepeat = myCard(3L, "아침 반복 (월·수)", LocalDate.of(2026, 10, 1), List.of(1, 3));
    morningRepeat.changeStartTime(LocalTime.of(7, 30));
    Card tuesdayRepeat = myCard(4L, "화요일 반복", LocalDate.of(2026, 10, 1), List.of(2));
    tuesdayRepeat.changeStartTime(LocalTime.of(6, 0));
    given(cardRepository.findCandidatesOn(ME, MON_OCT_12))
        .willReturn(List.of(evening, noTime, morningRepeat, tuesdayRepeat));

    List<CardResponse> cards = cardService.getCards(MON_OCT_12);

    // 화요일 반복 카드는 후보에 있어도 요일이 달라서 빠집니다.
    assertThat(cards)
        .extracting(CardResponse::title)
        .containsExactly("아침 반복 (월·수)", "저녁 1회성", "시각 없는 옛 카드");
  }

  @Test
  @DisplayName("날짜를 안 보내면 내 카드 전부를 만든 순서로 돌려준다")
  void allCards() {
    given(cardRepository.findByUserIdOrderById(ME))
        .willReturn(
            List.of(
                myCard(1L, "수학", MON_OCT_12, List.of()),
                myCard(2L, "영어", LocalDate.of(2026, 10, 1), List.of(1))));

    assertThat(cardService.getCards(null))
        .extracting(CardResponse::title)
        .containsExactly("수학", "영어");
  }

  // ---- 만들기 ----

  @Test
  @DisplayName("꼭 있어야 하는 값만 보내면 1회성 · 유연 · 자습 · 기본 알림 카드가 되고, 만든 시각은 서버 시계")
  void createWithDefaults() {
    CardResponse saved = cardService.create(create(List.of(item(null, "42~45쪽"))));

    assertThat(saved.id()).isEqualTo("100");
    assertThat(saved.title()).isEqualTo("수학 문제집");
    assertThat(saved.date()).isEqualTo(MON_OCT_12);
    assertThat(saved.repeatDays()).isNull();
    assertThat(saved.startTime()).isEqualTo("19:00");
    assertThat(saved.durationMinutes()).isEqualTo(60);
    assertThat(saved.priority()).isEqualTo("flexible");
    assertThat(saved.type()).isEqualTo("self");
    assertThat(saved.goalId()).isNull();
    assertThat(saved.expectedDone()).isNull();
    assertThat(saved.expectedFailCauses()).isNull();
    assertThat(saved.alert()).isNull();
    assertThat(saved.planningMs()).isNull();
    assertThat(saved.createdAt()).isEqualTo(NOW.toEpochMilli());
  }

  @Test
  @DisplayName("모든 칸을 보내면 그대로 저장된다 (반복 요일은 작은 숫자부터)")
  void createWithEverything() {
    CardCreateRequest request =
        new CardCreateRequest(
            "영어 단어",
            LocalDate.of(2026, 10, 1),
            List.of(5, 1, 3),
            "07:30",
            30,
            List.of(item("a", "50개"), item("b", "복습")),
            "12",
            null,
            "absolute",
            "lecture",
            1,
            "피곤함",
            List.of("폰을 볼 것 같다"),
            new CardAlertRequest("call", 10, "단어 외울 시간이야"),
            42000L);

    CardResponse saved = cardService.create(request);

    assertThat(saved.repeatDays()).containsExactly(1, 3, 5);
    assertThat(saved.checklist())
        .extracting(CardResponse.ChecklistItemResponse::id)
        .containsExactly("a", "b");
    assertThat(saved.goalId()).isEqualTo("12");
    assertThat(saved.priority()).isEqualTo("absolute");
    assertThat(saved.type()).isEqualTo("lecture");
    assertThat(saved.expectedDone()).isEqualTo(1);
    assertThat(saved.expectedDoneReason()).isEqualTo("피곤함");
    assertThat(saved.expectedFailCauses()).containsExactly("폰을 볼 것 같다");
    assertThat(saved.alert()).isEqualTo(new CardResponse.AlertResponse("call", 10, "단어 외울 시간이야"));
    assertThat(saved.planningMs()).isEqualTo(42000L);
  }

  @Test
  @DisplayName("id 를 안 보낸 체크리스트 항목에는 겹치지 않는 i1, i2 … 를 붙인다")
  void checklistIdsAreMade() {
    CardResponse saved =
        cardService.create(create(List.of(item(null, "a"), item("i1", "b"), item("", "c"))));

    assertThat(saved.checklist())
        .extracting(CardResponse.ChecklistItemResponse::id)
        .containsExactly("i2", "i1", "i3");
  }

  @Test
  @DisplayName("체크리스트 id 가 겹치면 400 이고 저장하지 않는다")
  void duplicateChecklistIds() {
    assertError(
        () -> cardService.create(create(List.of(item("x", "a"), item("x", "b")))),
        ErrorCode.VALIDATION_ERROR,
        "체크리스트 항목 id 가 겹치지 않게 보내 주세요");
    verify(cardRepository, never()).save(any());
  }

  @Test
  @DisplayName("틀린 값이면 400 이고 저장하지 않는다 (겹친 요일 · 우선순위 · 유형 · 알림 방법 · 예상 달성 개수)")
  void rejectsWrongValues() {
    List<ChecklistItemRequest> two = List.of(item(null, "a"), item(null, "b"));
    assertError(
        () ->
            cardService.create(
                new CardCreateRequest(
                    "수학",
                    MON_OCT_12,
                    List.of(1, 1),
                    null,
                    60,
                    two,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null)),
        ErrorCode.VALIDATION_ERROR,
        "반복 요일이 겹치지 않게 보내 주세요");
    assertError(
        () ->
            cardService.create(
                new CardCreateRequest(
                    "수학",
                    MON_OCT_12,
                    null,
                    null,
                    60,
                    two,
                    null,
                    null,
                    "urgent",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null)),
        ErrorCode.VALIDATION_ERROR,
        "우선순위는 absolute, flexible 중에서 골라 주세요");
    assertError(
        () ->
            cardService.create(
                new CardCreateRequest(
                    "수학",
                    MON_OCT_12,
                    null,
                    null,
                    60,
                    two,
                    null,
                    null,
                    null,
                    "SELF",
                    null,
                    null,
                    null,
                    null,
                    null)),
        ErrorCode.VALIDATION_ERROR,
        "카드 유형은 self, lecture 중에서 골라 주세요");
    assertError(
        () ->
            cardService.create(
                new CardCreateRequest(
                    "수학",
                    MON_OCT_12,
                    null,
                    null,
                    60,
                    two,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new CardAlertRequest("push", 0, null),
                    null)),
        ErrorCode.VALIDATION_ERROR,
        "알림 방법은 message, call 중에서 골라 주세요");
    assertError(
        () ->
            cardService.create(
                new CardCreateRequest(
                    "수학",
                    MON_OCT_12,
                    null,
                    null,
                    60,
                    two,
                    null,
                    null,
                    null,
                    null,
                    3,
                    null,
                    null,
                    null,
                    null)),
        ErrorCode.VALIDATION_ERROR,
        "예상 달성 개수는 체크리스트 개수(2개) 이하로 보내 주세요");
    verify(cardRepository, never()).save(any());
  }

  // ---- 바꾸기 ----

  @Test
  @DisplayName("보낸 칸만 바뀌고 나머지는 그대로다")
  void updateOnlySentFields() {
    Card card = myCard(7L, "수학", MON_OCT_12, List.of());
    card.changeStartTime(LocalTime.of(19, 0));
    given(cardRepository.findByIdAndUserId(7L, ME)).willReturn(Optional.of(card));

    CardResponse changed =
        cardService.update(
            7L,
            new CardUpdateRequest(
                "수학 2권",
                null,
                List.of(1, 3),
                null,
                90,
                null,
                null,
                null,
                "absolute",
                null,
                null,
                null,
                null,
                null,
                null));

    assertThat(changed.title()).isEqualTo("수학 2권");
    assertThat(changed.repeatDays()).containsExactly(1, 3);
    assertThat(changed.durationMinutes()).isEqualTo(90);
    assertThat(changed.priority()).isEqualTo("absolute");
    // 안 보낸 칸
    assertThat(changed.date()).isEqualTo(MON_OCT_12);
    assertThat(changed.startTime()).isEqualTo("19:00");
    assertThat(changed.type()).isEqualTo("self");
    assertThat(changed.checklist()).hasSize(2);
  }

  @Test
  @DisplayName(
      "빈 값을 보내면 지운다: repeatDays [] → 1회성, startTime \"\" · goalId \"\" · expectedFailCauses []")
  void updateClearsWithEmptyValues() {
    Card card = myCard(7L, "수학", LocalDate.of(2026, 10, 1), List.of(1, 3));
    card.changeStartTime(LocalTime.of(19, 0));
    card.changeGoalId(12L);
    card.changeExpectedFailCauses(List.of("폰"));
    given(cardRepository.findByIdAndUserId(7L, ME)).willReturn(Optional.of(card));

    CardResponse changed =
        cardService.update(
            7L,
            new CardUpdateRequest(
                null, null, List.of(), "", null, null, "", null, null, null, null, "", List.of(),
                null, null));

    assertThat(changed.repeatDays()).isNull();
    assertThat(changed.startTime()).isNull();
    assertThat(changed.goalId()).isNull();
    assertThat(changed.expectedDoneReason()).isNull();
    assertThat(changed.expectedFailCauses()).isNull();
  }

  @Test
  @DisplayName("체크리스트를 바꾸면 통째로 바뀌고, 새 항목에는 원래 항목의 id 를 다시 붙이지 않는다")
  void updateChecklist() {
    Card card = myCard(7L, "수학", MON_OCT_12, List.of());
    given(cardRepository.findByIdAndUserId(7L, ME)).willReturn(Optional.of(card));
    CardUpdateRequest request =
        new CardUpdateRequest(
            null,
            null,
            null,
            null,
            null,
            List.of(item("i2", "43쪽"), item(null, "새 항목")),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    CardResponse changed = cardService.update(7L, request);

    // 원래 i1 · i2 가 있었으므로 새 항목은 i1 이 아니라 i3 입니다. (지난 세션 기록의 i1 과 헷갈리지 않게)
    assertThat(changed.checklist())
        .extracting(CardResponse.ChecklistItemResponse::id)
        .containsExactly("i2", "i3");
  }

  @Test
  @DisplayName("체크리스트를 줄여서 예상 달성 개수가 넘치면 400 이다")
  void updateChecksExpectedDoneAfterChange() {
    Card card = myCard(7L, "수학", MON_OCT_12, List.of());
    card.changeExpectedDone(2);
    given(cardRepository.findByIdAndUserId(7L, ME)).willReturn(Optional.of(card));
    CardUpdateRequest request =
        new CardUpdateRequest(
            null,
            null,
            null,
            null,
            null,
            List.of(item("i1", "42쪽")),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    assertError(
        () -> cardService.update(7L, request),
        ErrorCode.VALIDATION_ERROR,
        "예상 달성 개수는 체크리스트 개수(1개) 이하로 보내 주세요");
  }

  @Test
  @DisplayName("바꿀 값을 하나도 안 보내면 400 \"바꿀 값을 보내 주세요\"")
  void emptyUpdate400() {
    given(cardRepository.findByIdAndUserId(7L, ME))
        .willReturn(Optional.of(myCard(7L, "수학", MON_OCT_12, List.of())));

    assertError(
        () -> cardService.update(7L, emptyUpdate()), ErrorCode.VALIDATION_ERROR, "바꿀 값을 보내 주세요");
  }

  @Test
  @DisplayName("없는 카드나 남의 카드는 바꾸기 · 지우기 모두 404 이고, 지우지 않는다")
  void othersCardIs404() {
    // findByIdAndUserId 는 setUp 에서 늘 빈 Optional 입니다. (남의 카드는 내 번호로 찾으면 없음)
    assertError(
        () ->
            cardService.update(
                99L,
                new CardUpdateRequest(
                    "x", null, null, null, null, null, null, null, null, null, null, null, null,
                    null, null)),
        ErrorCode.NOT_FOUND,
        "카드를 찾을 수 없어요");
    assertError(() -> cardService.delete(99L), ErrorCode.NOT_FOUND, "카드를 찾을 수 없어요");
    verify(cardRepository, never()).delete(any());
  }

  @Test
  @DisplayName("내 카드는 지워진다")
  void deleteMine() {
    Card card = myCard(7L, "수학", MON_OCT_12, List.of());
    given(cardRepository.findByIdAndUserId(7L, ME)).willReturn(Optional.of(card));

    cardService.delete(7L);

    verify(cardRepository).delete(card);
  }

  // ---- 과목 (이슈 #404) ----

  // 과목만 고른 만들기 요청 (나머지는 꼭 있어야 하는 값만)
  private static CardCreateRequest createWithSubject(String subjectId) {
    return new CardCreateRequest(
        "수학 문제집",
        MON_OCT_12,
        null,
        "19:00",
        60,
        List.of(item(null, "42쪽")),
        null,
        subjectId,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  // 과목만 바꾸는 바꾸기 요청
  private static CardUpdateRequest updateSubject(String subjectId) {
    return new CardUpdateRequest(
        null, null, null, null, null, null, null, subjectId, null, null, null, null, null, null,
        null);
  }

  @Test
  @DisplayName("내 과목을 붙여 만들면 저장되고 응답의 subjectId 는 글자다. 안 보내거나 \"\" 면 과목 없음(null)")
  void createWithMySubject() {
    CardResponse saved = cardService.create(createWithSubject("3"));
    assertThat(saved.subjectId()).isEqualTo("3");

    assertThat(cardService.create(createWithSubject(null)).subjectId()).isNull();
    assertThat(cardService.create(createWithSubject("")).subjectId()).isNull();
  }

  @Test
  @DisplayName("없는 과목이나 남의 과목을 붙이면 만들기 · 바꾸기 모두 404 이고 저장하지 않는다")
  void othersSubjectIs404() {
    assertError(
        () -> cardService.create(createWithSubject("99")), ErrorCode.NOT_FOUND, "과목을 찾을 수 없어요");
    verify(cardRepository, never()).save(any());

    Card card = myCard(7L, "수학", MON_OCT_12, List.of());
    card.changeSubjectId(3L);
    given(cardRepository.findByIdAndUserId(7L, ME)).willReturn(Optional.of(card));
    assertError(
        () -> cardService.update(7L, updateSubject("99")), ErrorCode.NOT_FOUND, "과목을 찾을 수 없어요");
    assertThat(card.getSubjectId()).isEqualTo(3L);
  }

  @Test
  @DisplayName("바꾸기: 과목을 보내면 바뀌고, \"\" 면 비우고, 안 보내면(null) 그대로다")
  void updateSubjectKeepsOrClears() {
    Card card = myCard(7L, "수학", MON_OCT_12, List.of());
    card.changeSubjectId(3L);
    given(cardRepository.findByIdAndUserId(7L, ME)).willReturn(Optional.of(card));

    assertThat(cardService.update(7L, updateSubject("4")).subjectId()).isEqualTo("4");

    // 다른 칸만 바꾸면 과목은 그대로입니다.
    CardResponse titleOnly =
        cardService.update(
            7L,
            new CardUpdateRequest(
                "수학 2권", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null));
    assertThat(titleOnly.subjectId()).isEqualTo("4");

    assertThat(cardService.update(7L, updateSubject("")).subjectId()).isNull();
    assertThat(card.getSubjectId()).isNull();
  }

  // ---- 수면 보호 · 10분 버퍼 (이슈 #223) ----

  // 시작 시각 · 길이만 정한 만들기 요청
  private static CardCreateRequest createAt(String startTime, int minutes) {
    return new CardCreateRequest(
        "수학 문제집",
        MON_OCT_12,
        null,
        startTime,
        minutes,
        List.of(new ChecklistItemRequest(null, "42쪽")),
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

  private void givenSleep(String sleep, String wake) {
    OnboardingAnswer answer = mock(OnboardingAnswer.class);
    given(answer.getSleepTime()).willReturn(LocalTime.parse(sleep));
    given(answer.getWakeUpTime()).willReturn(LocalTime.parse(wake));
    given(onboardingAnswerRepository.findFirstByUserIdOrderByAnsweredAtDescIdDesc(ME))
        .willReturn(Optional.of(answer));
  }

  @Test
  @DisplayName("설문에 취침 · 기상이 없으면 수면 보호는 00:00~06:00 이라, 05:30 카드는 400 이고 저장하지 않는다")
  void defaultSleepWindowBlocks() {
    assertThatThrownBy(() -> cardService.create(createAt("05:30", 60)))
        .isInstanceOf(ApiException.class)
        .hasMessage("수면 보호 시간(00:00~06:00)과 겹쳐요");
    verify(cardRepository, never()).save(any());
  }

  @Test
  @DisplayName("설문의 23:00~07:00 과 겹치는 22:30 · 60분은 400, 딱 붙은 22:00 · 60분과 시작 시각 없는 카드는 저장된다")
  void sleepFromSurvey() {
    givenSleep("23:00", "07:00");

    assertThatThrownBy(() -> cardService.create(createAt("22:30", 60)))
        .isInstanceOf(ApiException.class)
        .hasMessage("수면 보호 시간(23:00~07:00)과 겹쳐요");
    assertThat(cardService.create(createAt("22:00", 60)).startTime()).isEqualTo("22:00");
    assertThat(cardService.create(createAt("", 60)).startTime()).isNull();
  }

  @Test
  @DisplayName("고칠 때는 시각이나 길이를 바꿀 때만 수면 보호를 본다 (제목만 고치면 예전 카드도 그대로 저장)")
  void sleepOnUpdateOnlyWhenTimeChanges() {
    givenSleep("23:00", "07:00");
    Card old = myCard(7, "예전 카드", MON_OCT_12, List.of());
    old.changeStartTime(LocalTime.of(23, 30));
    given(cardRepository.findByIdAndUserId(7L, ME)).willReturn(Optional.of(old));

    CardUpdateRequest titleOnly =
        new CardUpdateRequest(
            "새 제목", null, null, null, null, null, null, null, null, null, null, null, null, null,
            null);
    assertThat(cardService.update(7L, titleOnly).title()).isEqualTo("새 제목");

    CardUpdateRequest longer =
        new CardUpdateRequest(
            null, null, null, null, 90, null, null, null, null, null, null, null, null, null, null);
    assertThatThrownBy(() -> cardService.update(7L, longer))
        .isInstanceOf(ApiException.class)
        .hasMessage("수면 보호 시간(23:00~07:00)과 겹쳐요");
  }

  @Test
  @DisplayName("앞뒤 고정 일정과 10분 여유가 없으면 응답에 bufferWarning 을 붙이고 저장은 한다, 넉넉하면 null")
  void bufferWarning() {
    // 2026-10-12 는 월요일(1). 월요일마다 학원 18:00~21:00
    given(fixedBlockRepository.findByUserIdOrderById(ME))
        .willReturn(
            List.of(
                new FixedBlock(
                    ME, "학원", List.of(1), LocalTime.of(18, 0), LocalTime.of(21, 0), NOW)));

    CardResponse tight = cardService.create(createAt("21:05", 60));
    assertThat(tight.bufferWarning()).isEqualTo("학원 18:00~21:00 과 사이가 5분뿐이에요. 10분 여유를 두면 좋아요.");
    verify(cardRepository).save(any());

    assertThat(cardService.create(createAt("16:50", 60)).bufferWarning()).isNull();
  }
}
