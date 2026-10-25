package com.ithopeschool.projectbe.domain.variant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 사람이 원본의 단원 · 유형을 정해 주는 요청 (PATCH /api/v1/variant/source-problems/{id}/classification)
// 분류를 기다리거나(pending_classification) 확인이 필요한(needs_review) 원본에만 씁니다.
public record ClassificationUpdateRequest(
    @NotBlank(message = "단원 코드를 보내 주세요") @Size(max = 20, message = "단원 코드를 다시 확인해 주세요")
        String unitCode,
    @NotBlank(message = "유형 코드를 보내 주세요") @Size(max = 20, message = "유형 코드를 다시 확인해 주세요")
        String typeCode) {}
