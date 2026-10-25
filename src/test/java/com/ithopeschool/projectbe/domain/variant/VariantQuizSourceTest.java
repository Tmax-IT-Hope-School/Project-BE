package com.ithopeschool.projectbe.domain.variant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.variant.generate.FakeVariantAi;
import com.ithopeschool.projectbe.domain.variant.generate.QuizClassificationDraft;
import com.ithopeschool.projectbe.domain.variant.generate.SolveCheckDraft;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// 확인 문제 결과를 변형 문제의 원본으로 받는 흐름입니다. (POST /api/v1/variant/quiz-results, docs/VARIANT-SPEC.md 5-1)
//   - AI 는 가짜(FakeVariantAi), DB 는 이 테스트만 쓰는 H2 입니다. 성취기준은 진짜 파일(resources/curriculum)을 씁니다.
//   - 로컬 테스트 토큰(local-test-token)은 1번 사용자로 로그인한 것으로 봅니다.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:variant-quiz;MODE=PostgreSQL")
@AutoConfigureMockMvc
@Import(VariantQuizSourceTest.TestBeans.class)
class VariantQuizSourceTest {

  private static final String TOKEN = "local-test-token";
  // 공통국어1 "읽기" 성취기준. 아래 국어 문제의 글로 후보 검색에 걸립니다.
  private static final String KOREAN_STANDARD = "10공국1-02-01";

  @TestConfiguration
  static class TestBeans {

    @Bean
    @Primary
    MutableClock variantQuizTestClock() {
      return new MutableClock(Instant.parse("2026-10-24T12:00:00Z"));
    }

