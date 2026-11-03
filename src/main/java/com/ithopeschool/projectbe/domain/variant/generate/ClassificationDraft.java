package com.ithopeschool.projectbe.domain.variant.generate;

// AI 분류기의 답 하나 (검사 전). (classify_v2.md)
//   confidence     : 0 ~ 1
//   figureRequired : 그림 · 그래프가 있어야 풀 수 있다고 봤는지
public record ClassificationDraft(
    String unitCode, String typeCode, Double confidence, Boolean figureRequired, String reason) {}
