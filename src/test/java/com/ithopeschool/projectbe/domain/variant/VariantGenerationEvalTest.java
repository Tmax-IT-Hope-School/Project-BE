package com.ithopeschool.projectbe.domain.variant;

import com.ithopeschool.projectbe.domain.variant.generate.SourceClassifier;
import com.ithopeschool.projectbe.domain.variant.generate.VariantPipeline;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// 변형 생성 품질 평가 — **실제 AI(OpenAI)를 부릅니다. 돈이 듭니다.** (VAR-10, docs/VARIANT-EVAL.md)
//
// 평소 ./gradlew build 에서는 건너뜁니다. 사람이 키를 넣고 직접 돌립니다.
//   (Git Bash) LLM_LIVE_TESTS=1 OPENAI_API_KEY=키 OPENAI_MODEL=모델 \
//              ./gradlew test --tests '*VariantGenerationEvalTest'
// 고를 수 있는 값 (환경변수)
//   EVAL_LIMIT    시드 몇 문항까지 (기본 30)
//   EVAL_LEVELS   레벨들 (기본 -1,0,1,2)
//   EVAL_N        한 원본 · 레벨마다 몇 번 (기본 3)
//   EVAL_G7       true 면 독립 풀이 검사(G7)를 켜고 돌림 (켠 경우 · 끈 경우를 비교할 때)
// 결과: build/variant-eval/eval_날짜_시각.csv 와 _summary.md
// ⚠️ 키를 파일 · 대화 · PR 에 붙여 넣지 않습니다. 환경변수로만 넣습니다.
@EnabledIfEnvironmentVariable(named = "LLM_LIVE_TESTS", matches = "1")
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:variant-eval;MODE=PostgreSQL",
      "app.ai.openai-api-key=${OPENAI_API_KEY:}",
      "app.ai.model=${OPENAI_MODEL:}",
      "app.variant.enable-independent-solve-check=${EVAL_G7:false}"
    })
class VariantGenerationEvalTest {

  // 평가용 학생 번호 (실제 학생과 겹치지 않게 큰 수)
  private static final Long EVAL_USER_ID = 990_001L;

  @Autowired private SourceProblemService sourceProblemService;
  @Autowired private SourceProblemRepository sourceProblemRepository;
  @Autowired private VariantRepository variantRepository;
  @Autowired private VariantPipeline pipeline;
  @Autowired private SourceClassifier classifier;

  @Test
  @DisplayName("실제 AI 로 시드 × 레벨 × N 을 만들어 CSV 와 요약을 쓴다")
  void evaluate() throws Exception {
    int limit = intEnv("EVAL_LIMIT", 30);
    int n = intEnv("EVAL_N", 3);
    String levelsText = System.getenv().getOrDefault("EVAL_LEVELS", "-1,0,1,2");
    List<Integer> levels =
        Arrays.stream(levelsText.split(",")).map(String::strip).map(Integer::valueOf).toList();

    VariantEvalRunner.Report report =
        new VariantEvalRunner(
                sourceProblemService,
                sourceProblemRepository,
                variantRepository,
                pipeline,
                classifier)
            .run(EVAL_USER_ID, limit, levels, n, true, Path.of("build", "variant-eval"));

    System.out.println(report.summaryText());
    System.out.println("CSV: " + report.csv().toAbsolutePath());
  }

  private static int intEnv(String name, int fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : Integer.parseInt(value.strip());
  }
}
