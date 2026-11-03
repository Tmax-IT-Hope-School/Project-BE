package com.ithopeschool.projectbe.domain.insight;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.insight.dto.ConditionPerformanceResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller 만 띄워서 주소와 응답 모양(JSON 이름·날짜 글자)을 확인합니다. 진짜 로직은 가짜입니다.
@WebMvcTest(ConditionPerformanceController.class)
class ConditionPerformanceControllerTest {

  private static final String URL = "/api/v1/insights/condition-performance";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ConditionPerformanceService service;

  @Test
  @DisplayName("metric 을 안 보내면 studyMinutes 로 계산해 200 과 비교 결과를 돌려준다")
  void returnsComparison() throws Exception {
    // 응답 모양만 보려고, 작은 재료로 진짜 계산을 돌려 응답을 만듭니다.
    List<ConditionPerformance.Day> days =
        List.of(
            new ConditionPerformance.Day(LocalDate.of(2026, 10, 1), 120, 450, null, 5, null),
            new ConditionPerformance.Day(LocalDate.of(2026, 10, 2), 10, 300, null, 2, null));
    ConditionPerformanceResponse response =
        ConditionPerformanceResponse.of(
            LocalDate.of(2026, 9, 17),
            LocalDate.of(2026, 10, 16),
            PerformanceMetric.STUDY_MINUTES,
            ConditionPerformance.of(days));
    given(service.getConditionPerformance("studyMinutes")).willReturn(response);

    mockMvc
        .perform(get(URL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.from").value("2026-09-17"))
        .andExpect(jsonPath("$.metric").value("studyMinutes"))
        .andExpect(jsonPath("$.dayCount").value(2))
        .andExpect(jsonPath("$.topDays[0].date").value("2026-10-01"))
        .andExpect(jsonPath("$.topDays[0].value").value(120))
        .andExpect(jsonPath("$.conditions[0].condition").value("sleepMinutes"))
        .andExpect(jsonPath("$.conditions[0].topAverage").value(450.0))
        .andExpect(jsonPath("$.conditions[0].difference").value(150.0))
        .andExpect(jsonPath("$.conditions[0].topDayCount").value(1))
        .andExpect(jsonPath("$.conditions[0].bottomDates[0]").value("2026-10-02"))
        .andExpect(jsonPath("$.conditions[0].hypothesis").value(true))
        .andExpect(jsonPath("$.conditions[1].condition").value("condition"));
  }

  @Test
  @DisplayName("모르는 metric 이면 400")
  void unknownMetricIs400() throws Exception {
    given(service.getConditionPerformance("score"))
        .willThrow(
            new ApiException(
                ErrorCode.VALIDATION_ERROR, "metric 은 studyMinutes 또는 achievementRate 로 보내 주세요"));

    mockMvc
        .perform(get(URL).param("metric", "score"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName("로그인하지 않으면 401")
  void requiresLogin() throws Exception {
    given(service.getConditionPerformance("studyMinutes"))
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
  }
}
