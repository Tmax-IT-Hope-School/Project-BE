package com.ithopeschool.projectbe.domain.variant.dto;

import java.time.LocalDate;

// 오답노트 현황 한 줄 (GET /api/v1/variant/review-items)
//   status        : active · graduated · mastered
//   origin        : daily_wrong (원본 오답) · weekly_correct_fail (주간 (A) 오답)
//   nextDueDate   : 다음에 낼 날 (active 일 때만)
public record ReviewItemResponse(
    String id,
    String sourceProblemId,
    String problemText,
    String unitCode,
    String unitName,
    String typeCode,
    String typeName,
    String status,
    String origin,
    int currentLevel,
    boolean needsAttention,
    LocalDate nextDueDate) {}
