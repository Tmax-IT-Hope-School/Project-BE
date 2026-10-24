package com.ithopeschool.projectbe.domain.variant.generate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 프롬프트 파일 읽기 · 채우기 테스트입니다.
class PromptTemplateTest {

  private static final String TEXT =
      """
      <!-- 설명은 AI 에게 보내지 않습니다 {{not_a_value}} -->
      === system ===
      규칙: {x, k} 와 \\frac{1}{2} 는 그대로
      === user ===
      문제: {{problem}}
      """;

  @Test
  @DisplayName("칸별로 나누고, {{이름}} 만 채우며 넣은 값 안의 { } 는 다시 바꾸지 않는다")
  void rendersSections() {
    PromptTemplate template = PromptTemplate.parse("test_v1", TEXT);

    assertThat(template.render("system", Map.of())).isEqualTo("규칙: {x, k} 와 \\frac{1}{2} 는 그대로");
    assertThat(template.render("user", Map.of("problem", "{{problem}} $\\frac{1}{2}$")))
        .isEqualTo("문제: {{problem}} $\\frac{1}{2}$");
    assertThat(template.version()).isEqualTo("test_v1");
  }

  @Test
  @DisplayName("값이 빠지거나 없는 칸을 찾으면 에러다 (빈 칸이 AI 에게 가지 않게)")
  void missingValuesFail() {
    PromptTemplate template = PromptTemplate.parse("test_v1", TEXT);

    assertThatThrownBy(() -> template.render("user", Map.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("problem");
    assertThatThrownBy(() -> template.render("nothing", Map.of()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("실제 프롬프트 v2 세 개를 읽고, 변형 프롬프트에 원본 · 레벨 규칙 · 실패 사유가 들어간다")
  void realPrompts() {
    VariantPrompts prompts = new VariantPrompts();

    VariantPrompts.Prompt prompt =
        prompts.variant(
            new VariantPrompts.VariantRequest(
                "CM1-U05",
                "이차방정식",
                "CM1-U05-T03",
                "근과 계수의 관계",
                List.of("두 근의 합을 b/a로 계산한다"),
                "이차방정식 $x^2-5x+k=0$의 두 근의 곱이 6일 때 $k$",
                List.of(),
                "6",
                -1,
                List.of("이전 변형 문제"),
                "G5 계산값이 달라요"));

    // 채우지 않은 자리 표시({{이름}})가 남으면 안 됩니다. (행렬 예시 {{1, 2}, …} 는 자리 표시가 아님)
    String unfilled = "\\{\\{[a-z_]+}}";
    assertThat(prompt.instructions()).contains("Symja").doesNotContainPattern(unfilled);
    assertThat(prompt.input())
        .contains("CM1-U05-T03 근과 계수의 관계")
        .contains("L-1 비계")
        .contains("[1단계]")
        .contains("1) 이전 변형 문제")
        .contains("G5 계산값이 달라요")
        .contains("- 두 근의 합을 b/a로 계산한다")
        .doesNotContainPattern(unfilled);

    VariantPrompts.Prompt classify = prompts.classify("목록", "문제", null, null);
    assertThat(classify.instructions()).contains("목록");
    assertThat(classify.input()).contains("(없음)");

    VariantPrompts.Prompt solve = prompts.solveCheck("MC5", "문제", List.of("\\frac{1}{2}", "2"));
    assertThat(solve.input()).contains("1) \\frac{1}{2}").contains("[형식] MC5");
  }
}
