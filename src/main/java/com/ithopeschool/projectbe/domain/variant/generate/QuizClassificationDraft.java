package com.ithopeschool.projectbe.domain.variant.generate;

// 확인 문제 분류의 AI 답 하나 (검사 전). (quiz_classify_v1.md)
//   kind           : MATH (수학 분류체계) · STANDARD (성취기준) · NONE (어디에도 안 맞음)
//   confidence     : 0 ~ 1
//   figureRequired : 그림 · 그래프 · 표가 있어야 풀 수 있다고 봤는지
public record QuizClassificationDraft(
    String kind,
    String unitCode,
    String typeCode,
    String standardCode,
    Double confidence,
    Boolean figureRequired,
    String reason) {}
