package com.ithopeschool.projectbe.domain.quizresult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.quizresult.dto.QuizResultResponse;
import com.ithopeschool.projectbe.domain.quizresult.dto.QuizStatsResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller 만 띄워서 주소 · 상태 번호 · 입력 검사 · 응답 모양을 확인합니다. 진짜 로직은 가짜로 바꿉니다.
@WebMvcTest(QuizResultController.class)
class QuizResultControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private QuizResultService quizResultService;

  private static final LocalDate OCT_24 = LocalDate.of(2026, 10, 24);

  @Test
  @DisplayName("저장은 201 이고 저장한 결과를 돌려준다")
  void save() throws Exception {
    given(quizResultService.save(any()))
        .willReturn(new QuizResultResponse(1L, 3L, "함수", OCT_24, 2, 3, List.of(40, 30, 50)));

    mockMvc
        .perform(
            post("/api/v1/quiz-results")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "subjectId": 3, "unit": "함수", "correctCount": 2, "totalCount": 3,
                      "questionSeconds": [40, 30, 50] }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.quizDate").value("2026-10-24"))
        .andExpect(jsonPath("$.questionSeconds.length()").value(3));
  }

  @Test
  @DisplayName("빈 단원 · 0문제 · 음수 시간은 400 이고 Service 는 부르지 않는다")
  void invalidBody() throws Exception {
    String[] bodies = {
      """
      { "subjectId": 3, "unit": " ", "correctCount": 2, "totalCount": 3, "questionSeconds": [1, 2, 3] }
      """,
      """
      { "subjectId": 3, "unit": "함수", "correctCount": 0, "totalCount": 0, "questionSeconds": [1] }
      """,
      """
      { "subjectId": 3, "unit": "함수", "correctCount": 1, "totalCount": 1, "questionSeconds": [-5] }
      """,
      """
      { "unit": "함수", "correctCount": 1, "totalCount": 1, "questionSeconds": [5] }
      """
    };
    for (String body : bodies) {
      mockMvc
          .perform(
              post("/api/v1/quiz-results").contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isBadRequest());
    }
    verifyNoInteractions(quizResultService);
  }

  @Test
  @DisplayName("통계는 200 이고 과목 목록을 돌려준다")
  void stats() throws Exception {
    given(quizResultService.getStats(OCT_24.minusDays(6), OCT_24))
        .willReturn(new QuizStatsResponse(OCT_24.minusDays(6), OCT_24, List.of(), List.of()));

    mockMvc
        .perform(
            get("/api/v1/quiz-results/stats").param("from", "2026-10-18").param("to", "2026-10-24"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.from").value("2026-10-18"))
        .andExpect(jsonPath("$.subjects.length()").value(0));
  }

  @Test
  @DisplayName("통계 날짜 모양이 틀리면 400 이고 Service 는 부르지 않는다")
  void statsWrongDate() throws Exception {
    mockMvc
        .perform(get("/api/v1/quiz-results/stats").param("from", "어제").param("to", "2026-10-24"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(quizResultService);
  }
}
