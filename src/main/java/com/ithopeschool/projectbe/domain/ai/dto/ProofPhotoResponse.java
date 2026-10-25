package com.ithopeschool.projectbe.domain.ai.dto;

// "사진으로 증빙 적기" 의 결과입니다. 아무것도 저장하지 않습니다. (사진도 저장하지 않음, Project-FE#670)
//   { "showHelp": false, "summary": "수학 문제집 42~45쪽 풀고 틀린 3문제 표시함" }
// - summary 가 null 이면 만들지 못한 것입니다. (AI 꺼짐 · 실패 · 공부 흔적이 안 보이는 사진) 에러가 아니라 200 입니다.
//   FE 는 "직접 적어 주세요" 로 안내합니다.
// - showHelp 가 true 면 카드 제목이나 사진 속 글에서 위기 표현이 감지된 것입니다. summary 는 null 이고,
//   FE 는 도움 연결 안내를 먼저 보여 줍니다.
public record ProofPhotoResponse(boolean showHelp, String summary) {

  // AI 가 쓴 한 줄 (만들지 못했으면 null)
  public static ProofPhotoResponse of(String summary) {
    return new ProofPhotoResponse(false, summary);
  }

  // 만들지 못했을 때 (AI 꺼짐 · 실패 · 공부 흔적이 안 보이는 사진)
  public static ProofPhotoResponse notMade() {
    return new ProofPhotoResponse(false, null);
  }

  // 위기 표현이 감지됐을 때의 응답입니다.
  public static ProofPhotoResponse help() {
    return new ProofPhotoResponse(true, null);
  }
}
