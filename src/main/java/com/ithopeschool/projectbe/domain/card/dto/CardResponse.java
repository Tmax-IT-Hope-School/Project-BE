package com.ithopeschool.projectbe.domain.card.dto;

import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.ChecklistItem;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// 카드 한 장을 FE 에 알려 주는 모양입니다. FE 의 Card 와 같은 이름을 씁니다.
//   { "id": "7", "title": "수학 문제집", "date": "2026-10-12", "repeatDays": [1, 3],
//     "startTime": "19:00", "durationMinutes": 60,
//     "checklist": [ { "id": "i1", "text": "42~45쪽" } ], "goalId": null, "subjectId": "3",
//     "priority": "flexible", "type": "self", "expectedDone": null, "expectedDoneReason": null,
//     "expectedFailCauses": null, "alert": null, "planningMs": null, "createdAt": 1790000000000,
//     "bufferWarning": null }
// - id, goalId, subjectId 는 FE 타입이 글자(string)라서 숫자를 글자로 바꿔 보냅니다.
// - subjectId 는 카드의 과목입니다. "과목 없음" 이면 null 입니다. (이슈 #404)
// - 1회성 카드는 repeatDays 가 null, 예상 실패 원인이 없으면 expectedFailCauses 가 null 입니다. (값이 없는 칸은 null)
// - alert 가 null 이면 기본 알림(정각 · 메시지 · 기본 문구)입니다.
// - createdAt 은 밀리초 숫자입니다. (docs/API.md 2-2)
// - 누구의 카드인지(userId)는 담지 않습니다.
// - bufferWarning 은 카드를 만들거나 고친 응답에만 있는 경고 문장입니다. 앞뒤 카드 · 고정 일정과 10분 여유가 없으면
//   "학원 18:00~21:00 과 사이가 5분뿐이에요. 10분 여유를 두면 좋아요." 처럼 오고, 넉넉하면 null 입니다. (이슈 #223, 막지 않음)
//   목록 응답에서는 늘 null 입니다.
public record CardResponse(
    String id,
    String title,
    LocalDate date,
    List<Integer> repeatDays,
    String startTime,
    int durationMinutes,
    List<ChecklistItemResponse> checklist,
    String goalId,
    String subjectId,
    String priority,
    String type,
    Integer expectedDone,
    String expectedDoneReason,
    List<String> expectedFailCauses,
    AlertResponse alert,
    Long planningMs,
    long createdAt,
    String bufferWarning) {

  // 체크리스트 항목 하나 (FE 의 ChecklistItem)
  public record ChecklistItemResponse(String id, String text) {}

  // 알림 (FE 의 CardAlert). text 가 null 이면 기본 문구입니다.
  public record AlertResponse(String method, int minutesBefore, String text) {}

  // LocalTime 을 그냥 글자로 바꾸면 "19:00:00" 처럼 초가 붙을 수 있어서, 몇 시 몇 분만 꺼냅니다.
  private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

  public static CardResponse from(Card card) {
    return from(card, null);
  }

  // 만들거나 고친 카드의 응답. bufferWarning 은 ScheduleCheck.bufferWarning 이 만든 문장(없으면 null)입니다.
  public static CardResponse from(Card card, String bufferWarning) {
    List<ChecklistItemResponse> checklist = new ArrayList<>();
    for (ChecklistItem item : card.getChecklist()) {
      checklist.add(new ChecklistItemResponse(item.getItemKey(), item.getText()));
    }
    List<Integer> repeatDays = card.repeatDayList();
    List<String> failCauses = new ArrayList<>(card.getExpectedFailCauses());
    // 알림 방법이 비어 있으면 기본 알림이라 null 로 보냅니다. (방법과 몇 분 전은 늘 같이 저장됩니다)
    AlertResponse alert =
        card.getAlertMethod() == null
            ? null
            : new AlertResponse(
                card.getAlertMethod().toValue(), card.getAlertMinutesBefore(), card.getAlertText());

    return new CardResponse(
        String.valueOf(card.getId()),
        card.getTitle(),
        card.getCardDate(),
        repeatDays.isEmpty() ? null : repeatDays,
        card.getStartTime() == null ? null : card.getStartTime().format(HOUR_MINUTE),
        card.getDurationMinutes(),
        checklist,
        card.getGoalId() == null ? null : String.valueOf(card.getGoalId()),
        card.getSubjectId() == null ? null : String.valueOf(card.getSubjectId()),
        card.getPriority().toValue(),
        card.getType().toValue(),
        card.getExpectedDone(),
        card.getExpectedDoneReason(),
        failCauses.isEmpty() ? null : failCauses,
        alert,
        card.getPlanningMs(),
        card.getCreatedAt().toEpochMilli(),
        bufferWarning);
  }
}
