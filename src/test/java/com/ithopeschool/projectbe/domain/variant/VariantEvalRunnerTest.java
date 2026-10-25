package com.ithopeschool.projectbe.domain.variant;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.variant.generate.ClassificationDraft;
import com.ithopeschool.projectbe.domain.variant.generate.FakeVariantAi;
import com.ithopeschool.projectbe.domain.variant.generate.SourceClassifier;
import com.ithopeschool.projectbe.domain.variant.generate.VariantDrafts;
import com.ithopeschool.projectbe.domain.variant.generate.VariantPipeline;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

// 품질 평가 도구(VariantEvalRunner)가 제대로 도는지 가짜 AI 로 한 번 돌려 봅니다.
// (진짜 품질 평가는 VariantGenerationEvalTest 가 실제 AI 로 합니다. 여기서 나온 숫자는 의미가 없습니다)
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:variant-eval-runner;MODE=PostgreSQL",
      "app.variant.sandbox-timeout-sec=30"
    })
@Import(VariantEvalRunnerTest.TestBeans.class)
class VariantEvalRunnerTest {

  @TestConfiguration
  static class TestBeans {
    @Bean
    @Primary
    FakeVariantAi evalFakeVariantAi() {
      return new FakeVariantAi();
    }
  }

  @Autowired private FakeVariantAi ai;
  @Autowired private SourceProblemService sourceProblemService;
  @Autowired private SourceProblemRepository sourceProblemRepository;
  @Autowired private VariantRepository variantRepository;
  @Autowired private VariantPipeline pipeline;
  @Autowired private SourceClassifier classifier;

  @Test
  @DisplayName("시드 1문항 × L0 × 2번을 돌려 CSV(머리줄 + 2줄)와 레벨별 요약 · 분류기 정확도를 쓴다")
  void writesCsvAndSummary(@TempDir Path outDir) throws Exception {
    // 1번째 실행: 1차 실패(G5) 뒤 성공, 2번째 실행: 3번 모두 실패
    ai.willGenerate(VariantDrafts.shortWrongAnswer())
        .willGenerate(VariantDrafts.shortRootProduct())
        .alwaysGenerate(VariantDrafts.shortWrongAnswer());
    ai.willClassify(new ClassificationDraft("CM1-U01", "CM1-U01-T01", 0.9, false, "맞음"));

    VariantEvalRunner.Report report =
        new VariantEvalRunner(
                sourceProblemService,
                sourceProblemRepository,
                variantRepository,
                pipeline,
                classifier)
            .run(900L, 1, List.of(0), 2, true, outDir);

    List<String> lines = Files.readAllLines(report.csv(), StandardCharsets.UTF_8);
    assertThat(lines).hasSize(3);
    assertThat(lines.get(0)).contains("human_answer_correct_YN");
    assertThat(lines.get(1)).contains("\"VERIFIED\"").contains("\"2\"");
    assertThat(lines.get(2)).contains("\"FAILED\"").contains("G5");
    assertThat(report.summaryText())
        .contains("| L0 | 2 | 1 | 50.0% |")
        .contains("G5")
        .contains("단원 100.0%");
    assertThat(Files.exists(report.summary())).isTrue();
  }
}
