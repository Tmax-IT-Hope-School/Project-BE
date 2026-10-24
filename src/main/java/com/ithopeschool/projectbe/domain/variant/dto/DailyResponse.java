package com.ithopeschool.projectbe.domain.variant.dto;

import java.time.LocalDate;
import java.util.List;

// 그날의 일일 과제 (GET /api/v1/variant/daily)
//   items          : 낸 과제들 (정답 · 해설 · 검증 코드 · 선지 해설은 없음)
//   preparingCount : 낼 차례지만 아직 변형을 만들지 못한 수 (예약 작업이 만들면 들어옵니다)
public record DailyResponse(LocalDate date, List<Item> items, int preparingCount) {

  //   level        : -1 · 0 · 1
  //   format       : mc5 · short
  //   choicesLatex : 선지 화면용 LaTeX (short 면 빈 목록)
  //   status       : pending · answered · expired
  public record Item(
      String assignmentId,
      int level,
      String format,
      String problemText,
      List<String> choicesLatex,
      String status) {}
}
