package com.ithopeschool.projectbe.domain.variant.generate;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.curriculum.CurriculumLibrary;
import com.ithopeschool.projectbe.domain.variant.ClassificationSource;
import com.ithopeschool.projectbe.domain.variant.SourceProblem;
import com.ithopeschool.projectbe.domain.variant.SourceProblemOrigin;
import com.ithopeschool.projectbe.domain.variant.SourceProblemRepository;
import com.ithopeschool.projectbe.domain.variant.Variant;
import com.ithopeschool.projectbe.domain.variant.VariantChoice;
import com.ithopeschool.projectbe.domain.variant.VariantFormat;
import com.ithopeschool.projectbe.domain.variant.VariantRepository;
import com.ithopeschool.projectbe.domain.variant.VariantSettings;
import com.ithopeschool.projectbe.domain.variant.VariantStatus;
import com.ithopeschool.projectbe.domain.variant.VariantTaxonomy;
import com.ithopeschool.projectbe.domain.variant.VariantVerificationMethod;
import com.ithopeschool.projectbe.domain.variant.sandbox.VariantSandbox;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

// 수학이 아닌 과목의 변형 파이프라인 테스트입니다. (docs/VARIANT-SPEC.md 6-4)
//   생성 → G1 모양 → G2 형식(글 선지 MC5) → G6 중복 → G7 독립 풀이(항상) → 저장
// AI 는 가짜(FakeVariantAi)입니다. 계산할 식이 없어서 샌드박스는 부르지 않습니다. 성취기준은 진짜 파일을 읽습니다.
@DataJpaTest
class TextVariantPipelineTest {

  private static final Instant NOW = Instant.parse("2026-10-25T01:00:00Z");
  // G7 설정을 꺼 둡니다. 수학이 아닌 과목은 그래도 G7 을 해야 합니다.
  private static final VariantSettings SETTINGS =
      new VariantSettings(3, false, 30, 256, 10, 2, 3, 5, 10, 7, 0.6, 20);
  private static final String STANDARD = "10공국1-02-01";
  private static final String PASSAGE = "도시의 녹지는 여름철 기온을 낮춘다. 그러나 녹지를 늘리는 데에는 비용이 든다.";

  @Autowired private VariantRepository variantRepository;
  @Autowired private SourceProblemRepository sourceProblemRepository;

  private FakeVariantAi ai;
  private SourceProblem source;

  @BeforeEach
  void setUp() {
    ai = new FakeVariantAi();
    SourceProblem problem =
        new SourceProblem(
            7L,
            "윗글의 내용과 일치하는 것은?",
            List.of("녹지는 기온을 높인다.", "녹지는 기온을 낮춘다.", "녹지는 비용이 들지 않는다.", "녹지는 겨울에만 쓸모 있다."),
            "녹지는 기온을 낮춘다.",
            "녹지는 기온을 높인다.",
            false,
            false,
            NOW,
            NOW,
            SourceProblemOrigin.QUIZ,
            PASSAGE);
    problem.markSupportedByStandard("공통국어1", STANDARD, 0.9);
    source = sourceProblemRepository.save(problem);
  }

  private VariantPipeline pipeline() {
    return new VariantPipeline(
        ai,
        new VariantPrompts(),
        new VariantSandbox(SETTINGS),
        variantRepository,
        new VariantTaxonomy(),
        new CurriculumLibrary(),
        SETTINGS,
        Clock.fixed(NOW, ZoneOffset.UTC),
        new Random(1));
  }

  private List<Variant> attempts(int level) {
    return variantRepository.findBySourceProblemIdAndLevelOrderByIdAsc(source.getId(), level);
  }

  // 정답이 두 번째 선지(번호 1)인 글 선지 변형
  private static TextVariantDraft draft(String problemText, List<String> choices) {
    return new TextVariantDraft(
        problemText,
        choices,
        1,
        List.of("기온 효과를 반대로 읽음", "", "비용 문장을 놓침", "계절 조건을 지어냄", "글에 없는 내용"),
        "첫 문장에 녹지가 여름철 기온을 낮춘다고 나와 있다.",
        "같은 지문에서 묻는 대상만 바꾼 L0");
  }

