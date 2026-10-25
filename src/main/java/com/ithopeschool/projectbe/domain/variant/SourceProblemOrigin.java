package com.ithopeschool.projectbe.domain.variant;

import java.util.Locale;

// 원본 문항이 어디서 들어왔는지 (docs/VARIANT-SPEC.md 4-1)
public enum SourceProblemOrigin {
  // 원본 등록 API 로 직접 (POST /api/v1/variant/source-problems)
  MANUAL,
  // 내 컴퓨터의 시드 문항 (VariantSeedInitializer)
  SEED,
  // 확인 문제 결과 (POST /api/v1/variant/quiz-results)
  QUIZ;

  // JSON 으로 주고받을 때는 소문자로 씁니다.
  public String toJson() {
    return name().toLowerCase(Locale.ROOT);
  }
}
