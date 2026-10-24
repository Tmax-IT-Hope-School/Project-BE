package com.ithopeschool.projectbe.domain.variant.dto;

// 원본 문항 등록 · 분류 지정 결과
//   id           : 원본 번호 (글자)
//   supportStatus: supported · pending_classification · needs_review · unsupported_figure
//   reviewItemId : 학습 항목을 만들었으면 그 번호, 아니면 null
public record SourceProblemResponse(
    String id, String supportStatus, String unitCode, String typeCode, String reviewItemId) {}
