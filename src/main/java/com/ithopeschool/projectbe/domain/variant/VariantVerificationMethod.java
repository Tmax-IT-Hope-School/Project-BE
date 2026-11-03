package com.ithopeschool.projectbe.domain.variant;

// 변형의 정답을 무엇으로 확인했는지 (docs/VARIANT-SPEC.md 4-1 · 6-4)
//   SYMJA    : 수학. AI 가 쓴 검증 코드를 샌드박스에서 계산해서 정답과 같은지 봤습니다 (G4 · G5)
//   AI_SOLVE : 수학이 아닌 과목. 계산할 식이 없어서, 정답을 숨긴 채 AI 가 따로 풀어 같은지 봤습니다 (G7)
// ⚠️ AI_SOLVE 는 계산만큼 확실하지 않습니다(두 AI 가 같이 틀릴 수 있음). 품질 평가에서 따로 잽니다.
public enum VariantVerificationMethod {
  SYMJA,
  AI_SOLVE
}
