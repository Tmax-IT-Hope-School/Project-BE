package com.ithopeschool.projectbe.domain.weeklyscore;

import com.ithopeschool.projectbe.domain.weeklyscore.dto.WeeklyScoreResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 주간 점수 API 입니다. 계산은 WeeklyScoreService 가 하고, 여기서는 받은 값을 넘기기만 합니다. (이슈 #412)
@Tag(name = "주간 점수")
@RestController
@RequestMapping("/api/v1/weekly-scores")
@RequiredArgsConstructor
public class WeeklyScoreController {

  private final WeeklyScoreService weeklyScoreService;

  @Operation(
      summary = "한 주의 점수와 지난주 점수 (정답률 + 공부 달성률의 평균 · 퀴즈 10문제 미만이거나 계획이 없으면 점수 대신 null · 토큰 필요)")
  @GetMapping("/{weekStart}")
  public WeeklyScoreResponse getWeeklyScore(
      @Parameter(description = "그 주의 월요일 날짜. 예: 2026-10-19")
          @PathVariable
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate weekStart) {
    return weeklyScoreService.getWeeklyScore(weekStart);
  }
}
