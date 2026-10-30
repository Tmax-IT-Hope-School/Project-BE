package com.ithopeschool.projectbe.domain.call;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.bias.BiasHabit;
import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import com.ithopeschool.projectbe.domain.call.dto.BiasHabitResponse;
import com.ithopeschool.projectbe.domain.call.dto.CallFinishResponse;
import com.ithopeschool.projectbe.domain.call.dto.CallRecordResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Controller 만 띄워서 "요청 모양 검사" 와 "응답 모양·상태 번호" 를 확인합니다. 진짜 로직(CallRecordService)은 가짜로 바꿉니다.
@WebMvcTest(CallRecordController.class)
class CallRecordControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CallRecordService callRecordService;

  private static final String VALID =
      """
      { "lines": [ { "speaker": "ai", "text": "어제 약속 지켰어?" },
                   { "speaker": "me", "text": "아니 이따가 하려고 했어" } ] }
      """;

  private ResultActions finish(String id, String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/calls/" + id + "/finish")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private void expectRejected(String body, String message) throws Exception {
    finish("12", body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
  }

  @Test
  @DisplayName("마무리하면 200 과 결론(요약·습관·약속)을 돌려준다")
  void returnsTheConclusion() throws Exception {
    given(callRecordService.finish(eq(12L), any()))
        .willReturn(
            new CallFinishResponse(
                "12",
                false,
                "폰 때문에 늦었다고 함.",
                CallFinishResponse.Bias.of(CognitiveBias.FUTURE_SELF, "이따가 하면 된다고 함"),
                "phone",
                "학원에서 오면",
                "폰을 서랍에 넣기",
                null,
                3,
                null));

    finish("12", VALID)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.callId").value("12"))
        .andExpect(jsonPath("$.showHelp").value(false))
        .andExpect(jsonPath("$.bias.key").value("FUTURE_SELF"))
        .andExpect(jsonPath("$.bias.label").value("미래의 나 믿기"))
        .andExpect(jsonPath("$.bias.solution").value(CognitiveBias.FUTURE_SELF.getSolution()))
        .andExpect(jsonPath("$.missReason").value("phone"))
        .andExpect(jsonPath("$.promiseKept").doesNotExist());
  }

  @Test
  @DisplayName("빈 자막([])도 받는다 (학생이 한마디도 안 한 통화)")
  void acceptsEmptyLines() throws Exception {
    given(callRecordService.finish(eq(12L), any())).willReturn(CallFinishResponse.help("12"));

    finish("12", "{\"lines\":[]}").andExpect(status().isOk());
  }

  @Test
  @DisplayName("자막이 없거나, speaker 가 ai·me 가 아니거나, 줄이 너무 많거나 길면 400 이고 서비스를 부르지 않는다")
  void rejectsBadTranscripts() throws Exception {
    expectRejected("{}", "자막(lines)을 보내 주세요. 없으면 빈 목록([])을 보내 주세요");
    expectRejected(
        "{\"lines\":[{\"speaker\":\"teacher\",\"text\":\"안녕\"}]}", "speaker 는 ai 또는 me 예요");
    expectRejected("{\"lines\":[{\"speaker\":\"me\"}]}", "한 말(text)을 보내 주세요");
    expectRejected(
        "{\"lines\":[{\"speaker\":\"me\",\"text\":\"" + "가".repeat(1001) + "\"}]}",
        "자막 한 줄(text)은 1000자까지예요");
    String tooMany =
        "{\"lines\":["
            + String.join(",", Collections.nCopies(201, "{\"speaker\":\"me\",\"text\":\"응\"}"))
            + "]}";
    expectRejected(tooMany, "자막(lines)은 200줄까지 보낼 수 있어요");
    verifyNoInteractions(callRecordService);
  }

  @Test
  @DisplayName("서비스가 던진 에러는 약속한 상태 번호로 나간다 (404 · 409 · 503)")
  void errorsFromTheService() throws Exception {
    given(callRecordService.finish(eq(99L), any()))
        .willThrow(new ApiException(ErrorCode.NOT_FOUND, "통화 기록을 찾을 수 없어요"));
    given(callRecordService.finish(eq(12L), any()))
        .willThrow(new ApiException(ErrorCode.CONFLICT, "이미 마무리한 통화예요"));

    finish("99", VALID).andExpect(status().isNotFound());
    finish("12", VALID).andExpect(status().isConflict());
  }

  @Test
  @DisplayName("내 통화 기록과 자주 보이는 습관을 200 과 목록으로 돌려준다")
  void listsMyCallsAndHabits() throws Exception {
    given(callRecordService.getRecentCalls())
        .willReturn(
            List.of(
                new CallRecordResponse(
                    "12", LocalDate.of(2026, 10, 14), "요약", null, "phone", null, null, null)));
    given(callRecordService.getHabits())
        .willReturn(
            List.of(
                BiasHabitResponse.from(
                    new BiasHabit(
                        CognitiveBias.PLANNING_FALLACY, 3, LocalDate.of(2026, 10, 13), null))));

    mockMvc
        .perform(get("/api/v1/calls"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].callId").value("12"))
        .andExpect(jsonPath("$[0].date").value("2026-10-14"))
        .andExpect(jsonPath("$[0].missReason").value("phone"));
    mockMvc
        .perform(get("/api/v1/calls/habits"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].key").value("PLANNING_FALLACY"))
        .andExpect(jsonPath("$[0].label").value("계획 오류"))
        .andExpect(jsonPath("$[0].days").value(3))
        .andExpect(jsonPath("$[0].lastDate").value("2026-10-13"));
  }
}
