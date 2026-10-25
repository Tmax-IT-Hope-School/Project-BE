package com.ithopeschool.projectbe.domain.variant.generate;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.variant.ClassificationSource;
import com.ithopeschool.projectbe.domain.variant.SourceProblem;
import com.ithopeschool.projectbe.domain.variant.SourceProblemRepository;
import com.ithopeschool.projectbe.domain.variant.Variant;
import com.ithopeschool.projectbe.domain.variant.VariantChoice;
import com.ithopeschool.projectbe.domain.variant.VariantFormat;
import com.ithopeschool.projectbe.domain.variant.VariantRepository;
import com.ithopeschool.projectbe.domain.variant.VariantSettings;
import com.ithopeschool.projectbe.domain.variant.VariantStatus;
import com.ithopeschool.projectbe.domain.variant.VariantTaxonomy;
import com.ithopeschool.projectbe.domain.variant.sandbox.VariantSandbox;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

// 생성 → 검사(G1 ~ G7) → 저장 파이프라인 테스트입니다. (VAR-09)
// AI 는 가짜(FakeVariantAi)이고, 샌드박스는 진짜 프로세스를 띄웁니다. DB 는 테스트용 H2 입니다.
@DataJpaTest
class VariantPipelineTest {

  private static final Instant NOW = Instant.parse("2026-10-25T01:00:00Z");
  // 전체 빌드 중에도 샌드박스가 넉넉히 끝나도록 30초
  private static final VariantSettings SETTINGS =
      new VariantSettings(3, false, 30, 256, 10, 2, 3, 5, 10, 7, 0.6, 20);
  private static final VariantSettings WITH_G7 =
      new VariantSettings(3, true, 30, 256, 10, 2, 3, 5, 10, 7, 0.6, 20);

  @Autowired private VariantRepository variantRepository;
  @Autowired private SourceProblemRepository sourceProblemRepository;

  private final VariantPrompts prompts = new VariantPrompts();
  private final VariantTaxonomy taxonomy = new VariantTaxonomy();
  private FakeVariantAi ai;
  private SourceProblem source;

  @BeforeEach
  void setUp() {
    ai = new FakeVariantAi();
    SourceProblem problem =
        new SourceProblem(
            7L,
            "이차방정식 $x^2-5x+k=0$의 두 근의 곱이 6일 때, 상수 $k$의 값을 구하시오.",
            List.of(),
            "6",
            "5",
            false,
            false,
            NOW,
            NOW);
    problem.markSupported("CM1-U05", "CM1-U05-T03", ClassificationSource.UPSTREAM, null);
    source = sourceProblemRepository.save(problem);
  }

  private VariantPipeline pipeline(VariantSettings settings) {
    return new VariantPipeline(
        ai,
        prompts,
        new VariantSandbox(settings),
        variantRepository,
        taxonomy,
        settings,
        Clock.fixed(NOW, ZoneOffset.UTC),
        new Random(1));
  }

  private List<Variant> attempts(int level) {
    return variantRepository.findBySourceProblemIdAndLevelOrderByIdAsc(source.getId(), level);
  }

  @Test
  @DisplayName("1차에 정답이 틀리면 떨어지고, 2차 프롬프트에 실패 사유가 붙어 성공한다 (REJECTED 1, VERIFIED 1)")
  void retriesWithFailureReason() {
    ai.willGenerate(VariantDrafts.shortWrongAnswer())
        .willGenerate(VariantDrafts.shortRootProduct());

    Optional<Variant> variant = pipeline(SETTINGS).generate(source, 0);

    assertThat(variant).isPresent();
    assertThat(variant.get().getStatus()).isEqualTo(VariantStatus.VERIFIED);
    assertThat(variant.get().getShortAnswer()).isEqualTo(10);
    assertThat(variant.get().getGenerationAttempt()).isEqualTo(2);
    assertThat(variant.get().getPromptVersion()).isEqualTo("variant_v2");
    assertThat(variant.get().getVerificationLog()).contains("\"G5\":\"pass\"");

    List<Variant> all = attempts(0);
    assertThat(all)
        .extracting(Variant::getStatus)
        .containsExactly(VariantStatus.REJECTED, VariantStatus.VERIFIED);
    assertThat(all.getFirst().getRejectionReason()).startsWith("G5").contains("12");
    assertThat(ai.variantInputs.get(0)).contains("[직전 시도의 실패 사유").contains("(없음)");
    assertThat(ai.variantInputs.get(1)).contains("G5");
  }

  @Test
  @DisplayName("3번 모두 떨어지면 빈 값이고 REJECTED 3건이 남으며, 오늘은 더 만들지 않는다")
  void givesUpAfterThreeFailures() {
    ai.alwaysGenerate(VariantDrafts.shortWrongAnswer());
    VariantPipeline pipeline = pipeline(SETTINGS);

    assertThat(pipeline.generate(source, 0)).isEmpty();
    assertThat(attempts(0)).hasSize(3).allMatch(v -> v.getStatus() == VariantStatus.REJECTED);
    assertThat(pipeline.exhaustedToday(source.getId(), 0, LocalDate.parse("2026-10-25"))).isTrue();
    assertThat(pipeline.exhaustedToday(source.getId(), 1, LocalDate.parse("2026-10-25"))).isFalse();
  }

