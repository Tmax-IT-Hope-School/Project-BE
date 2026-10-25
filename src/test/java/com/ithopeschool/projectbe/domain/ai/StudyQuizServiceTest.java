package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.ai.dto.StudyQuizRequest;
import com.ithopeschool.projectbe.domain.ai.dto.StudyQuizResponse;
import com.ithopeschool.projectbe.domain.curriculum.CurriculumService;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// Service 단위 테스트: AI 를 가짜로 바꿔서 위기 표현 · 사진 검사 · 못 만듦 · 공부 아님 · 만든 문제를 봅니다.
class StudyQuizServiceTest {

  private static final Long USER_ID = 7L;
  // 작은 jpeg 처럼 보이는 사진 (내용은 상관없습니다. 모양만 봅니다)
  private static final String IMAGE =
      "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(new byte[] {1, 2, 3, 4});

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final AiClient aiClient = mock(AiClient.class);
  // 교육과정 줄은 가짜입니다. 따로 정하지 않으면 빈 목록(학년을 모름)입니다.
  private final CurriculumService curriculumService = mock(CurriculumService.class);
  private final StudyQuizService service =
      new StudyQuizService(currentUserProvider, aiClient, curriculumService);

  private static final StudyQuiz.Answer.Item ITEM =
      new StudyQuiz.Answer.Item(
          "lim (n→∞) 1/n 의 값은?", List.of("0", "1", "∞", "없다"), 0, "0 에 가까워져.", null);

  private static StudyQuizRequest request(String topic, String material, String image) {
    return new StudyQuizRequest(topic, null, material, image, null);
  }

  @BeforeEach
  void loggedIn() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
  }

  @Test
  @DisplayName("주제 · 체크리스트 · 자료 글에 위기 표현이 있으면 AI 를 부르지 않고 showHelp")
  void crisisSkipsAi() {
    assertThat(service.make(request("죽고 싶어", null, null))).isEqualTo(StudyQuizResponse.help());
    assertThat(service.make(request("수학", "다 그만두고 죽고 싶어", null)))
        .isEqualTo(StudyQuizResponse.help());
    assertThat(service.make(new StudyQuizRequest("수학", List.of("죽고 싶어"), null, null, null)))
        .isEqualTo(StudyQuizResponse.help());
    verifyNoInteractions(aiClient);
  }

  @Test
  @DisplayName("사진이 data URL 이 아니면 400 이고 AI 를 부르지 않는다")
  void rejectsBadImage() {
    assertThatThrownBy(() -> service.make(request("수학", null, "aGVsbG8=")))
        .isInstanceOf(ApiException.class)
        .hasMessage("사진은 jpeg · png · webp 의 data URL 로 보내 주세요");
    verifyNoInteractions(aiClient);
  }

  @Test
  @DisplayName("사진이 없으면 글로만 묻고(ASSIST 한도), 검사한 문제를 돌려준다. 못 받으면 questions: null")
  void asksWithTextOnly() {
    given(aiClient.ask(eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(StudyQuiz.Answer.class)))
        .willReturn(Optional.empty())
        .willReturn(Optional.of(new StudyQuiz.Answer(false, false, List.of(ITEM))));

    assertThat(service.make(request("수열의 극한", null, null))).isEqualTo(StudyQuizResponse.notMade());
    StudyQuizResponse made = service.make(request("수열의 극한", null, null));
    assertThat(made.showHelp()).isFalse();
    assertThat(made.reason()).isNull();
    assertThat(made.questions())
        .containsExactly(
            new StudyQuiz.Question(
                "lim (n→∞) 1/n 의 값은?", List.of("0", "1", "∞", "없다"), 0, "0 에 가까워져.", null));
    verify(aiClient, never()).askWithImage(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("사진에서 옮긴 지문은 그대로 돌려주고, 지문에 위기 표현이 있으면 showHelp")
  void passageFromPhoto() {
    StudyQuiz.Answer.Item reading =
        new StudyQuiz.Answer.Item(
            "윗글의 중심 내용은?",
            List.of("봄", "여름", "가을", "겨울"),
            0,
            "첫 문장이 봄을 말해.",
            "봄이 오면 산에 들에 진달래 피네.");
    StudyQuiz.Answer.Item crisis =
        new StudyQuiz.Answer.Item(
            "윗글의 중심 내용은?", List.of("봄", "여름", "가을", "겨울"), 0, "첫 문장을 봐.", "오늘은 그냥 죽고 싶어.");
    given(
            aiClient.askWithImage(
                eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(IMAGE), eq(StudyQuiz.Answer.class)))
        .willReturn(Optional.of(new StudyQuiz.Answer(false, false, List.of(reading))))
        .willReturn(Optional.of(new StudyQuiz.Answer(false, false, List.of(crisis))));

    assertThat(service.make(request("국어 비문학", null, IMAGE)).questions())
        .extracting(StudyQuiz.Question::passage)
        .containsExactly("봄이 오면 산에 들에 진달래 피네.");
    assertThat(service.make(request("국어 비문학", null, IMAGE))).isEqualTo(StudyQuizResponse.help());
  }

  @Test
  @DisplayName("사진이 있으면 사진과 함께 묻고, 공부 내용이 아니라고 하면 reason: NOT_STUDY, 위기라고 하면 showHelp")
  void asksWithImage() {
    given(
            aiClient.askWithImage(
                eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(IMAGE), eq(StudyQuiz.Answer.class)))
        .willReturn(Optional.of(new StudyQuiz.Answer(false, false, List.of(ITEM))))
        .willReturn(Optional.of(new StudyQuiz.Answer(false, true, List.of())))
        .willReturn(Optional.of(new StudyQuiz.Answer(true, false, List.of())));

    assertThat(service.make(request("교과서 52쪽", null, IMAGE)).questions()).hasSize(1);
    assertThat(service.make(request("교과서 52쪽", null, IMAGE)))
        .isEqualTo(StudyQuizResponse.notStudy());
    assertThat(service.make(request("교과서 52쪽", null, IMAGE))).isEqualTo(StudyQuizResponse.help());
    verify(aiClient, never()).ask(any(), any(), any(), any());
  }

  @Test
  @DisplayName("학년을 알면 교육과정 · 관련 성취기준 줄을 주제 · 체크리스트로 골라 AI 입력에 붙인다")
  void addsCurriculumLines() {
    given(curriculumService.quizLines(USER_ID, "미적분 극한 교과서 52쪽"))
        .willReturn(
            List.of(
                "교육과정: 고2 · 2022 개정 교육과정",
                "관련 성취기준(교육부 고시 원문):",
                "- [12미적Ⅰ-01-02] 미적분Ⅰ · 함수의 극한과 연속: 함수의 극한값을 구할 수 있다."));
    given(aiClient.ask(eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(StudyQuiz.Answer.class)))
        .willReturn(Optional.of(new StudyQuiz.Answer(false, false, List.of(ITEM))));

    service.make(new StudyQuizRequest("미적분 극한", List.of("교과서 52쪽"), null, null, null));

    ArgumentCaptor<AiQuestion> question = ArgumentCaptor.forClass(AiQuestion.class);
    verify(aiClient).ask(eq(USER_ID), eq(GptUsageKind.ASSIST), question.capture(), any());
    assertThat(question.getValue().input())
        .contains("교육과정: 고2 · 2022 개정 교육과정")
        .contains("[12미적Ⅰ-01-02]");
    assertThat(question.getValue().instructions()).contains("교육과정");
  }
}
