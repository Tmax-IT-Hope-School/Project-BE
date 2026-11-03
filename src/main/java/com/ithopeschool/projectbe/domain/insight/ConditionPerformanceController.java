package com.ithopeschool.projectbe.domain.insight;

import com.ithopeschool.projectbe.domain.insight.dto.ConditionPerformanceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 조건-성과 관계 API 의 입구입니다. 로그인이 필요합니다. (이슈 #38, docs/API.md "조건-성과 관계")
@Tag(name = "인사이트")
@RestController
@RequiredArgsConstructor
public class ConditionPerformanceController {

  private final ConditionPerformanceService service;

  @Operation(summary = "조건-성과 관계 (최근 30일, 어제까지 · 성과 상위 25% 날 vs 하위 25% 날의 수면·취침·컨디션·걸음 · 토큰 필요)")
  @GetMapping("/api/v1/insights/condition-performance")
  public ConditionPerformanceResponse getConditionPerformance(
      @Parameter(description = "무엇을 성과로 볼지: studyMinutes(순공, 기본) 또는 achievementRate(달성률)")
          @RequestParam(defaultValue = "studyMinutes")
          String metric) {
    return service.getConditionPerformance(metric);
  }
}
