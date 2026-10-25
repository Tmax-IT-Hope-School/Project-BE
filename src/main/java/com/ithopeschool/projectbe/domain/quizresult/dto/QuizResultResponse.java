package com.ithopeschool.projectbe.domain.quizresult.dto;

import com.ithopeschool.projectbe.domain.quizresult.QuizResult;
import java.time.LocalDate;
import java.util.List;

// 저장한 퀴즈 결과 하나를 FE 에 알려 주는 모양입니다.
//   { "id": 1, "subjectId": 3, "unit": "함수", "quizDate": "2026-10-24",
//     "correctCount": 8, "totalCount": 10, "questionSeconds": [45, 30, …] }
// - 누구의 결과인지(userId)는 담지 않습니다.
public record QuizResultResponse(
    Long id,
    Long subjectId,
    String unit,
    LocalDate quizDate,
    int correctCount,
    int totalCount,
    List<Integer> questionSeconds) {

  public static QuizResultResponse from(QuizResult result) {
    return new QuizResultResponse(
        result.getId(),
        result.getSubjectId(),
        result.getUnit(),
        result.getQuizDate(),
        result.getCorrectCount(),
        result.getTotalCount(),
        result.questionSecondsList());
  }
}
