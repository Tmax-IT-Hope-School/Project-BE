package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.ai.dto.StudyQuizRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 확인 문제의 질문 만들기 · 답 검사 단위 테스트입니다. (AI 를 부르지 않습니다)
class StudyQuizTest {

  private static StudyQuiz.Answer.Item item(
      String question, List<String> choices, Integer answerIndex, String explanation) {
    return new StudyQuiz.Answer.Item(question, choices, answerIndex, explanation, null);
  }

  private static StudyQuiz.Answer.Item withPassage(String question, String passage) {
    return new StudyQuiz.Answer.Item(question, CHOICES, 0, "해설", passage);
  }

  private static final List<String> CHOICES = List.of("0", "1", "∞", "없다");

  private static StudyQuiz.Answer.Item good(String question) {
    return item(question, CHOICES, 0, "n 이 커지면 1/n 은 0 에 가까워져.");
  }

  private static StudyQuiz.Answer answer(StudyQuiz.Answer.Item... items) {
    return new StudyQuiz.Answer(false, false, List.of(items));
  }

  @Test
  @DisplayName("입력에는 주제 · 체크리스트 · 문제 수 · 자료 글이 들어가고, 사진이 있으면 함께 보낸다고 적는다")
  void inputHasTopicAndMaterial() {
    StudyQuizRequest request =
        new StudyQuizRequest(
            " 미적분 3단원 수열의 극한 ",
            List.of("교과서 52~60쪽 읽기", "익힘책 3단원"),
            "수열의 극한: n 이 한없이 커질 때",
            "data:image/jpeg;base64,AQID",
            null);

    assertThat(StudyQuiz.input(request))
        .isEqualTo(
            """
            주제: 미적분 3단원 수열의 극한
            체크리스트: 교과서 52~60쪽 읽기 / 익힘책 3단원
            문제 수: 3
            학생 자료(글):
            수열의 극한: n 이 한없이 커질 때
            학생 자료(사진): 함께 보낸 이미지""");
    assertThat(StudyQuiz.input(new StudyQuizRequest("영어 단어", null, " ", null, 5)))
        .isEqualTo("주제: 영어 단어\n문제 수: 5");
    assertThat(StudyQuiz.question(request).name()).isEqualTo("study_quiz");
  }

  @Test
  @DisplayName("보기 4개 · 정답 위치 · 해설이 맞는 문제만 남기고, 앞뒤 공백은 지운다")
  void keepsGoodQuestions() {
    List<StudyQuiz.Question> questions =
        StudyQuiz.from(
            answer(item(" lim (n→∞) 1/n 의 값은? ", List.of(" 0", "1 ", "∞", "없다"), 0, " 0 에 가까워져. ")),
            3);

    assertThat(questions)
        .containsExactly(
            new StudyQuiz.Question("lim (n→∞) 1/n 의 값은?", CHOICES, 0, "0 에 가까워져.", null));
  }

  @Test
  @DisplayName("지문은 앞뒤 공백을 지우고 2000자에서 자르며, 비었거나 깎아내리는 말이 있으면 지문만 비우고 문제는 남긴다")
  void cleansPassage() {
    List<StudyQuiz.Question> questions =
        StudyQuiz.from(
            answer(
                withPassage("지문 있음", "  봄이 오면 산에 들에 진달래 피네.  "),
                withPassage("긴 지문", "가".repeat(2100)),
                withPassage("빈 지문", "   "),
                withPassage("나쁜 말 지문", "넌 진짜 한심해"),
                withPassage("지문 없음", null)),
            5);

    assertThat(questions)
        .extracting(StudyQuiz.Question::passage)
        .containsExactly("봄이 오면 산에 들에 진달래 피네.", "가".repeat(2000), null, null, null);
  }

  @Test
  @DisplayName("AI 답 모양에 passage(문자열 또는 null)가 있고, 지침에 지문을 그대로 옮기라는 규칙이 있다")
  void schemaAndInstructionsHavePassage() {
    AiQuestion question =
        StudyQuiz.question(new StudyQuizRequest("국어 비문학", null, null, null, null));

    assertThat(question.schema().toString()).contains("passage");
    assertThat(question.instructions()).contains("passage").contains("그대로 옮겨");
  }

  @Test
  @DisplayName("보기가 4개가 아니거나 · 같은 보기 · 빈 보기 · 긴 보기 · 정답 위치가 없거나 밖이면 그 문제를 버린다")
  void dropsBadChoicesAndIndex() {
    List<StudyQuiz.Question> questions =
        StudyQuiz.from(
            answer(
                item("보기 3개", List.of("0", "1", "2"), 0, "해설"),
                item("같은 보기", List.of("0", "0", "1", "2"), 0, "해설"),
                item("빈 보기", List.of("0", " ", "1", "2"), 0, "해설"),
                item("긴 보기", List.of("0", "가".repeat(81), "1", "2"), 0, "해설"),
                item("정답 없음", CHOICES, null, "해설"),
                item("정답 밖", CHOICES, 4, "해설"),
                item("정답 음수", CHOICES, -1, "해설"),
                item("보기 없음", null, 0, "해설"),
                good("남는 문제")),
            3);

    assertThat(questions).extracting(StudyQuiz.Question::question).containsExactly("남는 문제");
  }

  @Test
  @DisplayName("문제 · 해설이 비었거나 길거나 깎아내리는 말이 있으면 버리고, 같은 문제는 한 번만")
  void dropsBadTexts() {
    List<StudyQuiz.Question> questions =
        StudyQuiz.from(
            answer(
                good(" "),
                good("가".repeat(201)),
                good("이것도 모르면 바보"),
                item("해설 없음", CHOICES, 0, " "),
                item("긴 해설", CHOICES, 0, "가".repeat(201)),
                item("보기에 나쁜 말", List.of("0", "1", "2", "한심"), 0, "해설"),
                good("첫 문제"),
                good("첫 문제")),
            3);

    assertThat(questions).extracting(StudyQuiz.Question::question).containsExactly("첫 문제");
  }

  @Test
  @DisplayName("문제 수(count)까지만 남기고, 하나도 안 남거나 답이 없으면 null")
  void limitsToCountOrNull() {
    assertThat(StudyQuiz.from(answer(good("1번"), good("2번"), good("3번")), 2))
        .extracting(StudyQuiz.Question::question)
        .containsExactly("1번", "2번");
    assertThat(StudyQuiz.from(answer(good(" ")), 3)).isNull();
    assertThat(StudyQuiz.from(new StudyQuiz.Answer(false, false, List.of()), 3)).isNull();
    assertThat(StudyQuiz.from(new StudyQuiz.Answer(false, false, null), 3)).isNull();
    assertThat(StudyQuiz.from(null, 3)).isNull();
  }

  @Test
  @DisplayName("단원은 앞뒤 빈칸을 지워 쓰고, 비었거나 30자를 넘으면 null (통계 탭)")
  void unitOf() {
    assertThat(StudyQuiz.unitOf(new StudyQuiz.Answer(false, false, " 함수 ", List.of())))
        .isEqualTo("함수");
    assertThat(StudyQuiz.unitOf(new StudyQuiz.Answer(false, false, " ", List.of()))).isNull();
    assertThat(StudyQuiz.unitOf(new StudyQuiz.Answer(false, false, "가".repeat(31), List.of())))
        .isNull();
    assertThat(StudyQuiz.unitOf(new StudyQuiz.Answer(false, false, List.of()))).isNull();
    assertThat(StudyQuiz.unitOf(null)).isNull();
  }
}
