package com.ithopeschool.projectbe.domain.variant;

import java.util.Locale;

// 학습 항목이 왜 생겼는지 (docs/VARIANT-SPEC.md 3장)
public enum ReviewItemOrigin {
  // 원본을 틀려서 생김
  DAILY_WRONG,
  // 원본은 맞혔지만 주간 (A) 심화를 틀려서 생김 (L1 부터 시작)
  WEEKLY_CORRECT_FAIL;

  // JSON 으로 주고받을 때는 소문자로 씁니다.
  public String toJson() {
    return name().toLowerCase(Locale.ROOT);
  }
}
