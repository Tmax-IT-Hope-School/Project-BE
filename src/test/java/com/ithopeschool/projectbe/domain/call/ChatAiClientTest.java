package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ithopeschool.projectbe.domain.call.dto.CallFinishRequest.Line;
import com.ithopeschool.projectbe.domain.call.dto.ChatReplyResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// GPT 를 진짜로 부르지 않는 테스트입니다. 가짜 OpenAI 서버(MockRestServiceServer)를 끼워 넣습니다. (이슈 #238 "가짜 OpenAI 서버
// 테스트")
// ⚠️ 진짜 GPT 와 맞는지는 이 테스트로 알 수 없습니다. 배포 서버에서 직접 불러 봐야 합니다.
@ExtendWith(OutputCaptureExtension.class)
class ChatAiClientTest {

  // 테스트용 가짜 키입니다. 진짜 키처럼 보이지 않게 적었습니다.
  private static final String KEY = "test-key-123";
  private static final String MODEL = "test-model";
  private static final String URL = "https://api.openai.com/v1/responses";

  private final RestClient.Builder builder = RestClient.builder();
  // bindTo: 이 builder 로 만든 RestClient 의 요청이 진짜 인터넷 대신 가짜 서버로 갑니다.
  private final MockRestServiceServer openAi = MockRestServiceServer.bindTo(builder).build();

  private ChatAiClient clientWith(String key, String model) {
    return new ChatAiClient(key, model, builder);
  }

  // OpenAI 가 글 하나를 돌려줄 때의 응답 모양 (CoachAiClientTest 와 같음)
  private static String answerOf(String text) {
    return """
        { "output": [
          { "type": "reasoning", "summary": [] },
          { "type": "message", "role": "assistant",
            "content": [ { "type": "output_text", "text": "%s", "annotations": [] } ] }
        ] }
        """
        .formatted(text);
  }

  private static final List<Line> TALK =
      List.of(new Line("ai", "어제 어땠어?"), new Line("me", "폰 보다가 늦게 시작했어"));

  @Test
  @DisplayName("키와 모델 이름이 둘 다 있어야 켜지고, 꺼져 있으면 OpenAI 를 부르지 않는다")
  void doesNotCallWhenDisabled() {
    openAi.expect(never(), anything());

    assertThat(clientWith("", MODEL).isEnabled()).isFalse();
    assertThat(clientWith(KEY, " ").isEnabled()).isFalse();
    assertThat(clientWith("", "").reply("지침", TALK)).isEmpty();

    openAi.verify();
  }

  @Test
  @DisplayName("대본을 instructions 로, 대화를 '코치: / 학생:' 줄로 보내고, 답에서 글만 꺼낸다 (store=false)")
  void sendsTheScriptAndTheTalk() {
    openAi
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer " + KEY))
        .andExpect(jsonPath("$.model").value(MODEL))
        .andExpect(jsonPath("$.instructions").value("채팅 대본"))
        .andExpect(
            jsonPath("$.input")
                .value("코치: 어제 어땠어?\n학생: 폰 보다가 늦게 시작했어\n\n(위 대화에 이어서 코치로서 다음 말을 해.)"))
        .andExpect(jsonPath("$.store").value(false))
        .andRespond(withSuccess(answerOf("그때 폰은 어디 있었어?"), MediaType.APPLICATION_JSON));

    assertThat(clientWith(KEY, MODEL).reply("채팅 대본", TALK)).contains("그때 폰은 어디 있었어?");
    openAi.verify();
  }

  @Test
  @DisplayName("채팅을 막 열었으면(대화 없음) 첫 인사를 부탁한다")
  void asksForTheFirstGreeting() {
    assertThat(ChatAiClient.inputOf(List.of())).isEqualTo("(학생이 방금 채팅을 열었어. [통화 순서] 1번부터 시작해.)");
  }

