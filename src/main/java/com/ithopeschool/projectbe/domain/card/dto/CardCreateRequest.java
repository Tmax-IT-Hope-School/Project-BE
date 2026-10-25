package com.ithopeschool.projectbe.domain.card.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// "카드 만들기" 요청의 모양입니다. (FE 의 Card 에서 id · createdAt 을 뺀 것)
//   { "title": "수학 문제집", "date": "2026-10-12", "repeatDays": [1, 3], "startTime": "19:00",
//     "durationMinutes": 60, "checklist": [ { "text": "42~45쪽" }, { "text": "오답 정리" } ],
//     "goalId": null, "subjectId": "3", "priority": "flexible", "type": "self", "expectedDone": 2,
//     "expectedDoneReason": null, "expectedFailCauses": ["폰을 볼 것 같다"],
//     "alert": { "method": "message", "minutesBefore": 0 }, "planningMs": 42000 }
// - 꼭 보내야 하는 것: title · date · durationMinutes · checklist
// - 안 보내면: 1회성 카드 · 시작 시각 없음 · 목표 없음 · 과목 없음 · 유연(flexible) · 자습(self) · 알림 기본
// - subjectId 는 내 과목이어야 합니다. (없는 과목 · 남의 과목은 Service 에서 404)
// - startTime 이 없는 카드는 옛 계획에서 옮긴 카드뿐입니다. 새 카드는 FE 가 꼭 보냅니다.
// - 요일이 겹치는지, priority · type · alert.method 가 정해진 값인지, expectedDone 이 체크리스트 개수 이하인지는
//   Service 에서 검사합니다.
public record CardCreateRequest(
    @NotBlank(message = "카드 제목을 입력해 주세요") @Size(max = 100, message = "카드 제목은 100자 이하로 적어 주세요")
        String title,
    @NotNull(message = "날짜를 보내 주세요") LocalDate date,
    // 0 = 일요일 … 6 = 토요일. 비어 있거나([]) 안 보내면 1회성 카드입니다.
    @Size(max = 7, message = "반복 요일은 7개까지 보낼 수 있어요")
        List<
                @NotNull(message = "반복 요일을 0 ~ 6 숫자로 보내 주세요")
                @Min(value = 0, message = "반복 요일은 0(일) ~ 6(토) 으로 보내 주세요")
                @Max(value = 6, message = "반복 요일은 0(일) ~ 6(토) 으로 보내 주세요") Integer>
            repeatDays,
    // "19:00" 처럼 두 자리:두 자리. 00:00 ~ 23:59 만 됩니다.
    @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "시작 시각은 19:00 처럼 보내 주세요")
        String startTime,
    // FE 의 타이머와 같은 범위입니다. (계획의 목표 시간과 같음)
    @NotNull(message = "카드 길이(분)를 보내 주세요")
        @Min(value = 1, message = "카드 길이는 1분 이상이어야 해요")
        @Max(value = 600, message = "카드 길이는 600분(10시간) 이하여야 해요")
        Integer durationMinutes,
    @NotEmpty(message = "체크리스트를 하나 이상 적어 주세요") @Size(max = 20, message = "체크리스트는 20개까지 적을 수 있어요")
        List<@NotNull(message = "체크리스트 항목을 입력해 주세요") @Valid ChecklistItemRequest> checklist,
    // FE 의 id 는 글자라서 글자로 받습니다. 숫자만, 18자리까지. 빈 글자("")는 "목표 없음" 입니다.
    @Pattern(regexp = "\\d{0,18}", message = "목표 번호가 올바르지 않아요") String goalId,
    // 과목 번호. goalId 와 같은 모양입니다. 빈 글자("")는 "과목 없음" 입니다. (이슈 #404)
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

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다. 그래서 위의 검사는 공백을 지운 값으로 돌아갑니다.
  public CardCreateRequest {
    title = title == null ? null : title.trim();
    expectedDoneReason = expectedDoneReason == null ? null : expectedDoneReason.trim();
    expectedFailCauses = trimAll(expectedFailCauses);
  }

  // 글자 목록의 글자마다 앞뒤 공백을 지웁니다. 목록이 없으면(null) 그대로 null 입니다. (바꾸기 요청도 같이 씁니다)
  static List<String> trimAll(List<String> texts) {
    if (texts == null) {
      return null;
    }
    List<String> trimmed = new ArrayList<>();
    for (String text : texts) {
      trimmed.add(text == null ? null : text.trim());
    }
    return trimmed;
  }
}
