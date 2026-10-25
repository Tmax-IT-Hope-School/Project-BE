package com.ithopeschool.projectbe.domain.card;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.card.dto.CardAlertRequest;
import com.ithopeschool.projectbe.domain.card.dto.CardCreateRequest;
import com.ithopeschool.projectbe.domain.card.dto.CardResponse;
import com.ithopeschool.projectbe.domain.card.dto.CardUpdateRequest;
import com.ithopeschool.projectbe.domain.card.dto.ChecklistItemRequest;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Controller 만 띄워서 "요청 모양 검사" 와 "응답 모양·상태 번호" 를 확인합니다. 진짜 로직(CardService)은 가짜로 바꿉니다.
@WebMvcTest(CardController.class)
class CardControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CardService cardService;

  private static final LocalDate OCT_12 = LocalDate.of(2026, 10, 12);

  // 반복 카드 한 장 (과목은 3번, 알림 · 목표 · 예상 칸은 비어 있음)
  private static final CardResponse MATH =
      new CardResponse(
          "7",
          "수학 문제집",
          LocalDate.of(2026, 10, 1),
          List.of(1, 3),
          "19:00",
          60,
          List.of(new CardResponse.ChecklistItemResponse("i1", "42~45쪽")),
          null,
          "3",
          "flexible",
          "self",
          null,
          null,
          null,
          null,
          null,
          1790000000000L,
          null);

  // 카드 만들기 요청의 꼭 있어야 하는 칸. 바꾸고 싶은 칸만 뒤에 덧붙여 씁니다.
  private static final String VALID_CARD =
      "\"title\":\"수학\",\"date\":\"2026-10-12\",\"durationMinutes\":60,"
          + "\"checklist\":[{\"text\":\"42쪽\"}]";

  private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
    return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void expectRejected(ResultActions result, String message) throws Exception {
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(cardService);
  }

  // ---- 목록 ----

  @Test
  @DisplayName("목록은 200 이고, 카드마다 FE Card 의 열일곱 칸과 bufferWarning(목록에서는 null) 이 있다")
  void getCards() throws Exception {
    given(cardService.getCards(OCT_12)).willReturn(List.of(MATH));

    mockMvc
        .perform(get("/api/v1/cards").param("date", "2026-10-12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value("7"))
        .andExpect(jsonPath("$[0].title").value("수학 문제집"))
        .andExpect(jsonPath("$[0].date").value("2026-10-01"))
        .andExpect(jsonPath("$[0].repeatDays[1]").value(3))
        .andExpect(jsonPath("$[0].startTime").value("19:00"))
        .andExpect(jsonPath("$[0].durationMinutes").value(60))
        .andExpect(jsonPath("$[0].checklist[0].id").value("i1"))
        .andExpect(jsonPath("$[0].checklist[0].text").value("42~45쪽"))
        .andExpect(jsonPath("$[0].priority").value("flexible"))
        .andExpect(jsonPath("$[0].type").value("self"))
        // 카드의 과목 번호도 글자로 보냅니다. (이슈 #404)
        .andExpect(jsonPath("$[0].subjectId").value("3"))
        .andExpect(jsonPath("$[0].createdAt").value(1790000000000L))
        // 값이 없는 칸은 빼지 않고 null 로 보냅니다. (docs/API.md 4장)
        .andExpect(jsonPath("$[0].goalId").isEmpty())
        .andExpect(jsonPath("$[0].alert").isEmpty())
        // 버퍼 경고는 만들기 · 바꾸기 응답에만 있어서, 목록에서는 빼지 않고 null 로 보냅니다. (이슈 #223)
        .andExpect(jsonPath("$[0].bufferWarning").isEmpty())
        .andExpect(jsonPath("$[0].length()").value(18))
        .andExpect(jsonPath("$[0].userId").doesNotExist());
  }

  @Test
  @DisplayName("날짜를 안 보내면 전부 (Service 에 null 을 넘김), 카드가 없으면 빈 배열 []")
  void getAllCards() throws Exception {
    given(cardService.getCards(null)).willReturn(List.of());

    mockMvc
        .perform(get("/api/v1/cards"))
        .andExpect(status().isOk())
        .andExpect(content().json("[]", true));
    verify(cardService).getCards(null);
  }

  @Test
  @DisplayName("날짜 모양이 틀리면 400 이고, Service 는 부르지 않는다")
  void wrongDateFormat() throws Exception {
    mockMvc
        .perform(get("/api/v1/cards").param("date", "2026/10/12"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(cardService);
  }

  // ---- 만들기 ----

  @Test
  @DisplayName("만들기는 201 이고, 제목·체크리스트 글의 앞뒤 공백을 지우고 넘긴다")
  void create() throws Exception {
    given(cardService.create(any())).willReturn(MATH);

    send(
            post("/api/v1/cards"),
            """
            {"title":"  수학 문제집 ","date":"2026-10-01","repeatDays":[1,3],"startTime":"19:00",
             "durationMinutes":60,"checklist":[{"text":" 42~45쪽 "}],
             "alert":{"method":"call","minutesBefore":10}}
            """)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("7"));
    verify(cardService)
        .create(
            new CardCreateRequest(
                "수학 문제집",
                LocalDate.of(2026, 10, 1),
                List.of(1, 3),
                "19:00",
                60,
                List.of(new ChecklistItemRequest(null, "42~45쪽")),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new CardAlertRequest("call", 10, null),
                null));
  }

  // { 보낼 JSON, 돌아와야 할 문장 }
  static Stream<Arguments> wrongCreateRequests() {
    return Stream.of(
        Arguments.of("{" + VALID_CARD.replace("\"수학\"", "\"  \"") + "}", "카드 제목을 입력해 주세요"),
        Arguments.of(
            "{" + VALID_CARD.replace("\"수학\"", "\"" + "가".repeat(101) + "\"") + "}",
            "카드 제목은 100자 이하로 적어 주세요"),
        Arguments.of("{" + VALID_CARD.replace("\"date\":\"2026-10-12\",", "") + "}", "날짜를 보내 주세요"),
        Arguments.of(
            "{" + VALID_CARD.replace("\"durationMinutes\":60,", "") + "}", "카드 길이(분)를 보내 주세요"),
        Arguments.of("{" + VALID_CARD.replace(":60", ":0") + "}", "카드 길이는 1분 이상이어야 해요"),
        Arguments.of("{" + VALID_CARD.replace(":60", ":601") + "}", "카드 길이는 600분(10시간) 이하여야 해요"),
        Arguments.of(
            "{" + VALID_CARD.replace("[{\"text\":\"42쪽\"}]", "[]") + "}", "체크리스트를 하나 이상 적어 주세요"),
        Arguments.of("{" + VALID_CARD.replace("\"42쪽\"", "\" \"") + "}", "체크리스트 항목을 입력해 주세요"),
        Arguments.of(
            "{" + VALID_CARD.replace("\"42쪽\"", "\"" + "가".repeat(101) + "\"") + "}",
            "체크리스트 항목은 100자 이하로 적어 주세요"),
        Arguments.of(
            "{"
                + VALID_CARD.replace(
                    "[{\"text\":\"42쪽\"}]",
                    "[" + "{\"text\":\"a\"},".repeat(20) + "{\"text\":\"a\"}]")
                + "}",
            "체크리스트는 20개까지 적을 수 있어요"),
        Arguments.of("{" + VALID_CARD + ",\"repeatDays\":[7]}", "반복 요일은 0(일) ~ 6(토) 으로 보내 주세요"),
        Arguments.of("{" + VALID_CARD + ",\"repeatDays\":[-1]}", "반복 요일은 0(일) ~ 6(토) 으로 보내 주세요"),
        Arguments.of("{" + VALID_CARD + ",\"startTime\":\"7:00\"}", "시작 시각은 19:00 처럼 보내 주세요"),
        Arguments.of("{" + VALID_CARD + ",\"goalId\":\"abc\"}", "목표 번호가 올바르지 않아요"),
        Arguments.of("{" + VALID_CARD + ",\"subjectId\":\"수학\"}", "과목 번호가 올바르지 않아요"),
        Arguments.of("{" + VALID_CARD + ",\"expectedDone\":-1}", "예상 달성 개수는 0 이상이어야 해요"),
        Arguments.of(
            "{" + VALID_CARD + ",\"expectedFailCauses\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\"]}",
            "예상 실패 원인은 5개까지 적을 수 있어요"),
        Arguments.of(
            "{" + VALID_CARD + ",\"expectedFailCauses\":[\"" + "가".repeat(101) + "\"]}",
            "예상 실패 원인은 100자 이하로 적어 주세요"),
        Arguments.of(
            "{" + VALID_CARD + ",\"alert\":{\"method\":\"message\",\"minutesBefore\":121}}",
            "알림은 120분 전까지만 정할 수 있어요"),
        Arguments.of(
            "{" + VALID_CARD + ",\"alert\":{\"method\":\"message\"}}", "몇 분 전에 알릴지 보내 주세요"),
        Arguments.of("{" + VALID_CARD + ",\"planningMs\":-1}", "계획에 쓴 시간은 0 이상이어야 해요"),
        Arguments.of(
            "{" + VALID_CARD.replace("2026-10-12", "2026/10/12") + "}", "보낸 값을 다시 확인해 주세요"));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("wrongCreateRequests")
  @DisplayName("만들기 요청이 틀리면 400 이고, Service 는 부르지 않는다")
  void createRejected(String body, String message) throws Exception {
    expectRejected(send(post("/api/v1/cards"), body), message);
  }

  // ---- 바꾸기 ----

  @Test
  @DisplayName("바꾸기는 200 이고, 보낸 값만 Service 로 넘긴다 (빈 글자 · 빈 목록도 지우기로 통과)")
  void update() throws Exception {
    given(cardService.update(eq(7L), any())).willReturn(MATH);

    send(
            patch("/api/v1/cards/7"),
            "{\"repeatDays\":[],\"startTime\":\"\",\"goalId\":\"\",\"subjectId\":\"\"}")
        .andExpect(status().isOk());
    verify(cardService)
        .update(
            7L,
            new CardUpdateRequest(
                null, null, List.of(), "", null, null, "", "", null, null, null, null, null, null,
                null));
  }

  static Stream<Arguments> wrongUpdateRequests() {
    return Stream.of(
        Arguments.of("{\"title\":\"   \"}", "카드 제목은 1자 이상 100자 이하로 적어 주세요"),
        Arguments.of("{\"durationMinutes\":601}", "카드 길이는 600분(10시간) 이하여야 해요"),
        Arguments.of("{\"checklist\":[]}", "체크리스트는 1개 이상 20개까지 적을 수 있어요"),
        Arguments.of("{\"startTime\":\"24:00\"}", "시작 시각은 19:00 처럼 보내 주세요"),
        Arguments.of("{\"goalId\":\"9999999999999999999\"}", "목표 번호가 올바르지 않아요"),
        Arguments.of("{\"subjectId\":\"-3\"}", "과목 번호가 올바르지 않아요"));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("wrongUpdateRequests")
  @DisplayName("바꾸기 요청이 틀리면 400 이고, Service 는 부르지 않는다")
  void updateRejected(String body, String message) throws Exception {
    expectRejected(send(patch("/api/v1/cards/7"), body), message);
  }

  @Test
  @DisplayName("남의 카드를 바꾸면 Service 가 던진 404 가 그대로 나간다")
  void updateOthersCard() throws Exception {
    given(cardService.update(eq(8L), any()))
        .willThrow(new ApiException(ErrorCode.NOT_FOUND, "카드를 찾을 수 없어요"));

    send(patch("/api/v1/cards/8"), "{\"title\":\"수학\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.message").value("카드를 찾을 수 없어요"));
  }

  // ---- 지우기 ----

  @Test
  @DisplayName("지우기는 204 이고 본문이 없다")
  void deleteCard() throws Exception {
    mockMvc
        .perform(delete("/api/v1/cards/7"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    verify(cardService).delete(7L);
  }

  @Test
  @DisplayName("남의 카드를 지우면 404, 주소의 번호가 숫자가 아니면 400")
  void deleteErrors() throws Exception {
    willThrow(new ApiException(ErrorCode.NOT_FOUND, "카드를 찾을 수 없어요")).given(cardService).delete(8L);

    mockMvc.perform(delete("/api/v1/cards/8")).andExpect(status().isNotFound());
    mockMvc.perform(delete("/api/v1/cards/abc")).andExpect(status().isBadRequest());
  }
}
