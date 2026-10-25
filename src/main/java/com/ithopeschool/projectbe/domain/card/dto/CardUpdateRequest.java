package com.ithopeschool.projectbe.domain.card.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

// "카드 바꾸기" 요청의 모양입니다. 바꿀 것만 보냅니다. (PATCH)
//   { "title": "수학 문제집 2권" }      { "repeatDays": [] }
//   { "checklist": [ { "id": "i1", "text": "42쪽" } ] }
// - 보내지 않은(null) 값은 그대로 둡니다. 아무것도 보내지 않은 요청은 Service 에서 400 으로 돌려보냅니다.
// - 목록(repeatDays · checklist · expectedFailCauses)은 항목 하나만 고치지 않고 **보낸 목록으로 통째로 바꿉니다.**
// - 지우는 방법 (계획 바꾸기와 같은 약속: 빈 값을 보냄)
//     repeatDays: []          → 1회성 카드          startTime: ""          → 시작 시각 없음
//     goalId: ""              → 목표 없음           expectedDoneReason: "" → 이유 지우기
//     subjectId: ""           → 과목 없음
//     expectedFailCauses: []  → 예상 실패 원인 지우기
// - expectedDone · alert · planningMs 는 지우는 방법이 없습니다. 기본으로 되돌리려면 기본값을 보냅니다.
//   (예상 달성 개수 = 체크리스트 개수, 알림 = { "method": "message", "minutesBefore": 0 })
// - 각 칸의 규칙은 카드 만들기(CardCreateRequest)와 같습니다.
public record CardUpdateRequest(
    @Size(min = 1, max = 100, message = "카드 제목은 1자 이상 100자 이하로 적어 주세요") String title,
    LocalDate date,
    @Size(max = 7, message = "반복 요일은 7개까지 보낼 수 있어요")
        List<
                @NotNull(message = "반복 요일을 0 ~ 6 숫자로 보내 주세요")
                @Min(value = 0, message = "반복 요일은 0(일) ~ 6(토) 으로 보내 주세요")
                @Max(value = 6, message = "반복 요일은 0(일) ~ 6(토) 으로 보내 주세요") Integer>
            repeatDays,
    // 빈 글자("")도 받습니다. (시작 시각 지우기)
    @Pattern(regexp = "|([01]\\d|2[0-3]):[0-5]\\d", message = "시작 시각은 19:00 처럼 보내 주세요")
        String startTime,
    @Min(value = 1, message = "카드 길이는 1분 이상이어야 해요")
        @Max(value = 600, message = "카드 길이는 600분(10시간) 이하여야 해요")
        Integer durationMinutes,
    @Size(min = 1, max = 20, message = "체크리스트는 1개 이상 20개까지 적을 수 있어요")
        List<@NotNull(message = "체크리스트 항목을 입력해 주세요") @Valid ChecklistItemRequest> checklist,
    @Pattern(regexp = "\\d{0,18}", message = "목표 번호가 올바르지 않아요") String goalId,
    @Pattern(regexp = "\\d{0,18}", message = "과목 번호가 올바르지 않아요") String subjectId,
    String priority,
    String type,
    @Min(value = 0, message = "예상 달성 개수는 0 이상이어야 해요") Integer expectedDone,
    @Size(max = 30, message = "예상 달성 개수를 낮춘 이유는 30자 이하로 보내 주세요") String expectedDoneReason,
    @Size(max = 5, message = "예상 실패 원인은 5개까지 적을 수 있어요")
        List<
                @NotBlank(message = "예상 실패 원인을 입력해 주세요")
                @Size(max = 100, message = "예상 실패 원인은 100자 이하로 적어 주세요") String>
            expectedFailCauses,
    @Valid CardAlertRequest alert,
    @Min(value = 0, message = "계획에 쓴 시간은 0 이상이어야 해요") Long planningMs) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다. "   " 처럼 공백뿐인 제목은 빈 글자("")가 되어 위의 검사에 걸립니다.
  public CardUpdateRequest {
    title = title == null ? null : title.trim();
    expectedDoneReason = expectedDoneReason == null ? null : expectedDoneReason.trim();
    expectedFailCauses = CardCreateRequest.trimAll(expectedFailCauses);
  }

  // 바꿀 값을 하나도 보내지 않았는지 알려 줍니다.
  public boolean isEmpty() {
    return title == null
        && date == null
        && repeatDays == null
        && startTime == null
        && durationMinutes == null
        && checklist == null
        && goalId == null
        && subjectId == null
        && priority == null
        && type == null
        && expectedDone == null
        && expectedDoneReason == null
        && expectedFailCauses == null
        && alert == null
        && planningMs == null;
  }
}
