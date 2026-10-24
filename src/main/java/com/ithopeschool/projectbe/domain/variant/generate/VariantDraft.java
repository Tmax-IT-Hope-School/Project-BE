package com.ithopeschool.projectbe.domain.variant.generate;

import java.util.List;

// AI 가 만든 변형 문항 하나 (검사 전). AI 가 돌려주는 JSON 모양 그대로입니다. (docs/VARIANT-SPEC.md 6-1)
// 칸이 비어 있거나 모양이 틀릴 수 있어서, 쓰기 전에 VariantPipeline 이 G1 ~ G7 로 검사합니다.
public record VariantDraft(
    String problemText,
    String format,
    List<String> choices,
    Integer answerIndex,
    Integer shortAnswer,
    List<String> distractorRationales,
    String solution,
    String verificationCode,
    String levelJustification) {}