  private static TextVariantDraft goodDraft() {
    return draft(
        "윗글에서 녹지를 늘릴 때의 단점으로 말한 것은?",
        List.of("기온이 오른다.", "비용이 든다.", "소음이 커진다.", "겨울에 춥다.", "공기가 나빠진다."));
  }

  @Test
  @DisplayName("G7 이 정답과 같으면 VERIFIED 로 저장한다 (검증 방법 AI_SOLVE, 검증 코드 없음, G3 ~ G5 건너뜀)")
  void verifiesByIndependentSolve() {
    ai.willGenerateText(goodDraft()).willSolve(new SolveCheckDraft(1, null, "둘째 문장"));

    Variant variant = pipeline().generate(source, 0).orElseThrow();

    assertThat(variant.getStatus()).isEqualTo(VariantStatus.VERIFIED);
    assertThat(variant.getVerificationMethod()).isEqualTo(VariantVerificationMethod.AI_SOLVE);
    assertThat(variant.getFormat()).isEqualTo(VariantFormat.MC5);
    assertThat(variant.getVerificationCode()).isEmpty();
    assertThat(variant.getPromptVersion()).isEqualTo("variant_text_v1");
    assertThat(variant.getVerificationLog())
        .contains("\"G3\":\"skip\"", "\"G4\":\"skip\"", "\"G5\":\"skip\"", "\"G7\":\"pass\"");
    // 선지를 섞어도 정답 번호는 같은 글을 가리키고, 화면용 글자는 선지 글 그대로입니다.
    assertThat(variant.getChoices()).hasSize(5);
    assertThat(variant.getChoices().get(variant.getAnswerIndex()).getLatex()).isEqualTo("비용이 든다.");
    assertThat(variant.getChoices().get(variant.getAnswerIndex()).getRationale()).isEmpty();
    assertThat(variant.getChoices())
        .allMatch(choice -> choice.getLatex().equals(choice.getValueExpr()));
    assertThat(variant.answerLatex()).isEqualTo("비용이 든다.");
    assertThat(variant.getComputedLatex()).isEqualTo("비용이 든다.");
  }

  @Test
  @DisplayName("생성 프롬프트에 지문 · 성취기준이 들어가고, 독립 풀이 프롬프트에는 지문은 있지만 정답 · 해설은 없다")
  void promptsCarryPassageButHideAnswer() {
    ai.willGenerateText(goodDraft()).willSolve(new SolveCheckDraft(1, null, "둘째 문장"));

    pipeline().generate(source, 0);

    assertThat(ai.variantInputs).isEmpty();
    assertThat(ai.textVariantInputs.getFirst())
        .contains(PASSAGE, STANDARD, "공통국어1", "논증의 타당성", "L0 재인출");
    assertThat(ai.solveInputs.getFirst())
        .contains(PASSAGE, "0) 기온이 오른다.", "1) 비용이 든다.")
        .doesNotContain("첫 문장에 녹지가", "비용 문장을 놓침");
  }

  @Test
  @DisplayName("G7 설정이 꺼져 있어도 수학이 아닌 과목은 독립 풀이를 하고, 답이 다르면 G7 에서 떨어진다")
  void independentSolveIsAlwaysOn() {
    ai.willGenerateText(goodDraft())
        .willSolve(new SolveCheckDraft(3, null, "다르게 읽힘"))
        .willGenerateText(goodDraft())
        .willSolve(new SolveCheckDraft(1, null, "같음"));

    Optional<Variant> variant = pipeline().generate(source, 0);

    assertThat(variant).isPresent();
    List<Variant> all = attempts(0);
    assertThat(all)
        .extracting(Variant::getStatus)
        .containsExactly(VariantStatus.REJECTED, VariantStatus.VERIFIED);
    assertThat(all.getFirst().getRejectionReason()).startsWith("G7");
    assertThat(all.getFirst().getVerificationMethod())
        .isEqualTo(VariantVerificationMethod.AI_SOLVE);
    assertThat(ai.textVariantInputs.get(1)).contains("G7");
  }

