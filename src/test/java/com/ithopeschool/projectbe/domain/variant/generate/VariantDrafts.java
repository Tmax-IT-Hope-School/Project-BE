package com.ithopeschool.projectbe.domain.variant.generate;

import java.util.List;

// 테스트에서 쓰는 AI 답(초안) 견본입니다. 모두 실제 샌드박스 검사를 통과하는 진짜 문제입니다.
public final class VariantDrafts {

  private VariantDrafts() {}

  // 단답형: 두 근의 합이 7, 차가 3일 때 두 근의 곱 → 10
  public static VariantDraft shortRootProduct(String text) {
    return new VariantDraft(
        text,
        "SHORT",
        null,
        null,
        10,
        null,
        "두 근을 $a$, $b$라 하면 $a+b=7$, $a-b=3$이므로 $a=5$, $b=2$이고 곱은 $10$이다.",
        "s = Solve({a + b == 7, a - b == 3}, {a, b}); k = (a*b) /. First(s); k",
        "수치만 바꾼 L0");
  }

  public static VariantDraft shortRootProduct() {
    return shortRootProduct("이차방정식의 두 근의 합이 $7$, 차가 $3$일 때 두 근의 곱을 구하시오.");
  }

  // 단답형인데 AI 가 말한 정답(12)이 계산값(10)과 다름 → G5 에서 떨어짐
  public static VariantDraft shortWrongAnswer() {
    VariantDraft good = shortRootProduct("두 근의 합이 $7$, 차가 $3$인 이차방정식에서 두 근의 곱은?");
    return new VariantDraft(
        good.problemText(),
        "SHORT",
        null,
        null,
        12,
        null,
        good.solution(),
        good.verificationCode(),
        good.levelJustification());
  }

  // 5지선다: x^2 - 4x + 3 < 0 의 해. 정답 선지는 0번 "1 < x < 3"
  public static VariantDraft mc5Inequality() {
    return new VariantDraft(
        "이차부등식 $x^2-4x+3<0$의 해는?",
        "MC5",
        List.of("1 < x < 3", "x < 1 || x > 3", "1 <= x <= 3", "-3 < x < -1", "x < 3"),
        0,
        null,
        List.of("", "부등호 방향을 반대로 읽음", "등호를 포함함", "근의 부호를 바꿈", "큰 근만 봄"),
        "$(x-1)(x-3)<0$이므로 $1<x<3$이다.",
        "r = x /. Solve(x^2 - 4*x + 3 == 0, x); Min(r) < x < Max(r)",
        "수치만 바꾼 L0");
  }

  // 단답형 L-1 비계: [1단계] · [2단계] 표지가 있는 문제
  public static VariantDraft scaffolded() {
    return new VariantDraft(
        "[1단계] 두 근의 합이 $4$이면 $a+b=4$이다. [2단계] 두 근의 차가 $2$일 때 두 근의 곱을 구하시오.",
        "SHORT",
        null,
        null,
        3,
        null,
        "$a=3$, $b=1$이므로 곱은 $3$이다.",
        "s = Solve({a + b == 4, a - b == 2}, {a, b}); (a*b) /. First(s)",
        "단계를 나눈 L-1");
  }
}
