package com.ithopeschool.projectbe.domain.quizresult.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

// 퀴즈 한 번을 다 풀었을 때 FE 가 보내는 모양입니다.
//   { "subjectId": 3, "unit": "함수", "correctCount": 8, "totalCount": 10,
//     "questionSeconds": [45, 30, 62, 40, 51, 38, 70, 29, 44, 60] }
// - 날짜는 받지 않습니다. 서버가 지금 시각으로 정합니다. (하루는 새벽 4시 기준)
// - 맞은 개수 ≤ 전체 개수, 걸린 시간의 개수 = 전체 개수 는 Service 가 확인합니다.
public record QuizResultRequest(
    @NotNull(message = "과목을 골라 주세요") Long subjectId,
    @NotBlank(message = "단원을 적어 주세요") @Size(max = 30, message = "단원은 30자까지 적을 수 있어요") String unit,
    @NotNull(message = "맞은 개수를 적어 주세요") @Min(value = 0, message = "맞은 개수는 0 이상이어야 해요")
        Integer correctCount,
    @NotNull(message = "전체 개수를 적어 주세요")
        @Min(value = 1, message = "전체 개수는 1 이상이어야 해요")
        @Max(value = 20, message = "한 번에 20문제까지 저장할 수 있어요")
        Integer totalCount,
    @NotEmpty(message = "문제마다 걸린 시간을 보내 주세요")
        List<
                @NotNull(message = "걸린 시간이 비어 있어요") @Min(value = 0, message = "걸린 시간은 0초 이상이어야 해요")
                @Max(value = 3600, message = "한 문제에 1시간(3600초)까지 적을 수 있어요") Integer>
            questionSeconds) {}
