package com.ithopeschool.projectbe.domain.answercheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.ai.AiClient;
import com.ithopeschool.projectbe.domain.answercheck.dto.AnswerCheckCreateRequest;
import com.ithopeschool.projectbe.domain.answercheck.dto.AnswerCheckItemUpdateRequest;
import com.ithopeschool.projectbe.domain.answercheck.dto.AnswerCheckResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

// Service 단위 테스트: AI(AiClient)와 DB(Repository)를 가짜로 두고, 순서 · 판정 규칙 · 저장하는 것만 확인합니다.
class AnswerCheckServiceTest {

  private static final Long USER_ID = 7L;
  private static final LocalDate DATE = LocalDate.of(2026, 10, 24);
  private static final Instant NOW = Instant.parse("2026-10-24T11:02:00Z");
  // 작은 jpeg 처럼 보이는 사진 (내용은 상관없습니다. 모양만 봅니다)
  private static final String IMAGE =
      "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(new byte[] {1, 2, 3, 4});

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final AnswerCheckRepository answerCheckRepository = mock(AnswerCheckRepository.class);
  private final AnswerCheckItemRepository answerCheckItemRepository =
      mock(AnswerCheckItemRepository.class);
  private final AiClient aiClient = mock(AiClient.class);
  private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
  private final AnswerCheckService service =
      new AnswerCheckService(
          currentUserProvider,
          answerCheckRepository,
          answerCheckItemRepository,
          aiClient,
          transactionTemplate,
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    // 트랜잭션은 받은 일을 그대로 실행하기만 합니다.
    given(transactionTemplate.execute(any()))
        .willAnswer(
            invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
    // 저장하면 DB 가 번호를 붙여 주는 것처럼 기록은 12번, 문제는 31번부터 붙입니다.
    given(answerCheckRepository.save(any()))
        .willAnswer(
            invocation -> {
              AnswerCheck check = invocation.getArgument(0);
              ReflectionTestUtils.setField(check, "id", 12L);
              return check;
            });
    given(answerCheckItemRepository.saveAll(anyList()))
        .willAnswer(
            invocation -> {
              List<AnswerCheckItem> rows = invocation.getArgument(0);
              for (int i = 0; i < rows.size(); i++) {
                ReflectionTestUtils.setField(rows.get(i), "id", 31L + i);
              }
              return rows;
            });
  }

  private void givenAnswer(AnswerGrading.Answer answer) {
    given(aiClient.askWithImage(any(), any(), any(), any(), eq(AnswerGrading.Answer.class)))
        .willReturn(Optional.ofNullable(answer));
  }

  private static AnswerGrading.Answer.Item item(
      String number, String myAnswer, String aiAnswer, String markRead, String result) {
    return new AnswerGrading.Answer.Item(number, myAnswer, aiAnswer, markRead, result);
  }

  private static AnswerCheckCreateRequest request(AnswerCheckMode mode) {
    return new AnswerCheckCreateRequest(mode, IMAGE, DATE);
  }

  @Test
  @DisplayName("아직 안 했어요(UNMARKED): AI 판정을 쓰되 학생 답 · AI 답을 못 읽은 문제는 애매로 바꿔 저장한다")
  void gradesUnmarked() {
    givenAnswer(
        new AnswerGrading.Answer(
            false,
            true,
            "수학",
            List.of(
                item("1", "x = 5", "x = 5", "O", "CORRECT"),
                item("2", "3", "4", null, "WRONG"),
                // 학생 답을 못 읽었는데 AI 가 틀림이라고 함 → 애매
                item("3", null, "12", null, "WRONG"),
                item("3-1", "1/2", "0.5", null, "UNSURE"))));

    AnswerCheckResponse response = service.create(request(AnswerCheckMode.UNMARKED));

    assertThat(response.graded()).isTrue();
    assertThat(response.showHelp()).isFalse();
    assertThat(response.id()).isEqualTo("12");
    assertThat(response.date()).isEqualTo(DATE);
    assertThat(response.mode()).isEqualTo(AnswerCheckMode.UNMARKED);
    assertThat(response.subjectText()).isEqualTo("수학");
    assertThat(response.createdAt()).isEqualTo(NOW);
    assertThat(response.counts()).isEqualTo(new AnswerCheckResponse.Counts(1, 1, 2));
    assertThat(response.items())
        .extracting(
            AnswerCheckResponse.Item::id,
            AnswerCheckResponse.Item::number,
            AnswerCheckResponse.Item::aiAnswer,
            AnswerCheckResponse.Item::markRead,
            AnswerCheckResponse.Item::result,
            AnswerCheckResponse.Item::editedByUser)
        .containsExactly(
            // UNMARKED 에서는 AI 가 O · X 를 줘도 버립니다.
            tuple("31", "1", "x = 5", null, GradeResult.CORRECT, false),
            tuple("32", "2", "4", null, GradeResult.WRONG, false),
            tuple("33", "3", "12", null, GradeResult.UNSURE, false),
            tuple("34", "3-1", "0.5", null, GradeResult.UNSURE, false));
    verify(aiClient)
        .askWithImage(
            eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(IMAGE), eq(AnswerGrading.Answer.class));
  }

  @Test
  @DisplayName("채점했어요(MARKED): 판정은 O · X 표시로 코드가 정하고, AI 답은 버린다")
  void gradesMarked() {
    givenAnswer(
        new AnswerGrading.Answer(
            false,
            true,
            null,
            List.of(
                item("1", "②", "③", "O", "WRONG"),
                item("2", null, null, "X", "CORRECT"),
                // 세모 · 못 읽은 표시 → 애매
                item("3", null, null, "△", "CORRECT"),
                item("4", null, null, null, "WRONG"))));

    AnswerCheckResponse response = service.create(request(AnswerCheckMode.MARKED));

    assertThat(response.graded()).isTrue();
    assertThat(response.subjectText()).isNull();
    assertThat(response.counts()).isEqualTo(new AnswerCheckResponse.Counts(1, 1, 2));
    assertThat(response.items())
        .extracting(
            AnswerCheckResponse.Item::number,
            AnswerCheckResponse.Item::myAnswer,
            AnswerCheckResponse.Item::aiAnswer,
            AnswerCheckResponse.Item::markRead,
            AnswerCheckResponse.Item::result)
        .containsExactly(
            tuple("1", "②", null, MarkRead.O, GradeResult.CORRECT),
            tuple("2", null, null, MarkRead.X, GradeResult.WRONG),
            tuple("3", null, null, null, GradeResult.UNSURE),
            tuple("4", null, null, null, GradeResult.UNSURE));
  }

  @Test
  @DisplayName("저장하는 문제 줄에는 내 번호 · 기록 번호 · 사진 속 순서 · AI 처음 판정이 들어간다")
  @SuppressWarnings("unchecked")
  void savesRowsWithOwnerAndOrder() {
    givenAnswer(
        new AnswerGrading.Answer(
            false,
            true,
            "영어",
            List.of(item("1", "a", "a", null, "CORRECT"), item("2", "b", "c", null, "WRONG"))));

    service.create(request(AnswerCheckMode.UNMARKED));

    ArgumentCaptor<List<AnswerCheckItem>> rows = ArgumentCaptor.forClass(List.class);
    verify(answerCheckItemRepository).saveAll(rows.capture());
    assertThat(rows.getValue())
        .extracting(
            AnswerCheckItem::getAnswerCheckId,
            AnswerCheckItem::getUserId,
            AnswerCheckItem::getSortOrder,
            AnswerCheckItem::getAiResult,
            AnswerCheckItem::getResult,
            AnswerCheckItem::getEditedAt)
        .containsExactly(
            tuple(12L, USER_ID, 0, GradeResult.CORRECT, GradeResult.CORRECT, null),
            tuple(12L, USER_ID, 1, GradeResult.WRONG, GradeResult.WRONG, null));
  }

  @Test
  @DisplayName("채점했어요(MARKED)인데 O · X 를 하나도 못 읽었으면 한 번 더 묻고, 읽은 답을 쓴다")
  void retriesOnceWhenNoMarksRead() {
    AnswerGrading.Answer noMarks =
        new AnswerGrading.Answer(
            false,
            true,
            "수학",
            List.of(item("1", null, null, null, "UNSURE"), item("2", null, null, null, "UNSURE")));
    AnswerGrading.Answer withMarks =
        new AnswerGrading.Answer(
            false,
            true,
            "수학",
            List.of(item("1", null, null, "O", "CORRECT"), item("2", null, null, "x", "WRONG")));
    given(aiClient.askWithImage(any(), any(), any(), any(), eq(AnswerGrading.Answer.class)))
        .willReturn(Optional.of(noMarks), Optional.of(withMarks));

    AnswerCheckResponse response = service.create(request(AnswerCheckMode.MARKED));

    verify(aiClient, times(2))
        .askWithImage(any(), any(), any(), any(), eq(AnswerGrading.Answer.class));
    assertThat(response.counts()).isEqualTo(new AnswerCheckResponse.Counts(1, 1, 0));
  }

  @Test
  @DisplayName("다시 물어도 O · X 를 못 읽으면 처음 답(전부 애매)을 쓰고, 세 번은 묻지 않는다")
  void keepsFirstAnswerWhenRetryAlsoReadsNoMarks() {
    AnswerGrading.Answer noMarks =
        new AnswerGrading.Answer(false, true, null, List.of(item("1", null, null, null, "UNSURE")));
    given(aiClient.askWithImage(any(), any(), any(), any(), eq(AnswerGrading.Answer.class)))
        .willReturn(Optional.of(noMarks), Optional.empty());

    AnswerCheckResponse response = service.create(request(AnswerCheckMode.MARKED));

    verify(aiClient, times(2))
        .askWithImage(any(), any(), any(), any(), eq(AnswerGrading.Answer.class));
    assertThat(response.graded()).isTrue();
    assertThat(response.counts()).isEqualTo(new AnswerCheckResponse.Counts(0, 0, 1));
  }

  @Test
  @DisplayName("표시를 하나라도 읽었거나 아직 안 했어요(UNMARKED)면 다시 묻지 않는다")
  void noRetryWhenSomeMarksReadOrUnmarked() {
    givenAnswer(
        new AnswerGrading.Answer(
            false,
            true,
            null,
            List.of(item("1", null, null, "O", "CORRECT"), item("2", null, null, null, "UNSURE"))));
    service.create(request(AnswerCheckMode.MARKED));

    givenAnswer(
        new AnswerGrading.Answer(false, true, null, List.of(item("1", null, "3", null, "UNSURE"))));
    service.create(request(AnswerCheckMode.UNMARKED));

    verify(aiClient, times(2))
        .askWithImage(any(), any(), any(), any(), eq(AnswerGrading.Answer.class));
  }

  @Test
  @DisplayName("AI 가 못 답하면(꺼짐 · 실패 · 시간 초과) graded:false, UNAVAILABLE 이고 저장하지 않는다")
  void unavailableWhenNoAnswer() {
    givenAnswer(null);

    AnswerCheckResponse response = service.create(request(AnswerCheckMode.MARKED));

    assertThat(response)
        .isEqualTo(
            AnswerCheckResponse.notGraded(
                AnswerCheckResponse.UNAVAILABLE, DATE, AnswerCheckMode.MARKED));
    verifyNoInteractions(answerCheckRepository, answerCheckItemRepository);
  }

  @Test
  @DisplayName("문제 사진이 아니거나 번호 있는 문제가 하나도 없으면 NOT_PROBLEM 이고 저장하지 않는다")
  void notProblem() {
    givenAnswer(new AnswerGrading.Answer(false, false, null, List.of()));
    assertThat(service.create(request(AnswerCheckMode.UNMARKED)).reason())
        .isEqualTo(AnswerCheckResponse.NOT_PROBLEM);

    givenAnswer(
        new AnswerGrading.Answer(false, true, "수학", List.of(item("  ", "3", "4", null, "WRONG"))));
    AnswerCheckResponse response = service.create(request(AnswerCheckMode.UNMARKED));
    assertThat(response.graded()).isFalse();
    assertThat(response.reason()).isEqualTo(AnswerCheckResponse.NOT_PROBLEM);
    assertThat(response.items()).isNull();
    verifyNoInteractions(answerCheckRepository, answerCheckItemRepository);
  }

  @Test
  @DisplayName("AI 가 위기 표현이라고 하거나 읽은 글에 위기 표현이 있으면 showHelp 이고 저장하지 않는다")
  void crisisMeansHelp() {
    givenAnswer(
        new AnswerGrading.Answer(true, true, "수학", List.of(item("1", "3", "3", null, "CORRECT"))));
    assertThat(service.create(request(AnswerCheckMode.UNMARKED)))
        .isEqualTo(AnswerCheckResponse.help(DATE, AnswerCheckMode.UNMARKED));

    givenAnswer(
        new AnswerGrading.Answer(
            false, true, "국어", List.of(item("1", "죽고 싶다", null, "O", "CORRECT"))));
    AnswerCheckResponse response = service.create(request(AnswerCheckMode.MARKED));
    assertThat(response.showHelp()).isTrue();
    assertThat(response.graded()).isFalse();
    verifyNoInteractions(answerCheckRepository, answerCheckItemRepository);
  }

  @Test
  @DisplayName("사진 모양이 틀리면 AI 를 부르기 전에 400")
  void rejectsBadImage() {
    assertThatThrownBy(
            () ->
                service.create(
                    new AnswerCheckCreateRequest(
                        AnswerCheckMode.MARKED, "data:text/plain,hi", DATE)))
        .isInstanceOf(ApiException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
    verifyNoInteractions(aiClient);
  }

  @Test
  @DisplayName("그날 목록: 내 기록마다 그 기록의 문제를 붙여 최신 순서 그대로 돌려준다")
  void listsChecksWithTheirItems() {
    AnswerCheck newer = check(20L, Instant.parse("2026-10-24T12:00:00Z"));
    AnswerCheck older = check(12L, NOW);
    given(answerCheckRepository.findByUserIdAndCheckDateOrderByCreatedAtDescIdDesc(USER_ID, DATE))
        .willReturn(List.of(newer, older));
    given(
            answerCheckItemRepository
                .findByUserIdAndAnswerCheckIdInOrderByAnswerCheckIdAscSortOrderAsc(
                    USER_ID, List.of(20L, 12L)))
        .willReturn(
            List.of(
                row(31L, 12L, 0, GradeResult.CORRECT),
                row(32L, 12L, 1, GradeResult.WRONG),
                row(40L, 20L, 0, GradeResult.UNSURE)));

    List<AnswerCheckResponse> responses = service.getChecks(DATE);

    assertThat(responses).extracting(AnswerCheckResponse::id).containsExactly("20", "12");
    assertThat(responses.get(0).items())
        .extracting(AnswerCheckResponse.Item::id)
        .containsExactly("40");
    assertThat(responses.get(1).counts()).isEqualTo(new AnswerCheckResponse.Counts(1, 1, 0));
  }

  @Test
  @DisplayName("그날 기록이 없으면 빈 배열이고 문제는 찾지 않는다")
  void emptyList() {
    given(answerCheckRepository.findByUserIdAndCheckDateOrderByCreatedAtDescIdDesc(USER_ID, DATE))
        .willReturn(List.of());

    assertThat(service.getChecks(DATE)).isEmpty();
    verifyNoInteractions(answerCheckItemRepository);
  }

  @Test
  @DisplayName("기간 목록: 내 번호 · 기간으로 찾은 기록에 문제를 붙여 받은 순서(최근 날짜부터) 그대로 돌려준다")
  void listsChecksBetweenWithTheirItems() {
    LocalDate from = DATE.minusDays(6);
    AnswerCheck today = check(20L, NOW);
    AnswerCheck weekAgo = check(12L, Instant.parse("2026-10-18T09:00:00Z"));
    ReflectionTestUtils.setField(weekAgo, "checkDate", from);
    given(
            answerCheckRepository
                .findByUserIdAndCheckDateBetweenOrderByCheckDateDescCreatedAtDescIdDesc(
                    USER_ID, from, DATE))
        .willReturn(List.of(today, weekAgo));
    given(
            answerCheckItemRepository
                .findByUserIdAndAnswerCheckIdInOrderByAnswerCheckIdAscSortOrderAsc(
                    USER_ID, List.of(20L, 12L)))
        .willReturn(
            List.of(row(31L, 12L, 0, GradeResult.WRONG), row(40L, 20L, 0, GradeResult.CORRECT)));

    List<AnswerCheckResponse> responses = service.getChecksBetween(from, DATE);

    assertThat(responses)
        .extracting(AnswerCheckResponse::id, AnswerCheckResponse::date)
        .containsExactly(tuple("20", DATE), tuple("12", from));
    assertThat(responses.get(0).counts()).isEqualTo(new AnswerCheckResponse.Counts(1, 0, 0));
    assertThat(responses.get(1).counts()).isEqualTo(new AnswerCheckResponse.Counts(0, 1, 0));
    // 하루치 조회는 부르지 않습니다.
    verify(answerCheckRepository, never())
        .findByUserIdAndCheckDateOrderByCreatedAtDescIdDesc(any(), any());
  }

  @Test
  @DisplayName("기간 안에 기록이 없으면 빈 배열이고 문제는 찾지 않는다")
  void emptyRange() {
    given(
            answerCheckRepository
                .findByUserIdAndCheckDateBetweenOrderByCheckDateDescCreatedAtDescIdDesc(
                    USER_ID, DATE, DATE))
        .willReturn(List.of());

    // 같은 날을 from · to 로 보내면 하루짜리 기간입니다.
    assertThat(service.getChecksBetween(DATE, DATE)).isEmpty();
    verifyNoInteractions(answerCheckItemRepository);
  }

  @Test
  @DisplayName("시작이 끝보다 늦으면 400 이고 DB 를 부르지 않는다")
  void rejectsReversedRange() {
    assertThatThrownBy(() -> service.getChecksBetween(DATE, DATE.minusDays(1)))
        .isInstanceOf(ApiException.class)
        .hasMessage("시작 날짜는 끝 날짜보다 늦을 수 없어요")
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
    verifyNoInteractions(answerCheckRepository, answerCheckItemRepository);
  }

  @Test
  @DisplayName("31일(양 끝 포함)까지는 되고, 32일이면 400 이고 DB 를 부르지 않는다")
  void rangeUpTo31Days() {
    LocalDate from = LocalDate.of(2026, 10, 1);
    given(
            answerCheckRepository
                .findByUserIdAndCheckDateBetweenOrderByCheckDateDescCreatedAtDescIdDesc(
                    any(), any(), any()))
        .willReturn(List.of());

    // 10/1 ~ 10/31 은 31일
    assertThat(service.getChecksBetween(from, from.plusDays(30))).isEmpty();

    // 10/1 ~ 11/1 은 32일
    assertThatThrownBy(() -> service.getChecksBetween(from, from.plusDays(31)))
        .isInstanceOf(ApiException.class)
        .hasMessage("한 번에 31일까지만 조회할 수 있어요")
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
    verify(answerCheckRepository, never())
        .findByUserIdAndCheckDateBetweenOrderByCheckDateDescCreatedAtDescIdDesc(
            USER_ID, from, from.plusDays(31));
  }

  @Test
  @DisplayName("기간 목록도 로그인 확인이 먼저다 (토큰이 없으면 날짜가 틀려도 401)")
  void rangeChecksLoginFirst() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    assertThatThrownBy(() -> service.getChecksBetween(DATE, DATE.minusDays(1)))
        .isInstanceOf(ApiException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.UNAUTHORIZED);
    verifyNoInteractions(answerCheckRepository);
  }

  @Test
  @DisplayName("판정을 고치면 AI 처음 판정은 남고, 개수 · editedByUser · 정답이 바뀐 기록 전체를 돌려준다")
  void updatesItem() {
    AnswerCheck check = check(12L, NOW);
    AnswerCheckItem first = row(31L, 12L, 0, GradeResult.WRONG);
    AnswerCheckItem second = row(32L, 12L, 1, GradeResult.CORRECT);
    given(answerCheckRepository.findByIdAndUserId(12L, USER_ID)).willReturn(Optional.of(check));
    given(answerCheckItemRepository.findByIdAndAnswerCheckIdAndUserId(31L, 12L, USER_ID))
        .willReturn(Optional.of(first));
    given(answerCheckItemRepository.findByUserIdAndAnswerCheckIdOrderBySortOrderAsc(USER_ID, 12L))
        .willReturn(new ArrayList<>(List.of(first, second)));

    AnswerCheckResponse response =
        service.updateItem(
            12L, 31L, new AnswerCheckItemUpdateRequest(GradeResult.CORRECT, " x = 7 "));

    assertThat(response.counts()).isEqualTo(new AnswerCheckResponse.Counts(2, 0, 0));
    assertThat(response.items().get(0))
        .isEqualTo(
            new AnswerCheckResponse.Item(
                "31", "1", "3", "3", null, GradeResult.CORRECT, "x = 7", true));
    assertThat(response.items().get(1).editedByUser()).isFalse();
    assertThat(first.getAiResult()).isEqualTo(GradeResult.WRONG);
    assertThat(first.getEditedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("없는 기록 · 남의 기록 · 그 기록에 없는 문제를 고치려 하면 404")
  void updateNotFound() {
    given(answerCheckRepository.findByIdAndUserId(99L, USER_ID)).willReturn(Optional.empty());
    assertThatThrownBy(
            () ->
                service.updateItem(
                    99L, 31L, new AnswerCheckItemUpdateRequest(GradeResult.WRONG, null)))
        .isInstanceOf(ApiException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);

    given(answerCheckRepository.findByIdAndUserId(12L, USER_ID))
        .willReturn(Optional.of(check(12L, NOW)));
    given(answerCheckItemRepository.findByIdAndAnswerCheckIdAndUserId(77L, 12L, USER_ID))
        .willReturn(Optional.empty());
    assertThatThrownBy(
            () ->
                service.updateItem(
                    12L, 77L, new AnswerCheckItemUpdateRequest(GradeResult.WRONG, null)))
        .isInstanceOf(ApiException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verify(answerCheckItemRepository, never())
        .findByUserIdAndAnswerCheckIdOrderBySortOrderAsc(any(), any());
  }

  private static AnswerCheck check(Long id, Instant createdAt) {
    AnswerCheck check = new AnswerCheck(USER_ID, DATE, AnswerCheckMode.UNMARKED, "수학", createdAt);
    ReflectionTestUtils.setField(check, "id", id);
    return check;
  }

  private static AnswerCheckItem row(Long id, Long checkId, int sortOrder, GradeResult result) {
    AnswerCheckItem item =
        new AnswerCheckItem(
            checkId, USER_ID, sortOrder, String.valueOf(sortOrder + 1), "3", "3", null, result);
    ReflectionTestUtils.setField(item, "id", id);
    return item;
  }
}
