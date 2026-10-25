package com.ithopeschool.projectbe.domain.variant.dto;

import java.time.LocalDate;
import java.util.List;

// 주간 심화 세트 (GET /api/v1/variant/weekly)
//   weekStart : 돌아보는 주의 월요일 ("2026-10-19"). 세트는 그다음 월요일에 만들어집니다
//   dueDate   : 마감 날 (새벽 4시 기준, 이날까지)
public record WeeklyResponse(LocalDate weekStart, LocalDate dueDate, List<Item> items) {

  //   number    : 1 부터의 문항 번호
  //   source    : a (맞은 문제의 대표 유형 심화) · b (극복한 오답의 심화)
  //   answerKey : includeAnswers=true 이고, 이미 답했거나 마감된 문항만. 그 밖에는 null
  public record Item(
      int number,
      String assignmentId,
      String source,
      String format,
      String problemText,
      List<String> choicesLatex,
      String status,
      AnswerKey answerKey) {}

  public record AnswerKey(String correctAnswer, String correctAnswerLatex, String solution) {}
}