    @Bean
    @Primary
    FakeVariantAi variantQuizFakeAi() {
      return new FakeVariantAi();
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private FakeVariantAi ai;
  @Autowired private SourceProblemRepository sourceProblemRepository;
  @Autowired private ReviewItemRepository reviewItemRepository;

  @BeforeEach
  void cleanUp() {
    reviewItemRepository.deleteAll();
    sourceProblemRepository.deleteAll();
    ai.reset();
  }

  // ---- 도우미 ----

  private static String question(String text, String passage, int answer, int chosen) {
    return """
        {"question":"%s","passage":%s,"choices":["보기0","보기1","보기2","보기3"],\
        "answerIndex":%d,"chosenIndex":%d}"""
        .formatted(text, passage == null ? "null" : "\"" + passage + "\"", answer, chosen);
  }

  private static String body(String topic, boolean guessed, String... questions) {
    return """
        {"topic":"%s","solvedAt":1792841400000,"guessed":%s,"questions":[%s]}"""
        .formatted(topic, guessed, String.join(",", questions));
  }

  private ResultActions send(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/variant/quiz-results")
            .header("Authorization", "Bearer " + TOKEN)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private static SolveCheckDraft solved(Integer answerIndex) {
    return new SolveCheckDraft(answerIndex, null, "직접 풀어 봄");
  }

  private static QuizClassificationDraft math(String unit, String type, double confidence) {
    return new QuizClassificationDraft("MATH", unit, type, "", confidence, false, "이차방정식");
  }

  private static QuizClassificationDraft standard(String code, double confidence) {
    return new QuizClassificationDraft("STANDARD", "", "", code, confidence, false, "독서");
  }

  private static final String KOREAN_TOPIC = "공통국어1 논증 타당성";
  private static final String KOREAN_QUESTION = "윗글에서 논증의 타당성을 평가한 것으로 알맞은 것은?";
  private static final String MATH_QUESTION = "이차방정식 $x^2-5x+6=0$ 의 두 근의 곱은?";

  private List<SourceProblem> sources() {
    return sourceProblemRepository.findAll().stream()
        .sorted(Comparator.comparing(SourceProblem::getId))
        .toList();
  }

  // ---- 테스트 ----

  @Test
  @DisplayName("정상: 틀린 수학 문제는 원본 + 학습 항목, 맞힌 국어 문제는 성취기준 · 지문과 함께 원본만")
  void registersMathWrongAndKoreanCorrect() throws Exception {
    ai.willSolve(solved(2))
        .willClassifyQuiz(math("CM1-U05", "CM1-U05-T03", 0.9))
        .willSolve(solved(0))
        .willClassifyQuiz(standard(KOREAN_STANDARD, 0.9));

    send(body(
            KOREAN_TOPIC,
            false,
            question(MATH_QUESTION, null, 2, 1),
            question(KOREAN_QUESTION, "논증은 주장과 근거로 이루어진다.", 0, 0)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.registered").value(2))
        .andExpect(jsonPath("$.skipped.answerMismatch").value(0))
        .andExpect(jsonPath("$.skipped.guessedCorrect").value(0))
        .andExpect(jsonPath("$.skipped.unsupported").value(0));

    List<SourceProblem> saved = sources();
    assertThat(saved).hasSize(2);
    assertThat(saved).allMatch(s -> s.getUserId().equals(CurrentUserProvider.TEST_USER_ID));
    assertThat(saved).allMatch(s -> s.getOrigin() == SourceProblemOrigin.QUIZ);

    SourceProblem mathSource = saved.get(0);
    assertThat(mathSource.isMath()).isTrue();
    assertThat(mathSource.getSupportStatus()).isEqualTo(SupportStatus.SUPPORTED);
    assertThat(mathSource.getTypeCode()).isEqualTo("CM1-U05-T03");
    assertThat(mathSource.getSubject()).isEqualTo(SourceProblem.MATH_SUBJECT);
    assertThat(mathSource.isCorrect()).isFalse();
    assertThat(mathSource.getCorrectAnswer()).isEqualTo("보기2");
    assertThat(mathSource.getStudentAnswer()).isEqualTo("보기1");
    assertThat(mathSource.getChoices()).containsExactly("보기0", "보기1", "보기2", "보기3");

    SourceProblem koreanSource = saved.get(1);
    assertThat(koreanSource.isMath()).isFalse();
    assertThat(koreanSource.getSupportStatus()).isEqualTo(SupportStatus.SUPPORTED);
    assertThat(koreanSource.getStandardCode()).isEqualTo(KOREAN_STANDARD);
    assertThat(koreanSource.getSubject()).isEqualTo("공통국어1");
    assertThat(koreanSource.getUnitCode()).isNull();
    assertThat(koreanSource.getPassageText()).isEqualTo("논증은 주장과 근거로 이루어진다.");
    assertThat(koreanSource.isCorrect()).isTrue();

    // 틀린 문제에만 학습 항목 (다음 날부터 매일 검증 문제)
    List<ReviewItem> items = reviewItemRepository.findAll();
    assertThat(items).hasSize(1);
    assertThat(items.getFirst().getSourceProblemId()).isEqualTo(mathSource.getId());
    assertThat(items.getFirst().getStatus()).isEqualTo(ReviewItemStatus.ACTIVE);

    // 정답 확인 프롬프트에는 지문과 보기가 가고, 정답 번호는 가지 않습니다.
    assertThat(ai.solveInputs.get(1)).contains("논증은 주장과 근거로").contains("0) 보기0");
    assertThat(ai.solveInputs).noneMatch(input -> input.contains("answerIndex"));
    // 분류 프롬프트(user 칸)에는 공부한 주제와 지문이 갑니다.
    assertThat(ai.quizClassifyInputs.get(1)).contains(KOREAN_TOPIC).contains("논증은 주장과 근거로");
  }

  @Test
  @DisplayName("찍은 것 같은 결과: 맞힌 문제는 AI 도 부르지 않고 건너뛰고, 틀린 문제만 원본으로")
  void guessedKeepsOnlyWrong() throws Exception {
    ai.willSolve(solved(3)).willClassifyQuiz(math("CM1-U05", "CM1-U05-T03", 0.9));

    send(body(
            KOREAN_TOPIC,
            true,
            question(MATH_QUESTION + " (맞힘)", null, 2, 2),
            question(MATH_QUESTION + " (틀림)", null, 3, 0)))
        .andExpect(jsonPath("$.registered").value(1))
        .andExpect(jsonPath("$.skipped.guessedCorrect").value(1));

    assertThat(ai.solveInputs).hasSize(1);
    assertThat(sources()).hasSize(1);
    assertThat(sources().getFirst().isCorrect()).isFalse();
  }

  @Test
  @DisplayName("정답 확인: AI 가 다시 푼 답이 다르거나 하나로 못 정하면 저장하지 않고 분류도 하지 않는다")
  void answerMismatchIsSkipped() throws Exception {
    ai.willSolve(solved(1)).willSolve(solved(null));

    send(body(
            KOREAN_TOPIC,
            false,
            question(MATH_QUESTION, null, 2, 1),
            question(MATH_QUESTION + "?", null, 0, 0)))
        .andExpect(jsonPath("$.registered").value(0))
        .andExpect(jsonPath("$.skipped.answerMismatch").value(2));

    assertThat(sources()).isEmpty();
    assertThat(ai.quizClassifyInputs).isEmpty();
  }

  @Test
  @DisplayName("범위 밖: 어디에도 안 맞음 · 후보에 없는 성취기준 두 번 · 자신 없는 수학 밖 과목은 저장하지 않는다")
  void outOfScopeIsSkipped() throws Exception {
    ai.willSolve(solved(0))
        .willClassifyQuiz(new QuizClassificationDraft("NONE", "", "", "", 0.9, false, "정보 과목"))
        .willSolve(solved(0))
        .willClassifyQuiz(standard("99없는-01-01", 0.9))
        .willClassifyQuiz(standard("99없는-01-02", 0.9))
        .willSolve(solved(0))
        .willClassifyQuiz(standard(KOREAN_STANDARD, 0.3));

    send(body(
            KOREAN_TOPIC,
            false,
            question("알고리즘의 시간 복잡도는?", null, 0, 1),
            question(KOREAN_QUESTION, null, 0, 1),
            question(KOREAN_QUESTION + "!", null, 0, 1)))
        .andExpect(jsonPath("$.registered").value(0))
        .andExpect(jsonPath("$.skipped.unsupported").value(3));

    assertThat(sources()).isEmpty();
    assertThat(reviewItemRepository.count()).isZero();
  }

  @Test
  @DisplayName("수학인데 분류체계 밖 코드를 두 번 고르면 사람이 확인(needs_review), 그림이 필요하면 저장만 — 둘 다 학습 항목 없음")
  void mathNeedsReviewAndFigure() throws Exception {
    ai.willSolve(solved(2))
        .willClassifyQuiz(math("CM1-U99", "CM1-U99-T01", 0.9))
        .willClassifyQuiz(math("CM1-U05", "CM1-U04-T01", 0.9))
        .willSolve(solved(2))
        .willClassifyQuiz(
            new QuizClassificationDraft("MATH", "CM1-U05", "CM1-U05-T03", "", 0.9, true, "그래프"));

    send(body(
            KOREAN_TOPIC,
            false,
            question(MATH_QUESTION, null, 2, 1),
            question(MATH_QUESTION + " 그래프", null, 2, 1)))
        .andExpect(jsonPath("$.registered").value(2));

    List<SourceProblem> saved = sources();
    assertThat(saved.get(0).getSupportStatus()).isEqualTo(SupportStatus.NEEDS_REVIEW);
    assertThat(saved.get(1).getSupportStatus()).isEqualTo(SupportStatus.UNSUPPORTED_FIGURE);
    assertThat(reviewItemRepository.count()).isZero();
    // 두 번째 분류 프롬프트에는 직전 실패 사유가 들어갑니다.
    assertThat(ai.quizClassifyInputs.get(1)).contains("CM1-U99");
  }

  @Test
  @DisplayName("AI 가 꺼져 있거나 답하지 않으면 정답을 확인할 수 없어 저장하지 않는다")
  void unavailableAiIsSkipped() throws Exception {
    ai.disable();
    send(body(KOREAN_TOPIC, false, question(MATH_QUESTION, null, 2, 1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.skipped.unsupported").value(1));
    assertThat(ai.solveInputs).isEmpty();

    ai.reset();
    // 정답 확인은 답했지만 분류가 답하지 않음 / 정답 확인부터 답하지 않음
    ai.willSolve(solved(2));
    send(body(
            KOREAN_TOPIC,
            false,
            question(MATH_QUESTION, null, 2, 1),
            question(MATH_QUESTION + "?", null, 2, 1)))
        .andExpect(jsonPath("$.registered").value(0))
        .andExpect(jsonPath("$.skipped.unsupported").value(2));

    assertThat(sources()).isEmpty();
  }

  @Test
  @DisplayName("로그인하지 않으면 401, 저장하지 않는다")
  void requiresLogin() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/variant/quiz-results")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(KOREAN_TOPIC, false, question(MATH_QUESTION, null, 2, 1))))
        .andExpect(status().isUnauthorized());
    assertThat(sources()).isEmpty();
  }
}
