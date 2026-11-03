package com.ithopeschool.projectbe.domain.variant.generate;

import java.util.List;

// AI 가 만든 수학이 아닌 과목의 변형 문항 하나 (검사 전). (docs/VARIANT-SPEC.md 2-1 · 6-4)
// 수학의 VariantDraft 와 달리 형식은 MC5 하나뿐이고, 선지는 수식이 아니라 글이며, 검증 코드가 없습니다.
// 쓰기 전에 VariantPipeline 이 G1 · G2 · G6 · G7 로 검사합니다.
public record TextVariantDraft(
    String problemText,
    List<String> choices,
    Integer answerIndex,
    List<String> distractorRationales,
    String solution,
    String levelJustification) {}