  @Test
  @DisplayName("독립 풀이 답을 받지 못하면 G7 에서 떨어진다 (검증 안 된 문항을 내지 않음)")
  void noIndependentAnswerFailsG7() {
    ai.willGenerateText(goodDraft()).willSolve(null);

    pipeline().generate(source, 0);

    assertThat(attempts(0).getFirst().getRejectionReason()).startsWith("G7");
  }

  @Test
  @DisplayName("띄어쓰기만 다른 선지가 있으면 G2 에서 떨어진다")
  void sameTextChoicesFailG2() {
    ai.willGenerateText(
            draft(
                "윗글에서 녹지를 늘릴 때의 단점으로 말한 것은?",
                List.of("비용이 든다.", "비용이  든다.", "소음이 커진다.", "겨울에 춥다.", "공기가 나빠진다.")))
        .willGenerateText(goodDraft())
        .willSolve(new SolveCheckDraft(1, null, "같음"));

    pipeline().generate(source, 0);

    assertThat(attempts(0).getFirst().getRejectionReason()).startsWith("G2").contains("같은 글");
  }

  @Test
  @DisplayName("선지가 5개가 아니면 G2, 원본과 같은 문제면 G6 에서 떨어진다")
  void formatAndDuplicateGates() {
    ai.willGenerateText(
            draft("윗글에서 녹지를 늘릴 때의 단점으로 말한 것은?", List.of("기온이 오른다.", "비용이 든다.", "소음이 커진다.")))
        .willGenerateText(
            draft(
                source.getProblemText(),
                List.of("기온이 오른다.", "기온이 내린다.", "소음이 커진다.", "겨울에 춥다.", "공기가 나빠진다.")))
        .willGenerateText(goodDraft())
        .willSolve(new SolveCheckDraft(1, null, "같음"));

    pipeline().generate(source, 0);

    List<Variant> all = attempts(0);
    assertThat(all.get(0).getRejectionReason()).startsWith("G2");
    assertThat(all.get(1).getRejectionReason()).startsWith("G6");
    assertThat(all.get(2).getStatus()).isEqualTo(VariantStatus.VERIFIED);
  }

  @Test
  @DisplayName("AI 가 답하지 않으면 G1 에서 떨어지고, 형식은 비어 있다")
  void noAnswerFailsG1() {
    ai.willGenerateText(null);

    pipeline().generate(source, 0);

    Variant first = attempts(0).getFirst();
    assertThat(first.getRejectionReason()).startsWith("G1");
    assertThat(first.getFormat()).isNull();
    assertThat(first.getPromptVersion()).isEqualTo("variant_text_v1");
  }

  @Test
  @DisplayName("성취기준 코드가 없는 원본(수학)은 지금처럼 수학 프롬프트를 쓴다")
  void mathSourceKeepsMathPath() {
    SourceProblem math =
        new SourceProblem(7L, "두 근의 곱을 구하시오.", List.of(), "6", "5", false, false, NOW, NOW);
    math.markSupported("CM1-U05", "CM1-U05-T03", ClassificationSource.UPSTREAM, null);
    SourceProblem saved = sourceProblemRepository.save(math);
    ai.willNotAnswer();

    pipeline().generate(saved, 0);

    assertThat(ai.variantInputs).hasSize(3);
    assertThat(ai.textVariantInputs).isEmpty();
    assertThat(variantRepository.findBySourceProblemIdAndLevelOrderByIdAsc(saved.getId(), 0))
        .allMatch(v -> v.getVerificationMethod() == VariantVerificationMethod.SYMJA);
  }

  @Test
  @DisplayName("선지 글이 한도(200자)를 넘으면 G1 에서 떨어진다")
  void tooLongChoiceFailsG1() {
    String tooLong = "가".repeat(VariantChoice.MAX_VALUE_LENGTH + 1);
    ai.willGenerateText(
        draft(
            "윗글에서 녹지를 늘릴 때의 단점으로 말한 것은?",
            List.of(tooLong, "비용이 든다.", "소음이 커진다.", "겨울에 춥다.", "공기가 나빠진다.")));

    pipeline().generate(source, 0);

    assertThat(attempts(0).getFirst().getRejectionReason()).startsWith("G1").contains("200");
  }
}