  @Test
  @DisplayName("OpenAI 가 실패하면 빈 값이고, 키·대화·답은 로그에 남지 않는다")
  void emptyWhenOpenAiFailsAndNothingIsLogged(CapturedOutput output) {
    openAi
        .expect(requestTo(URL))
        .andRespond(
            withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\":\"OpenAI 가 돌려준 내용\"}")
                .contentType(MediaType.APPLICATION_JSON));

    assertThat(clientWith(KEY, MODEL).reply("채팅 대본", TALK)).isEmpty();
    assertThat(output)
        .doesNotContain(KEY)
        .doesNotContain("폰 보다가 늦게 시작했어")
        .doesNotContain("OpenAI 가 돌려준 내용");
  }

  @Test
  @DisplayName("답에 글이 없으면 빈 값이다")
  void emptyWhenThereIsNoText() {
    openAi
        .expect(requestTo(URL))
        .andRespond(withSuccess("{ \"output\": [] }", MediaType.APPLICATION_JSON));

    assertThat(clientWith(KEY, MODEL).reply("채팅 대본", TALK)).isEmpty();
  }

  @Test
  @DisplayName("turn 은 대본 끝에 [답 모양] 을 붙이고 json_schema 로 불러, 받은 JSON 을 말 · 빠른 답 · 행동으로 나눈다")
  void turnAsksForJsonAndParsesIt() {
    ChatAiClient client = new ChatAiClient(KEY, MODEL, builder);
    openAi
        .expect(requestTo(URL))
        .andExpect(jsonPath("$.text.format.type").value("json_schema"))
        .andExpect(jsonPath("$.text.format.strict").value(true))
        .andExpect(jsonPath("$.store").value(false))
        .andRespond(
            withSuccess(
                """
                {"output":[{"type":"message","content":[{"type":"output_text","text":
                "{\\"reply\\":\\"10분만 해 볼까?\\",\\"quickReplies\\":[\\"좋아\\",\\"오늘은 쉴래\\"],\\"action\\":{\\"type\\":\\"shrink_today\\",\\"minutes\\":10,\\"title\\":null}}"}]}]}
                """,
                MediaType.APPLICATION_JSON));

    Optional<ChatAiClient.ChatTurn> turn = client.turn("채팅 대본", List.of());

    assertThat(turn)
        .contains(
            new ChatAiClient.ChatTurn(
                "10분만 해 볼까?",
                List.of("좋아", "오늘은 쉴래"),
                new ChatReplyResponse.Action("shrink_today", 10, null)));
    openAi.verify();
  }

  @Test
  @DisplayName("긴 계획 버튼(make_plan)은 마감 날(date)까지 읽는다")
  void parseTurnReadsPlanDate() {
    String text =
        "{\"reply\":\"계획 짜 볼까?\",\"quickReplies\":[],\"action\":{\"type\":\"make_plan\","
            + "\"minutes\":null,\"title\":\"중간고사 미적분\",\"date\":\"2026-11-02\"}}";

    assertThat(ChatAiClient.parseTurn(text).action())
        .isEqualTo(new ChatReplyResponse.Action("make_plan", null, "중간고사 미적분", "2026-11-02"));
  }

  @Test
  @DisplayName("계획 방(turnForPlanner)은 대본 끝에 계획 방의 답 모양을 붙인다: 다섯 문장 · 짧은 목록 · day_plan (#431)")
  void plannerTurnUsesPlannerRules() {
    ChatAiClient client = clientWith(KEY, MODEL);
    openAi
        .expect(requestTo(URL))
        .andExpect(jsonPath("$.instructions").value("계획 대본" + ChatAiClient.PLANNER_TURN_RULES))
        .andExpect(jsonPath("$.text.format.type").value("json_schema"))
        .andExpect(jsonPath("$.store").value(false))
        .andRespond(
            withSuccess(
                answerOf(
                    "{\\\"reply\\\":\\\"이렇게 짜 볼게\\\",\\\"quickReplies\\\":[],\\\"action\\\":"
                        + "{\\\"type\\\":\\\"day_plan\\\",\\\"minutes\\\":120,\\\"title\\\":null,"
                        + "\\\"date\\\":null,\\\"startTime\\\":\\\"21:00\\\"},\\\"tags\\\":[]}"),
                MediaType.APPLICATION_JSON));

    Optional<ChatAiClient.ChatTurn> turn = client.turnForPlanner("계획 대본", TALK, null);

    assertThat(turn.map(ChatAiClient.ChatTurn::action))
        .contains(new ChatReplyResponse.Action("day_plan", 120, null, null, "21:00"));
    openAi.verify();
  }

  @Test
  @DisplayName("계획 방의 답 모양은 두세 문장 제한이 없고 '오늘 계획 짜 줘' 칩을 넣지 않게 한다. 다른 방은 그대로")
  void plannerRulesDifferFromTheOthers() {
    assertThat(ChatAiClient.PLANNER_TURN_RULES)
        .contains("다섯 문장까지", "짧은 목록 3~5줄", "질문은 한 답에 하나까지만")
        .contains("day_plan", "startTime 은 시작 시각(HH:mm", "안 정한 것은 null")
        .contains("'오늘 계획 짜 줘' 는 앱이 늘 따로 보여 주니 넣지 마")
        .doesNotContain("두세 문장");
    assertThat(ChatAiClient.TURN_RULES)
        .contains("reply: 학생에게 할 말. 두세 문장.")
        .doesNotContain("day_plan");
  }

  @Test
  @DisplayName("하루 계획 버튼(day_plan)은 총 분 · 먼저 할 것 · 시작 시각(startTime)까지 읽는다 (#431)")
  void parseTurnReadsDayPlan() {
    String text =
        "{\"reply\":\"이렇게 짜 볼게\",\"quickReplies\":[],\"action\":{\"type\":\"day_plan\","
            + "\"minutes\":120,\"title\":\"수학 먼저\",\"date\":null,\"startTime\":\"21:00\"},"
            + "\"tags\":[]}";

    assertThat(ChatAiClient.parseTurn(text).action())
        .isEqualTo(new ChatReplyResponse.Action("day_plan", 120, "수학 먼저", null, "21:00"));
  }

  @Test
  @DisplayName("답 모양의 action 은 day_plan 을 고를 수 있고, startTime 칸도 꼭 채우게 한다 (strict)")
  void schemaHasDayPlanAndStartTime() {
    Map<?, ?> properties = (Map<?, ?>) ChatAiClient.TURN_SCHEMA.get("properties");
    Map<?, ?> action = (Map<?, ?>) properties.get("action");
    Map<?, ?> actionProperties = (Map<?, ?>) action.get("properties");
    Map<?, ?> type = (Map<?, ?>) actionProperties.get("type");

    assertThat(action.get("required"))
        .isEqualTo(List.of("type", "minutes", "title", "date", "startTime"));
    assertThat(type.get("enum"))
        .isEqualTo(
            List.of(
                "none",
                "start_timer",
                "shrink_today",
                "create_card",
                "make_plan",
                "start_quiz",
                "day_plan"));
    assertThat(actionProperties.get("startTime"))
        .isEqualTo(Map.of("type", List.of("string", "null")));
  }

  @Test
  @DisplayName("받은 글이 JSON 이 아니면 그 글을 그대로 말로 쓰고 버튼은 없다")
  void parseTurnFallsBackToText() {
    assertThat(ChatAiClient.parseTurn("그냥 글이야"))
        .isEqualTo(ChatAiClient.ChatTurn.textOnly("그냥 글이야"));
    assertThat(ChatAiClient.parseTurn("{\"reply\":\"\"}"))
        .isEqualTo(ChatAiClient.ChatTurn.textOnly("{\"reply\":\"\"}"));
  }

  @Test
  @DisplayName("주제가 있는 채팅의 이유 값(tags)을 읽고, 없으면 빈 목록이다")
  void parseTurnReadsTags() {
    String withTags =
        "{\"reply\":\"어땠어?\",\"quickReplies\":[],\"action\":null,\"tags\":[\"phone\",\"tired\"]}";
    String withoutTags = "{\"reply\":\"어땠어?\",\"quickReplies\":[],\"action\":null}";

    assertThat(ChatAiClient.parseTurn(withTags).tags()).containsExactly("phone", "tired");
    assertThat(ChatAiClient.parseTurn(withoutTags).tags()).isEmpty();
  }

  @Test
  @DisplayName("답 모양(TURN_SCHEMA)은 tags 를 꼭 채우게 한다 (strict 모드는 모든 칸이 required 여야 함)")
  void schemaRequiresTags() {
    assertThat(ChatAiClient.TURN_SCHEMA.get("required"))
        .isEqualTo(List.of("reply", "quickReplies", "action", "tags"));
  }

  @Test
  @DisplayName("사진이 붙은 말은 입력을 메시지 목록으로 바꿔 글과 사진(input_image)을 같이 보낸다")
  void turnWithImageSendsImage() {
    String image = "data:image/jpeg;base64,AAAA";
    ChatAiClient client = clientWith(KEY, MODEL);
    openAi
        .expect(requestTo(URL))
        .andExpect(jsonPath("$.store").value(false))
        .andExpect(jsonPath("$.input[0].role").value("user"))
        .andExpect(jsonPath("$.input[0].content[0].type").value("input_text"))
        .andExpect(jsonPath("$.input[0].content[0].text").value(containsString("학생: 이 문제 봐 줘")))
        .andExpect(jsonPath("$.input[0].content[1].type").value("input_image"))
        .andExpect(jsonPath("$.input[0].content[1].image_url").value(image))
        .andRespond(
            withSuccess(
                answerOf(
                    "{\\\"reply\\\":\\\"어디까지 풀었어?\\\",\\\"quickReplies\\\":[],\\\"action\\\":null,\\\"tags\\\":[]}"),
                MediaType.APPLICATION_JSON));

    Optional<ChatAiClient.ChatTurn> turn =
        client.turnWithImage("대본", List.of(new Line("me", "이 문제 봐 줘")), image);

    assertThat(turn.map(ChatAiClient.ChatTurn::reply)).contains("어디까지 풀었어?");
    openAi.verify();
  }

  @Test
  @DisplayName("사진이 없는 말은 지금처럼 입력을 글 하나로 보낸다")
  void turnWithoutImageSendsText() {
    ChatAiClient client = clientWith(KEY, MODEL);
    openAi
        .expect(requestTo(URL))
        .andExpect(jsonPath("$.input").isString())
        .andRespond(
            withSuccess(
                answerOf(
                    "{\\\"reply\\\":\\\"응\\\",\\\"quickReplies\\\":[],\\\"action\\\":null,\\\"tags\\\":[]}"),
                MediaType.APPLICATION_JSON));

    assertThat(client.turn("대본", List.of(new Line("me", "안녕")))).isPresent();
    openAi.verify();
  }

  @Test
  @DisplayName("AI 가 꺼져 있으면 사진이 있어도 부르지 않는다")
  void turnWithImageWhenDisabled() {
    openAi.expect(never(), anything());

    assertThat(clientWith("", MODEL).turnWithImage("대본", List.of(), "data:image/jpeg;base64,AAAA"))
        .isEmpty();
    openAi.verify();
  }

  // ---- 과목 쌤의 웹 검색 (2026-10-18) ----

  // 검색한 답: output 에 web_search_call 항목이 있고, 글에 url_citation 이 붙어 있습니다. (OpenAI 문서 "Web search")
  private static final String SEARCHED_ANSWER =
      """
      {"output":[
        {"type":"web_search_call","id":"ws_1","status":"completed","action":{"type":"search","queries":["함수의 극한"]}},
        {"type":"message","content":[{"type":"output_text",
          "text":"{\\"reply\\":\\"위키백과에서 찾아봤어.\\",\\"quickReplies\\":[],\\"action\\":{\\"type\\":\\"none\\",\\"minutes\\":null,\\"title\\":null,\\"date\\":null},\\"tags\\":[]}",
          "annotations":[{"type":"url_citation","start_index":0,"end_index":4,
                          "url":"https://ko.wikipedia.org/wiki/극한","title":"극한 - 위키백과"}]}]}
      ]}
      """;

  private static final String PLAIN_ANSWER =
      """
      {"output":[{"type":"message","content":[{"type":"output_text",
        "text":"{\\"reply\\":\\"어디까지 알아?\\",\\"quickReplies\\":[],\\"action\\":{\\"type\\":\\"none\\",\\"minutes\\":null,\\"title\\":null,\\"date\\":null},\\"tags\\":[]}",
        "annotations":[]}]}]}
      """;

  @Test
  @DisplayName("과목 쌤에 검색을 켜면 web_search 도구(허락한 사이트만)를 붙이고, 출처와 '검색했음' 을 꺼낸다")
  void tutorWithWebSearch() {
    openAi
        .expect(requestTo(URL))
        .andExpect(jsonPath("$.tools[0].type").value("web_search"))
        .andExpect(jsonPath("$.tools[0].filters.allowed_domains[0]").value("ncic.re.kr"))
        .andExpect(jsonPath("$.text.format.type").value("json_schema"))
        .andExpect(jsonPath("$.store").value(false))
        .andRespond(withSuccess(SEARCHED_ANSWER, MediaType.APPLICATION_JSON));

    Optional<ChatAiClient.ChatTurn> turn =
        clientWith(KEY, MODEL).turnForTutor("과목 쌤 대본", TALK, null, true);

    assertThat(turn).isPresent();
    assertThat(turn.get().reply()).isEqualTo("위키백과에서 찾아봤어.");
    assertThat(turn.get().searched()).isTrue();
    assertThat(turn.get().sources())
        .containsExactly(
            new ChatReplyResponse.Source("극한 - 위키백과", "https://ko.wikipedia.org/wiki/극한"));
    openAi.verify();
  }

  @Test
  @DisplayName("검색을 끄면 tools 칸을 아예 보내지 않고, 출처는 빈 목록 · 검색 안 함이다")
  void tutorWithoutWebSearch() {
    openAi
        .expect(requestTo(URL))
        .andExpect(jsonPath("$.tools").doesNotExist())
        .andRespond(withSuccess(PLAIN_ANSWER, MediaType.APPLICATION_JSON));

    Optional<ChatAiClient.ChatTurn> turn =
        clientWith(KEY, MODEL).turnForTutor("과목 쌤 대본", TALK, null, false);

    assertThat(turn).isPresent();
    assertThat(turn.get().reply()).isEqualTo("어디까지 알아?");
    assertThat(turn.get().searched()).isFalse();
    assertThat(turn.get().sources()).isEmpty();
    openAi.verify();
  }

  @Test
  @DisplayName("검색 도구를 붙인 요청이 4xx 로 거절되면 도구 없이 한 번 더 보낸다 (키 · 대화는 로그에 없음)")
  void tutorRetriesWithoutToolsOn4xx(CapturedOutput output) {
    openAi
        .expect(requestTo(URL))
        .andExpect(jsonPath("$.tools[0].type").value("web_search"))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .body("{\"error\":\"tool not supported\"}")
                .contentType(MediaType.APPLICATION_JSON));
    openAi
        .expect(requestTo(URL))
        .andExpect(jsonPath("$.tools").doesNotExist())
        .andRespond(withSuccess(PLAIN_ANSWER, MediaType.APPLICATION_JSON));

    Optional<ChatAiClient.ChatTurn> turn =
        clientWith(KEY, MODEL).turnForTutor("과목 쌤 대본", TALK, null, true);

    assertThat(turn).isPresent();
    assertThat(turn.get().searched()).isFalse();
    assertThat(output).contains("도구 없이 다시").doesNotContain(KEY).doesNotContain("폰 보다가");
    openAi.verify();
  }
}
