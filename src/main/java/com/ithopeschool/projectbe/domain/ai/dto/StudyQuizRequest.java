package com.ithopeschool.projectbe.domain.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

// "확인 문제" 요청의 모양입니다. (2026-10-18)
//   { "topic": "미적분 3단원 수열의 극한", "checklist": ["교과서 52~60쪽 읽기"],
//     "material": "수열의 극한: n 이 한없이 커질 때 …", "image": "data:image/jpeg;base64,/9j/4AAQ...",
//     "count": 3, "choiceCount": 5, "shortCount": 1 }
// - topic    : 필수. 무엇을 공부했는지 한 줄 (100자). 보통 카드 제목입니다.
// - checklist: 선택. 그 카드의 체크리스트 (5개 · 각 50자)
// - material : 선택. 학생이 붙여 넣은 메모 · 교과서 글 (2,000자). 있으면 그 안에서만 문제를 냅니다.
// - image    : 선택. 교과서 · 필기 · 문제집 사진 한 장을 data URL 로 (jpeg · png · webp, 4MB).
//              "사진으로 카드 만들기"(CardPhotoRequest)와 같은 규칙이고, 모양 · 크기 검사는 StudyQuizService 가 합니다.
// - count    : 선택. 문제 수 1 ~ 5 (안 보내면 3)
// - choiceCount: 선택. 객관식 보기 수 4 또는 5 (안 보내면 4). 지금 FE 는 수능처럼 5 를 보냅니다. (2026-10-31)
//                예전 FE 는 이 칸을 몰라서 4지선다를 그대로 받습니다.
// - shortCount : 선택. 그중 주관식(단답형: 낱말 · 숫자 하나로 답이 정해지는 문제) 수 0 ~ 5 (안 보내면 0).
//                문제 수보다 크면 문제 수로 줄입니다. 몇 개를 넣을지는 FE 가 정합니다. (2026-10-31)
public record StudyQuizRequest(
    @NotBlank(message = "무엇을 공부했는지 보내 주세요") @Size(max = 100, message = "주제는 100자 이하로 보내 주세요")
        String topic,
    @Size(max = 5, message = "체크리스트는 5개까지 보낼 수 있어요")
        List<
                @NotBlank(message = "빈 체크리스트 항목이 있어요")
                @Size(max = 50, message = "체크리스트 항목은 50자 이하로 보내 주세요") String>
            checklist,
    @Size(max = 2000, message = "자료 글은 2000자 이하로 보내 주세요") String material,
    @Size(max = CardPhotoRequest.MAX_IMAGE_CHARS, message = "사진이 너무 커요. 4MB 이하로 보내 주세요")
        String image,
    @Min(value = 1, message = "문제 수는 1 ~ 5 로 보내 주세요")
        @Max(value = 5, message = "문제 수는 1 ~ 5 로 보내 주세요")
        Integer count,
    @Min(value = 4, message = "보기 수는 4 또는 5 로 보내 주세요")
        @Max(value = 5, message = "보기 수는 4 또는 5 로 보내 주세요")
        Integer choiceCount,
    @Min(value = 0, message = "주관식 수는 0 ~ 5 로 보내 주세요")
        @Max(value = 5, message = "주관식 수는 0 ~ 5 로 보내 주세요")
        Integer shortCount) {

  // 문제 수를 안 보냈을 때
  public static final int DEFAULT_COUNT = 3;
  // 보기 수를 안 보냈을 때 (예전 FE 와 같은 4지선다)
  public static final int DEFAULT_CHOICE_COUNT = 4;

  // 보기 수 · 주관식 수를 모르는 예전 모양 (예전 FE · 테스트)
  public StudyQuizRequest(
      String topic, List<String> checklist, String material, String image, Integer count) {
    this(topic, checklist, material, image, count, null, null);
  }

  // 값이 들어오는 순간에 앞뒤 공백을 지우고, 빈 칸은 없는 것(null · 빈 목록)으로 봅니다.
  public StudyQuizRequest {
    topic = topic == null ? null : topic.trim();
    checklist = checklist == null ? List.of() : checklist.stream().map(String::trim).toList();
    material = material == null || material.isBlank() ? null : material.trim();
    image = image == null || image.isBlank() ? null : image;
    count = count == null ? DEFAULT_COUNT : count;
    choiceCount = choiceCount == null ? DEFAULT_CHOICE_COUNT : choiceCount;
    shortCount = shortCount == null ? 0 : Math.min(shortCount, count);
  }
}
