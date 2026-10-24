package com.ithopeschool.projectbe.domain.variant.dto;

import com.ithopeschool.projectbe.global.json.WholeNumberDeserializer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

// 답 내기 (POST /api/v1/variant/assignments/{id}/attempts)
//   answer       : mc5 는 고른 선지 번호 "0" ~ "4", short 는 정수 글자 (예: "12")
//   timeSpentSec : 푸는 데 걸린 초 (선택, 0 ~ 86400)
public record AttemptRequest(
    @NotBlank(message = "답을 보내 주세요") @Size(max = 20, message = "답이 너무 길어요") String answer,
    @JsonDeserialize(using = WholeNumberDeserializer.class)
        @Min(value = 0, message = "걸린 시간은 0초 이상이에요")
        @Max(value = 86400, message = "걸린 시간은 하루(86400초)까지예요")
        Integer timeSpentSec) {}
