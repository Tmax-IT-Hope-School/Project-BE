package com.ithopeschool.projectbe.domain.ai.dto;

import com.ithopeschool.projectbe.domain.call.PlanTalk;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

// "계획 핑퐁" 요청의 모양입니다. (2026-10-18)
//   { "drafts": [ { "date": "2026-10-19", "startTime": "19:00", "durationMinutes": 30,
//                   "title": "미적분 문제집", "checklist": ["3단원 10문제"] } ],
//     "request": "영어는 빼 줘",
//     "focus": { "kind": "SUNEUNG", "name": null }, "subjects": ["미적분Ⅰ"] }
// - drafts   : 필수. 지금 계획 (코치가 짜 준 것 또는 사진으로 받은 것, 1~14장)
// - request  : 필수. 학생의 요청 한 줄 (100자). [줄여 줘] 같은 칩도 이 글로 보냅니다.
// - focus · subjects : 선택. AI 하루 계획(DayPlanRequest)과 같은 뜻
// - planTalk : 선택. 계획 방에서 오늘 나눈 말 몇 줄 (8줄 · 한 줄 200자, AI 하루 계획의 planTalk 와 같음, 2026-10-25 #431)
//              "줄여 줘" 처럼 짧은 요청도 대화를 보고 가장 그럴듯하게 고치게 합니다. 저장 · 로그 안 함.
public record DayPlanReviseRequest(
    @NotEmpty(message = "지금 계획을 보내 주세요") @Size(max = 14, message = "계획은 14장까지 보낼 수 있어요")
        List<@Valid Draft> drafts,
    @NotBlank(message = "어떻게 고칠지 적어 주세요") @Size(max = 100, message = "요청은 100자 이하로 보내 주세요")
        String request,
    @Valid DayPlanRequest.Focus focus,
    @Size(max = 8, message = "과목은 8개까지 보낼 수 있어요")
        List<
                @NotBlank(message = "빈 과목이 있어요") @Size(max = 20, message = "과목 이름은 20자 이하로 보내 주세요")
                String>
            subjects,
    @Size(max = PlanTalk.MAX_LINES, message = "계획 방 대화(planTalk)는 8줄까지 보낼 수 있어요")
        List<@Size(max = PlanTalk.MAX_LINE_LENGTH, message = "계획 방 대화 한 줄은 200자 이하로 보내 주세요") String>
            planTalk) {

  // 계획 방 대화 없이 보내는 모양 (#431 전의 모양, 테스트에서 씀)
  public DayPlanReviseRequest(
      List<Draft> drafts, String request, DayPlanRequest.Focus focus, List<String> subjects) {
    this(drafts, request, focus, subjects, null);
  }

  // 요청 앞뒤 공백을 지우고, 과목을 안 보냈으면 빈 목록으로 봅니다.
  public DayPlanReviseRequest {
    request = request == null ? null : request.trim();
    subjects = subjects == null ? List.of() : subjects.stream().map(String::trim).toList();
  }

  // 지금 계획의 카드 한 장
  public record Draft(
      @NotNull(message = "카드 날짜를 보내 주세요") LocalDate date,
      @NotNull(message = "카드 시작 시각을 보내 주세요")
          @Pattern(regexp = "\\d{2}:\\d{2}", message = "시작 시각은 HH:MM 으로 보내 주세요")
          String startTime,
      @NotNull(message = "카드 길이를 보내 주세요")
          @Min(value = 5, message = "카드 길이는 5 ~ 120분이에요")
          @Max(value = 120, message = "카드 길이는 5 ~ 120분이에요")
          Integer durationMinutes,
      @NotBlank(message = "카드 제목을 보내 주세요") @Size(max = 30, message = "카드 제목은 30자 이하로 보내 주세요")
          String title,
      @Size(max = 5, message = "체크리스트는 5개까지 보낼 수 있어요")
          List<@Size(max = 50, message = "체크리스트 항목은 50자 이하로 보내 주세요") String> checklist) {

    // 체크리스트를 안 보냈으면 빈 목록으로 봅니다.
    public Draft {
      checklist = checklist == null ? List.of() : checklist;
    }
  }
}
