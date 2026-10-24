package com.ithopeschool.projectbe.domain.variant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

// 원본 문항 등록 요청 (POST /api/v1/variant/source-problems, docs/VARIANT-SPEC.md 5장)
//
//   problemText   : 문제 글. 수식은 $...$ (2000자까지)
//   choices       : 원본이 객관식이면 선지 글자들 (10개 · 하나에 200자까지). 단답형이면 null
//   correctAnswer : 원본 정답 (모르면 null)
//   studentAnswer : 학생이 쓴 답 (모르면 null)
//   isCorrect     : 학생이 맞혔는지
//   hasFigure     : 그림 · 그래프가 있어야 풀 수 있는지 (안 보내면 false)
//   unitCode      : 단원 코드 (taxonomy.json). typeCode 와 둘 다 보내거나 둘 다 안 보냅니다
//   typeCode      : 유형 코드
//   solvedAt      : 푼 순간 (밀리초 숫자, docs/API.md 2-2)
public record SourceProblemCreateRequest(
    @NotBlank(message = "문제 글을 보내 주세요") @Size(max = 2000, message = "문제 글은 2000자까지예요")
        String problemText,
    @Size(max = 10, message = "선지는 10개까지예요")
        List<@NotBlank(message = "빈 선지가 있어요") @Size(max = 200, message = "선지는 하나에 200자까지예요") String>
            choices,
    @Size(max = 200, message = "정답은 200자까지예요") String correctAnswer,
    @Size(max = 200, message = "학생 답은 200자까지예요") String studentAnswer,
    @NotNull(message = "맞혔는지(isCorrect)를 보내 주세요") Boolean isCorrect,
    Boolean hasFigure,
    @Size(max = 20, message = "단원 코드를 다시 확인해 주세요") String unitCode,
    @Size(max = 20, message = "유형 코드를 다시 확인해 주세요") String typeCode,
    @NotNull(message = "푼 시각(solvedAt)을 보내 주세요") Long solvedAt) {}
