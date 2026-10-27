package com.ithopeschool.projectbe.domain.variant;

// 단원 · 유형 코드를 누가 정했는지
public enum ClassificationSource {
  // 문항을 보낸 쪽(앞단계 · 사람)이 정함
  UPSTREAM,
  // 우리 분류기(AI)가 정함
  LLM
}