  @Test
  @DisplayName("AI 가 답하지 않으면 G1 에서 떨어진다")
  void noAnswerFailsG1() {
    ai.willNotAnswer().willGenerate(VariantDrafts.shortRootProduct());

    assertThat(pipeline(SETTINGS).generate(source, 0)).isPresent();
    assertThat(attempts(0).getFirst().getRejectionReason()).startsWith("G1");
  }

  @Test
  @DisplayName("원본과 같은 문제(같은 지문)는 G6 에서 떨어진다")
  void sameAsSourceFailsG6() {
    ai.willGenerate(VariantDrafts.shortRootProduct(source.getProblemText()))
        .willGenerate(VariantDrafts.shortRootProduct());

    pipeline(SETTINGS).generate(source, 0);

    assertThat(attempts(0).getFirst().getRejectionReason()).startsWith("G6");
  }

  @Test
  @DisplayName("이미 만든 변형과 같은 문제도 G6 에서 떨어지고, 이미 만든 변형은 다음 프롬프트의 '피해야 할 변형' 에 들어간다")
  void sameAsExistingVariantFailsG6() {
    ai.willGenerate(VariantDrafts.shortRootProduct());
    pipeline(SETTINGS).generate(source, 0);

    ai.willGenerate(VariantDrafts.shortRootProduct());
    assertThat(pipeline(SETTINGS).generate(source, 0)).isEmpty();
    assertThat(attempts(0).get(1).getRejectionReason()).startsWith("G6");
    assertThat(ai.variantInputs.get(1)).contains("1) 이차방정식의 두 근의 합이");
  }

  @Test
  @DisplayName("G7 을 켜면 따로 푼 답이 다를 때 떨어지고, 같으면 통과한다")
  void independentSolveCheck() {
    ai.willGenerate(VariantDrafts.shortRootProduct())
        .willSolve(new SolveCheckDraft(null, 9, "다르게 풀림"))
        .willGenerate(VariantDrafts.shortRootProduct("두 근의 합이 $7$이고 차가 $3$일 때 곱을 구하시오."))
        .willSolve(new SolveCheckDraft(null, 10, "같음"));

    Optional<Variant> variant = pipeline(WITH_G7).generate(source, 0);

    assertThat(variant).isPresent();
    assertThat(attempts(0).getFirst().getRejectionReason()).startsWith("G7");
    assertThat(variant.get().getVerificationLog()).contains("\"G7\":\"pass\"");
  }

  @Test
  @DisplayName("5지선다를 통과하면 선지와 선지 해설을 같은 순서로 섞고, 정답 번호와 LaTeX 가 섞인 값과 맞는다")
  void shufflesChoicesConsistently() {
    ai.willGenerate(VariantDrafts.mc5Inequality());

    Variant variant = pipeline(SETTINGS).generate(source, 0).orElseThrow();

    assertThat(variant.getFormat()).isEqualTo(VariantFormat.MC5);
    List<VariantChoice> choices = variant.getChoices();
    assertThat(choices).hasSize(5);
    VariantChoice answer = choices.get(variant.getAnswerIndex());
    assertThat(answer.getValueExpr()).isEqualTo("1 < x < 3");
    assertThat(answer.getRationale()).isEmpty();
    assertThat(answer.getLatex()).isEqualTo("1 < x < 3");
    for (VariantChoice choice : choices) {
      if (choice.getValueExpr().equals("x < 1 || x > 3")) {
        assertThat(choice.getRationale()).isEqualTo("부등호 방향을 반대로 읽음");
        assertThat(choice.getLatex()).contains("또는");
      }
    }
    assertThat(variant.answerLatex()).isEqualTo("1 < x < 3");
  }

  @Test
  @DisplayName("L-1 은 [1단계] · [2단계] 표지가 없으면 G2 에서 떨어지고, 있으면 통과한다")
  void scaffoldMarkers() {
    ai.willGenerate(VariantDrafts.shortRootProduct()).willGenerate(VariantDrafts.scaffolded());

    Variant variant = pipeline(SETTINGS).generate(source, -1).orElseThrow();

    assertThat(variant.getShortAnswer()).isEqualTo(3);
    assertThat(attempts(-1).getFirst().getRejectionReason()).startsWith("G2");
  }

  @Test
  @DisplayName("계산 없이 정답만 적은 검증 코드는 G3 에서 떨어진다")
  void hardcodedCodeFailsG3() {
    VariantDraft good = VariantDrafts.shortRootProduct();
    ai.willGenerate(
            new VariantDraft(
                good.problemText(), "SHORT", null, null, 10, null, good.solution(), "10", "L0"))
        .willGenerate(good);

    pipeline(SETTINGS).generate(source, 0);

    assertThat(attempts(0).getFirst().getRejectionReason()).startsWith("G3").contains("하드코딩");
  }

  @Test
  @DisplayName("AI 가 꺼져 있으면 만들지 않고 아무것도 저장하지 않는다")
  void disabledAiDoesNothing() {
    ai.disable();

    assertThat(pipeline(SETTINGS).generate(source, 0)).isEmpty();
    assertThat(attempts(0)).isEmpty();
  }
}
