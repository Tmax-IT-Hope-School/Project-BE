package com.ithopeschool.projectbe.domain.variant.dto;

import com.ithopeschool.projectbe.global.json.WholeNumberDeserializer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

// 확인 문제 결과 (POST /api/v1/variant/quiz-results, docs/VARIANT-SPEC.md 5-1)
// 학생이 공부 카드를 끝내고 푼 확인 문제(4지선다) 한 묶음입니다. 문제마다 변형 문제의 원본이 됩니다.
//
//   topic     : 무엇에 대한 문제였는지 (카드 제목 등, 100자까지)
//   solvedAt  : 다 푼 순간 (밀리초 숫자, docs/API.md 2-2)
//   guessed   : FE 가 "찍은 것 같다" 고 본 결과인지. true 면 맞힌 문제는 원본으로 쓰지 않습니다
//   questions : 문제들 (1 ~ 5개, 확인 문제 최대 개수와 같음)
public record VariantQuizSourceRequest(
    @NotBlank(message = "주제(topic)를 보내 주세요") @Size(max = 100, message = "주제는 100자까지예요")
        String topic,
    @NotNull(message = "푼 시각(solvedAt)을 보내 주세요") Long solvedAt,
    @NotNull(message = "찍은 것 같은지(guessed)를 보내 주세요") Boolean guessed,
    @NotNull(message = "문제(questions)를 보내 주세요")
        @Size(min = 1, max = 5, message = "문제는 1 ~ 5개를 보내 주세요")
        List<@NotNull(message = "빈 문제가 있어요") @Valid Question> questions) {

  //   question    : 문제 글 (2000자까지)
  //   passage     : 문제가 기대는 지문 (없으면 null, 2000자까지)
  //   choices     : 보기 4개 (하나에 200자까지)
  //   answerIndex : 확인 문제의 정답 번호 (0 ~ 3)
  //   chosenIndex : 학생이 고른 번호 (0 ~ 3)
  public record Question(
      @NotBlank(message = "문제 글을 보내 주세요") @Size(max = 2000, message = "문제 글은 2000자까지예요")
          String question,
      @Size(max = 2000, message = "지문은 2000자까지예요") String passage,
      @NotNull(message = "보기(choices)를 보내 주세요") @Size(min = 4, max = 4, message = "보기는 4개를 보내 주세요")
          List<
                  @NotBlank(message = "빈 보기가 있어요") @Size(max = 200, message = "보기는 하나에 200자까지예요")
                  String>
              choices,
      @JsonDeserialize(using = WholeNumberDeserializer.class)
          @NotNull(message = "정답 번호(answerIndex)를 보내 주세요")
          @Min(value = 0, message = "정답 번호는 0 ~ 3 이에요")
          @Max(value = 3, message = "정답 번호는 0 ~ 3 이에요")
          Integer answerIndex,
      @JsonDeserialize(using = WholeNumberDeserializer.class)
          @NotNull(message = "고른 번호(chosenIndex)를 보내 주세요")
          @Min(value = 0, message = "고른 번호는 0 ~ 3 이에요")
          @Max(value = 3, message = "고른 번호는 0 ~ 3 이에요")
          Integer chosenIndex) {}
}
