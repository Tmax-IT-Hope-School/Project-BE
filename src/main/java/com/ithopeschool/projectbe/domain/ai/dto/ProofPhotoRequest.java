package com.ithopeschool.projectbe.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// "사진으로 증빙 적기" 요청의 모양입니다. (Project-FE#670)
//   { "image": "data:image/jpeg;base64,/9j/4AAQ...", "cardTitle": "수학 문제집" }
// - image: 사진 한 장을 data URL 로 (jpeg · png · webp). FE 가 긴 변 1600px 로 줄여서 보냅니다.
//          글자 수 한도는 "사진으로 카드 만들기" 와 같습니다. 모양 · 크기 검사는 ProofPhotoService 가 합니다.
// - cardTitle: (선택) 방금 끝낸 카드의 제목. 100자까지 (카드 제목과 같음)
public record ProofPhotoRequest(
    @NotBlank(message = "사진을 보내 주세요")
        @Size(max = CardPhotoRequest.MAX_IMAGE_CHARS, message = "사진이 너무 커요. 4MB 이하로 보내 주세요")
        String image,
    @Size(max = 100, message = "카드 제목은 100자 이하로 보내 주세요") String cardTitle) {

  // 값이 들어오는 순간에 카드 제목의 앞뒤 공백을 지우고, 비었으면 없는 것(null)으로 둡니다.
  public ProofPhotoRequest {
    cardTitle = cardTitle == null || cardTitle.isBlank() ? null : cardTitle.trim();
  }
}
