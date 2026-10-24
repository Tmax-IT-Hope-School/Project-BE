package com.ithopeschool.projectbe.domain.variant.dto;

// 채점 결과. 답을 낸 뒤에만 정답 · 해설을 보여 줍니다.
//   correctAnswer             : mc5 는 정답 선지 번호 "0" ~ "4", short 는 정답 정수 글자
//   correctAnswerLatex        : 정답의 화면용 LaTeX
//   chosenDistractorRationale : 고른 오답 선지가 어떤 실수인지 (맞았거나 short 면 null)
//   reviewItem                : 이 답으로 바뀐 학습 항목 (주간 (A) 를 맞혀 항목이 없으면 null)
public record AttemptResponse(
    boolean isCorrect,
    String correctAnswer,
    String correctAnswerLatex,
    String solution,
    String chosenDistractorRationale,
    ReviewItemSummary reviewItem) {

  //   status       : active · graduated · mastered
  //   currentLevel : -1 · 0 · 1
  public record ReviewItemSummary(String id, String status, int currentLevel) {}
}
