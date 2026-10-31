package com.ithopeschool.projectbe.domain.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// "내 학년 · 반 저장" 요청의 모양입니다. 둘 다 보내거나, 지우려면 둘 다 null 로 보냅니다.
//   { "grade": 2, "classNo": 1 }   /   { "grade": null, "classNo": null }
// 반 번호의 끝(30)은 학교 시간표 API 와 같습니다. (SchoolTodayService.MAX_CLASS_NO)
public record SchoolClassRequest(
    @Min(value = 1, message = "학년은 1~3, 반은 1~30 이어야 해요")
        @Max(value = 3, message = "학년은 1~3, 반은 1~30 이어야 해요")
        Integer grade,
    @Min(value = 1, message = "학년은 1~3, 반은 1~30 이어야 해요")
        @Max(value = 30, message = "학년은 1~3, 반은 1~30 이어야 해요")
        Integer classNo) {}
