package com.ithopeschool.projectbe.domain.weeklypick.dto;

import com.ithopeschool.projectbe.domain.weeklypick.WeeklyPick;
import java.time.LocalDate;

// 그 주에 고른 것 하나를 FE 에 알려 주는 모양입니다. (FE 의 WeeklyPick 타입과 같은 이름)
//   { "id": "5", "weekStart": "2026-10-26", "title": "폰을 다른 방에 두기",
//     "hint": "손이 안 닿으면 덜 멈춰요", "skip": false, "decidedAt": 1792800000000 }
// - id 는 다른 API 와 같이 글자로 보냅니다.
// - decidedAt 은 고른 시각(밀리초 숫자)입니다. (docs/API.md 2-2)
// - 누구의 것인지(userId)는 담지 않습니다.
public record WeeklyPickResponse(
    String id, LocalDate weekStart, String title, String hint, boolean skip, long decidedAt) {

  public static WeeklyPickResponse from(WeeklyPick pick) {
    return new WeeklyPickResponse(
        String.valueOf(pick.getId()),
        pick.getWeekStart(),
        pick.getTitle(),
        pick.getHint(),
        pick.isSkip(),
        pick.getDecidedAt().toEpochMilli());
  }
}
