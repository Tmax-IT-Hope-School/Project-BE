package com.ithopeschool.projectbe.domain.weeklypick.dto;

import com.ithopeschool.projectbe.domain.weeklypick.WeeklyPick;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// "그 주에 해 볼 것 저장" 요청의 모양입니다. 어느 주인지(weekStart)는 주소에 있습니다.
//   { "title": "폰을 다른 방에 두기", "hint": "손이 안 닿으면 덜 멈춰요", "skip": false, "decidedAt": 1792800000000 }
// - title · hint: 꼭 보냅니다. 앞뒤 공백을 지운 뒤 60자까지입니다. (넘어가기여도 보기 글자를 그대로 보냅니다)
// - skip: "이번 주는 넘어가기" 를 골랐으면 true. 꼭 보냅니다.
// - decidedAt: (선택) 고른 시각, 밀리초 숫자 (docs/API.md 2-2). 안 보내면 서버가 저장한 시각을 씁니다.
//   이 기기에 먼저 저장해 둔 것을 나중에 올릴 때, 처음 고른 시각을 그대로 남기려고 받습니다.
public record WeeklyPickSaveRequest(
    @NotBlank(message = "해 볼 것을 적어 주세요")
        @Size(max = WeeklyPick.MAX_TEXT_LENGTH, message = "해 볼 것은 60자까지 적을 수 있어요")
        String title,
    @NotBlank(message = "한 줄 설명을 적어 주세요")
        @Size(max = WeeklyPick.MAX_TEXT_LENGTH, message = "한 줄 설명은 60자까지 적을 수 있어요")
        String hint,
    @NotNull(message = "넘어가기인지(skip) 보내 주세요") Boolean skip,
    @Positive(message = "고른 시각(decidedAt)이 올바르지 않아요") Long decidedAt) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다. 그래서 위의 검사는 공백을 지운 값으로 돌아갑니다.
  public WeeklyPickSaveRequest {
    title = title == null ? null : title.trim();
    hint = hint == null ? null : hint.trim();
  }
}
