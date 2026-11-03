package com.ithopeschool.projectbe.domain.variant.dto;

// 확인 문제 결과를 받은 결과 (POST /api/v1/variant/quiz-results)
//   registered : 원본으로 저장한 문제 수
//   skipped    : 저장하지 않은 문제 수 (이유별)
public record VariantQuizSourceResponse(int registered, Skipped skipped) {

  //   answerMismatch : AI 가 다시 풀어 본 답이 확인 문제의 정답과 달라서 (확인 문제 정답이 틀렸을 수 있음)
  //   guessedCorrect : 찍은 것 같은 결과에서 맞힌 문제라서
  //   unsupported    : 변형 문제가 다루지 않는 범위이거나, AI 를 쓸 수 없어 확인하지 못해서
  public record Skipped(int answerMismatch, int guessedCorrect, int unsupported) {}
}
