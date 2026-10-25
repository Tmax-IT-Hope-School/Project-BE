package com.ithopeschool.projectbe.domain.ai.dto;

import java.time.LocalDate;
import java.util.List;

// AI 하루 계획의 결과입니다. **저장하지 않은 카드 초안**입니다.
//   { "date": "2026-10-18", "blockMinutes": 25, "totalMinutes": 100,
//     "drafts": [ { "date": "2026-10-18", "startTime": "16:40", "durationMinutes": 25,
//                   "title": "수학 문제집", "checklist": ["3단원 15문제"], "goalId": "4", "subjectId": "16"
// }, … ],
//     "evidence": ["최근 14일 공부 기록 12번의 한 번 길이 가운데값이 24분이라 25분으로 잡았어요", …] }
// - drafts 의 한 장은 "말로 카드 만들기" 의 초안과 같은 칸입니다. 학생이 고른 카드만 FE 가 /api/v1/cards 로 저장합니다.
// - 시작 시각은 고정 일정(학교 · 학원) · 그날 카드 · 수면 보호 시간을 피한 빈 시간입니다.
// - evidence: 왜 이렇게 짰는지. 숫자 · 시각은 모두 내 기록으로 코드가 정했고, 그 근거입니다.
// - drafts 가 빈 목록이면 짤 것이 없는 것입니다. (그날 이미 충분함 · 빈 시간이 없음) 이유는 evidence 에 있습니다.
public record DayPlanResponse(
    LocalDate date, int blockMinutes, int totalMinutes, List<Draft> drafts, List<String> evidence) {

  // 카드 초안 한 장. goalId 는 내 진행 중 목표 번호(글자), 목표 없는 칸이면 null
  // subjectId 는 내 과목 번호(글자). AI 가 내 과목 중에서 고르고, 못 고르면 제목에 든 과목 이름으로 찾습니다. 없으면 null (#457)
  public record Draft(
      LocalDate date,
      String startTime,
      int durationMinutes,
      String title,
      List<String> checklist,
      String goalId,
      String subjectId) {

    // 과목 없이 만드는 모양 (#457 전, 테스트에서 씀)
    public Draft(
        LocalDate date,
        String startTime,
        int durationMinutes,
        String title,
        List<String> checklist,
        String goalId) {
      this(date, startTime, durationMinutes, title, checklist, goalId, null);
    }
  }
}
