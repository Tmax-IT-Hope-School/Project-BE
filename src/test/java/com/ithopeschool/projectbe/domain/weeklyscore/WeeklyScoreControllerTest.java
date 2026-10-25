package com.ithopeschool.projectbe.domain.weeklyscore;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.weeklyscore.dto.WeeklyScoreResponse;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller 만 띄워서 주소 · 상태 번호 · 응답 모양을 확인합니다. 진짜 로직은 가짜로 바꿉니다. (이슈 #412)
@WebMvcTest(WeeklyScoreController.class)
class WeeklyScoreControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private WeeklyScoreService weeklyScoreService;

  private static final LocalDate THIS_MONDAY = LocalDate.of(2026, 10, 19);

  @Test
  @DisplayName("200 이고 점수 · 재료 · 안내 값 · 지난주 비교를 돌려준다")
  void getWeeklyScore() throws Exception {
    given(weeklyScoreService.getWeeklyScore(THIS_MONDAY))
        .willReturn(new WeeklyScoreResponse(THIS_MONDAY, true, 77, 80, 75, 8, 10, 0, false, 72, 5));

    mockMvc
        .perform(get("/api/v1/weekly-scores/2026-10-19"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.weekStart").value("2026-10-19"))
        .andExpect(jsonPath("$.inProgress").value(true))
        .andExpect(jsonPath("$.score").value(77))
        .andExpect(jsonPath("$.moreQuestionsNeeded").value(0))
        .andExpect(jsonPath("$.planNeeded").value(false))
        .andExpect(jsonPath("$.scoreChange").value(5));
  }

  @Test
  @DisplayName("점수를 낼 수 없으면 score 는 0 이 아니라 null 이다")
  void noScore() throws Exception {
    given(weeklyScoreService.getWeeklyScore(THIS_MONDAY))
        .willReturn(
            new WeeklyScoreResponse(
                THIS_MONDAY, true, null, null, null, 2, 3, 7, true, null, null));

    mockMvc
        .perform(get("/api/v1/weekly-scores/2026-10-19"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.score").isEmpty())
        .andExpect(jsonPath("$.moreQuestionsNeeded").value(7))
        .andExpect(jsonPath("$.planNeeded").value(true));
  }

  @Test
  @DisplayName("날짜 모양이 틀리면 400 이고 Service 는 부르지 않는다")
  void wrongDate() throws Exception {
    mockMvc.perform(get("/api/v1/weekly-scores/이번주")).andExpect(status().isBadRequest());
    verifyNoInteractions(weeklyScoreService);
  }
}
