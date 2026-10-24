package com.ithopeschool.projectbe.domain.variant;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.variant.sandbox.SymjaGuard;
import com.ithopeschool.projectbe.domain.variant.sandbox.SymjaValues;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.matheclipse.core.eval.ExprEvaluator;

// 시드 원본 문항(seed-source-problems.json) 검사입니다. (VAR-03)
// 각 문항의 정답을 Symja 로 다시 계산해서(checkCode) 적어 둔 정답(checkValue)과 같은지 봅니다.
// 사람 검토를 대신하지는 않습니다. 손으로 푼 검토는 팀원이 따로 합니다.
class VariantSeedDataTest {

  private final List<VariantSeedData.Problem> problems = VariantSeedData.load();

  @Test
  @DisplayName("30문항: 단원마다 2문항 이상, 오답 12문항 이상, 객관식 · 단답형이 섞여 있고 코드는 분류체계에 있다")
  void shape() {
    VariantTaxonomy taxonomy = new VariantTaxonomy();

    assertThat(problems).hasSize(30);
    Map<String, Integer> perUnit = new HashMap<>();
    for (VariantSeedData.Problem problem : problems) {
      assertThat(taxonomy.isValidPair(problem.unitCode(), problem.typeCode()))
          .as(problem.typeCode())
          .isTrue();
      perUnit.merge(problem.unitCode(), 1, Integer::sum);
    }
    assertThat(perUnit).hasSize(10);
    assertThat(perUnit.values()).allMatch(count -> count >= 2);
    assertThat(problems.stream().filter(p -> !p.isCorrect()).count()).isGreaterThanOrEqualTo(12);
    assertThat(problems.stream().filter(p -> p.choices() != null).count()).isPositive();
    assertThat(problems.stream().filter(p -> p.choices() == null).count()).isPositive();
  }

  @Test
  @DisplayName("모든 문항의 정답을 Symja 로 다시 계산하면 적어 둔 값과 같다")
  void answersAreRecomputed() {
    for (VariantSeedData.Problem problem : problems) {
      // 시드의 검산 코드도 AI 코드와 같은 안전 검사를 통과해야 합니다.
      assertThat(SymjaGuard.checkCode(problem.checkCode()).passed())
          .as(problem.checkCode())
          .isTrue();

      ExprEvaluator evaluator = new ExprEvaluator();
      SymjaValues values = new SymjaValues(evaluator.getEvalEngine());
      boolean same =
          values.same(evaluator.eval(problem.checkCode()), evaluator.eval(problem.checkValue()));
      assertThat(same).as(problem.problemText()).isTrue();
      if (problem.choices() == null) {
        assertThat(problem.checkValue())
            .as(problem.problemText())
            .isEqualTo(problem.correctAnswer());
      }
    }
  }
}
