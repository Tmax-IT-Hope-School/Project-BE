package com.ithopeschool.projectbe.domain.variant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.variant.dto.AttemptResponse;
import com.ithopeschool.projectbe.domain.variant.dto.SourceProblemResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Controller 만 띄워서 요청 모양 검사와 응답 상태 번호를 확인합니다. 진짜 로직(Service)은 가짜입니다.
@WebMvcTest(VariantController.class)
class VariantControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SourceProblemService sourceProblemService;
  @MockitoBean private AttemptService attemptService;
  @MockitoBean private VariantQueryService queryService;
  @MockitoBean private VariantQuizSourceService quizSourceService;

  // 확인 문제 결과 요청 본문. questionJson 은 문제 하나의 JSON
  private static String quizBody(String questionJson) {
    return "{\"topic\":\"국어 비문학\",\"solvedAt\":1792841400000,\"guessed\":false,\"questions\":["
        + questionJson
        + "]}";
  }

  private static final String QUIZ_QUESTION =
      "{\"question\":\"문제\",\"passage\":null,\"choices\":[\"가\",\"나\",\"다\",\"라\"],"
          + "\"answerIndex\":2,\"chosenIndex\":1}";

  @Test
  @DisplayName("확인 문제 결과는 200 과 저장 · 건너뛴 수를 돌려준다")
  void quizResultsReturnsCounts() throws Exception {
    given(quizSourceService.submit(any()))
        .willReturn(
            new com.ithopeschool.projectbe.domain.variant.dto.VariantQuizSourceResponse(
                1,
                new com.ithopeschool.projectbe.domain.variant.dto.VariantQuizSourceResponse.Skipped(
                    0, 0, 0)));

    postJson("/api/v1/variant/quiz-results", quizBody(QUIZ_QUESTION))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.registered").value(1))
        .andExpect(jsonPath("$.skipped.answerMismatch").value(0));
  }

  @Test
  @DisplayName("확인 문제 결과: 보기가 4개가 아니거나, 번호가 0 ~ 3 밖 · 소수이거나, 문제가 0개 · 6개면 400 이고 Service 를 부르지 않는다")
  void quizResultsValidation() throws Exception {
    postJson(
            "/api/v1/variant/quiz-results",
            quizBody(QUIZ_QUESTION.replace("[\"가\",\"나\",\"다\",\"라\"]", "[\"가\",\"나\",\"다\"]")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("보기는 4개를 보내 주세요"));
    postJson(
            "/api/v1/variant/quiz-results",
            quizBody(QUIZ_QUESTION.replace("\"answerIndex\":2", "\"answerIndex\":4")))
        .andExpect(status().isBadRequest());
    postJson(
            "/api/v1/variant/quiz-results",
            quizBody(QUIZ_QUESTION.replace("\"chosenIndex\":1", "\"chosenIndex\":1.5")))
        .andExpect(status().isBadRequest());
    postJson(
            "/api/v1/variant/quiz-results",
            quizBody(
                QUIZ_QUESTION.replace(
                    "\"passage\":null", "\"passage\":\"" + "가".repeat(2001) + "\"")))
        .andExpect(status().isBadRequest());
    postJson("/api/v1/variant/quiz-results", quizBody("")).andExpect(status().isBadRequest());
    String six = String.join(",", java.util.Collections.nCopies(6, QUIZ_QUESTION));
    postJson("/api/v1/variant/quiz-results", quizBody(six)).andExpect(status().isBadRequest());
    postJson(
            "/api/v1/variant/quiz-results",
            "{\"solvedAt\":1,\"guessed\":false,\"questions\":[" + QUIZ_QUESTION + "]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("주제(topic)를 보내 주세요"));
    verifyNoInteractions(quizSourceService);
  }

  private ResultActions postJson(String url, String body) throws Exception {
    return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  @DisplayName("원본 등록은 201 과 등록 결과를 돌려준다")
  void registerReturns201() throws Exception {
    given(sourceProblemService.register(any()))
        .willReturn(new SourceProblemResponse("3", "supported", "CM1-U05", "CM1-U05-T03", "7"));

    postJson(
            "/api/v1/variant/source-problems",
            "{\"problemText\":\"문제\",\"isCorrect\":false,\"solvedAt\":1792841400000}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("3"))
        .andExpect(jsonPath("$.supportStatus").value("supported"))
        .andExpect(jsonPath("$.reviewItemId").value("7"));
  }

  @Test
  @DisplayName("원본 등록: 문제 글 · 맞았는지 · 푼 시각이 없거나 글이 너무 길면 400 이고 Service 를 부르지 않는다")
  void registerValidation() throws Exception {
    postJson("/api/v1/variant/source-problems", "{\"isCorrect\":false,\"solvedAt\":1}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("문제 글을 보내 주세요"));
    postJson("/api/v1/variant/source-problems", "{\"problemText\":\"문제\",\"solvedAt\":1}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("맞혔는지(isCorrect)를 보내 주세요"));
    postJson("/api/v1/variant/source-problems", "{\"problemText\":\"문제\",\"isCorrect\":true}")
        .andExpect(status().isBadRequest());
    postJson(
            "/api/v1/variant/source-problems",
            "{\"problemText\":\"" + "가".repeat(2001) + "\",\"isCorrect\":true,\"solvedAt\":1}")
        .andExpect(status().isBadRequest());
    postJson(
            "/api/v1/variant/source-problems",
            "{\"problemText\":\"문제\",\"isCorrect\":true,\"solvedAt\":1,\"choices\":[\"\"]}")
        .andExpect(status().isBadRequest());
    verifyNoInteractions(sourceProblemService);
  }

  @Test
  @DisplayName("답 내기: 답이 없거나, 걸린 시간이 소수 · 음수면 400")
  void attemptValidation() throws Exception {
    postJson("/api/v1/variant/assignments/1/attempts", "{}").andExpect(status().isBadRequest());
    postJson("/api/v1/variant/assignments/1/attempts", "{\"answer\":\"1\",\"timeSpentSec\":1.5}")
        .andExpect(status().isBadRequest());
    postJson("/api/v1/variant/assignments/1/attempts", "{\"answer\":\"1\",\"timeSpentSec\":-1}")
        .andExpect(status().isBadRequest());
    postJson("/api/v1/variant/assignments/abc/attempts", "{\"answer\":\"1\"}")
        .andExpect(status().isBadRequest());
    verifyNoInteractions(attemptService);
  }

  @Test
  @DisplayName("답 내기는 200 과 채점 결과를 돌려준다")
  void attemptReturnsResult() throws Exception {
    given(attemptService.submit(eq(5L), any()))
        .willReturn(
            new AttemptResponse(
                false,
                "2",
                "\\frac{3}{2}",
                "해설",
                "부호 실수",
                new AttemptResponse.ReviewItemSummary("9", "active", -1)));

    postJson("/api/v1/variant/assignments/5/attempts", "{\"answer\":\"0\",\"timeSpentSec\":30}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isCorrect").value(false))
        .andExpect(jsonPath("$.chosenDistractorRationale").value("부호 실수"))
        .andExpect(jsonPath("$.reviewItem.currentLevel").value(-1));
  }
}
