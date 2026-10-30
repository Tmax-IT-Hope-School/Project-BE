package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ithopeschool.projectbe.domain.gpt.GptUsageGuard;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// GPT 를 진짜로 부르지 않는 테스트입니다. 가짜 OpenAI 서버를 끼워 넣고, GPT 호출 기록(GptUsageGuard)도 가짜로 바꿉니다.
// "언제 부르지 않는지(꺼짐·한도)" 와 "무엇을 보내고, 받은 JSON 을 어떻게 읽는지" 를 봅니다.
// ⚠️ 진짜 GPT 가 이 모양으로 답하는지는 이 테스트로 알 수 없습니다. 배포 서버에서 직접 불러 봐야 합니다.
class AiClientTest {

  private static final Long USER_ID = 7L;
  private static final String KEY = "test-key-123";
  private static final String MODEL = "test-model";
  private static final String URL = "https://api.openai.com/v1/responses";

  // 테스트용 질문: 글 목록 하나를 받습니다.
  private static final AiQuestion QUESTION =
      new AiQuestion(
          "checklist",
          "지침",
          "카드 제목: 수학 문제집",
          AiSchema.object(Map.of("items", AiSchema.stringList())));

  // 테스트용 답 모양
  record Answer(List<String> items) {}

  private final GptUsageGuard gptUsageGuard = mock(GptUsageGuard.class);
  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer openAi = MockRestServiceServer.bindTo(builder).build();

  private AiClient clientWith(String key, String model) {
    return new AiClient(key, model, gptUsageGuard, builder);
  }

  // OpenAI 가 글 하나를 돌려주는 응답. 글 안의 JSON 은 따옴표를 \" 로 바꿔 넣습니다.
  private static String answerOf(String text) {
    String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    return """
        { "output": [ { "type": "message",
            "content": [ { "type": "output_text", "text": "%s" } ] } ] }
        """
        .formatted(escaped);
  }

  @Test
  @DisplayName("키와 모델 이름이 둘 다 있어야 켜지고, 꺼져 있으면 OpenAI 를 부르지 않고 기록도 남기지 않는다")
  void disabledDoesNotCall() {
    openAi.expect(never(), anything());

    assertThat(clientWith(KEY, MODEL).isEnabled()).isTrue();
    assertThat(clientWith("", MODEL).isEnabled()).isFalse();
    assertThat(clientWith(KEY, " ").isEnabled()).isFalse();
    assertThat(clientWith("", "").ask(USER_ID, GptUsageKind.CHECKLIST, QUESTION, Answer.class))
        .isEmpty();

    verifyNoInteractions(gptUsageGuard);
    openAi.verify();
  }

  @Test
  @DisplayName("질문의 지침·입력·칸 모양을 보내고(보관 안 함), 한도를 그 종류로 쓰고, 받은 JSON 을 답 모양으로 읽는다")
  void sendsTheQuestionAndReadsTheAnswer() {
    openAi
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer " + KEY))
        .andExpect(jsonPath("$.model").value(MODEL))
        .andExpect(jsonPath("$.instructions").value("지침"))
        .andExpect(jsonPath("$.input").value("카드 제목: 수학 문제집"))
        .andExpect(jsonPath("$.store").value(false))
        .andExpect(jsonPath("$.text.format.type").value("json_schema"))
        .andExpect(jsonPath("$.text.format.name").value("checklist"))
        .andExpect(jsonPath("$.text.format.strict").value(true))
        .andExpect(jsonPath("$.text.format.schema.required[0]").value("items"))
        .andExpect(jsonPath("$.text.format.schema.additionalProperties").value(false))
        .andRespond(
            withSuccess(
                answerOf("{\"items\":[\"42쪽 펴기\",\"5문제 풀기\"]}"), MediaType.APPLICATION_JSON));

    Answer answer =
        clientWith(KEY, MODEL)
            .ask(USER_ID, GptUsageKind.CHECKLIST, QUESTION, Answer.class)
            .orElseThrow();

    assertThat(answer.items()).containsExactly("42쪽 펴기", "5문제 풀기");
    verify(gptUsageGuard).recordText(USER_ID, GptUsageKind.CHECKLIST);
    openAi.verify();
  }

  @Test
  @DisplayName("사진과 함께 물으면 input 을 메시지 목록으로 보낸다: 질문 글(input_text) + 사진(input_image, data URL)")
  void sendsTheImageWithTheQuestion() {
    String image = "data:image/jpeg;base64,AQID";
    openAi
        .expect(requestTo(URL))
        .andExpect(jsonPath("$.instructions").value("지침"))
        .andExpect(jsonPath("$.store").value(false))
        .andExpect(jsonPath("$.input[0].role").value("user"))
        .andExpect(jsonPath("$.input[0].content[0].type").value("input_text"))
        .andExpect(jsonPath("$.input[0].content[0].text").value("카드 제목: 수학 문제집"))
        .andExpect(jsonPath("$.input[0].content[1].type").value("input_image"))
        .andExpect(jsonPath("$.input[0].content[1].image_url").value(image))
        .andExpect(jsonPath("$.input[0].content[1].detail").value("auto"))
        .andExpect(jsonPath("$.text.format.name").value("checklist"))
        .andRespond(withSuccess(answerOf("{\"items\":[\"52쪽 읽기\"]}"), MediaType.APPLICATION_JSON));

    Answer answer =
        clientWith(KEY, MODEL)
            .askWithImage(USER_ID, GptUsageKind.CHECKLIST, QUESTION, image, Answer.class)
            .orElseThrow();

    assertThat(answer.items()).containsExactly("52쪽 읽기");
    verify(gptUsageGuard).recordText(USER_ID, GptUsageKind.CHECKLIST);
    openAi.verify();
  }

  @Test
  @DisplayName("사진 채점(askPhotoGrading)도 사진과 함께 같은 모양으로 보내고, 글 모델로 묻는다")
  void photoGradingSendsTheImageLikeAskWithImage() {
    String image = "data:image/jpeg;base64,AQID";
    openAi
        .expect(requestTo(URL))
        .andExpect(jsonPath("$.model").value(MODEL))
        .andExpect(jsonPath("$.input[0].content[0].type").value("input_text"))
        .andExpect(jsonPath("$.input[0].content[1].type").value("input_image"))
        .andExpect(jsonPath("$.input[0].content[1].image_url").value(image))
        .andRespond(withSuccess(answerOf("{\"items\":[\"1번\"]}"), MediaType.APPLICATION_JSON));

    Answer answer =
        clientWith(KEY, MODEL)
            .askPhotoGrading(USER_ID, GptUsageKind.ASSIST, QUESTION, image, Answer.class)
            .orElseThrow();

    assertThat(answer.items()).containsExactly("1번");
    verify(gptUsageGuard).recordText(USER_ID, GptUsageKind.ASSIST);
    openAi.verify();
  }

  @Test
  @DisplayName("답이 JSON 이 아니거나 모양이 다르면 빈 값이다")
  void wrongShapeMeansEmpty() {
    openAi
        .expect(requestTo(URL))
        .andRespond(withSuccess(answerOf("체크리스트를 만들었어요!"), MediaType.APPLICATION_JSON));
    openAi
        .expect(requestTo(URL))
        .andRespond(withSuccess(answerOf("{\"items\":{\"a\":1}}"), MediaType.APPLICATION_JSON));

    AiClient client = clientWith(KEY, MODEL);
    assertThat(client.ask(USER_ID, GptUsageKind.CHECKLIST, QUESTION, Answer.class)).isEmpty();
    assertThat(client.ask(USER_ID, GptUsageKind.CHECKLIST, QUESTION, Answer.class)).isEmpty();
  }

  @Test
  @DisplayName("답에 글이 없으면 빈 값이다")
  void noTextMeansEmpty() {
    openAi
        .expect(requestTo(URL))
        .andRespond(withSuccess("{ \"output\": [] }", MediaType.APPLICATION_JSON));

    assertThat(clientWith(KEY, MODEL).ask(USER_ID, GptUsageKind.CHECKLIST, QUESTION, Answer.class))
        .isEmpty();
  }

  @Test
  @DisplayName("OpenAI 가 에러로 답하거나 시간이 넘으면 빈 값이다 (에러를 던지지 않는다)")
  void errorOrTimeoutMeansEmpty() {
    openAi.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
    openAi.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException()));

    AiClient client = clientWith(KEY, MODEL);
    assertThat(client.ask(USER_ID, GptUsageKind.CHECKLIST, QUESTION, Answer.class)).isEmpty();
    assertThat(client.ask(USER_ID, GptUsageKind.CHECKLIST, QUESTION, Answer.class)).isEmpty();
  }

  @Test
  @DisplayName("askLong 도 ask 와 같은 모양으로 보내고 읽는다 (기다리는 시간만 다르다)")
  void askLongReadsTheAnswer() {
    openAi
        .expect(requestTo(URL))
        .andExpect(jsonPath("$.text.format.name").value("checklist"))
        .andRespond(withSuccess(answerOf("{\"items\":[\"채점하기\"]}"), MediaType.APPLICATION_JSON));

    assertThat(
            clientWith(KEY, MODEL).askLong(USER_ID, GptUsageKind.CHECKLIST, QUESTION, Answer.class))
        .contains(new Answer(List.of("채점하기")));
    openAi.verify();
  }

  @Test
  @DisplayName("askAnalysis 는 분석 모델 이름으로 보내고, 다른 질문은 글 모델 그대로다 (2026-10-24)")
  void askAnalysisUsesTheAnalysisModel() {
    openAi
        .expect(requestTo(URL))
        .andExpect(jsonPath("$.model").value("analysis-model"))
        .andRespond(withSuccess(answerOf("{\"items\":[\"분석\"]}"), MediaType.APPLICATION_JSON));
    openAi
        .expect(requestTo(URL))
        .andExpect(jsonPath("$.model").value(MODEL))
        .andRespond(withSuccess(answerOf("{\"items\":[\"보통\"]}"), MediaType.APPLICATION_JSON));

    AiClient client = new AiClient(KEY, MODEL, "analysis-model", gptUsageGuard, builder);

    assertThat(client.askAnalysis(USER_ID, GptUsageKind.ASSIST, QUESTION, Answer.class))
        .contains(new Answer(List.of("분석")));
    assertThat(client.ask(USER_ID, GptUsageKind.ASSIST, QUESTION, Answer.class))
        .contains(new Answer(List.of("보통")));
    openAi.verify();
  }

  @Test
  @DisplayName("분석 모델을 따로 정하지 않았으면(빈 값) askAnalysis 도 글 모델로 보낸다")
  void askAnalysisFallsBackToTheTextModel() {
    openAi
        .expect(requestTo(URL))
        .andExpect(jsonPath("$.model").value(MODEL))
        .andRespond(withSuccess(answerOf("{\"items\":[\"분석\"]}"), MediaType.APPLICATION_JSON));

    AiClient client = new AiClient(KEY, MODEL, " ", gptUsageGuard, builder);

    assertThat(client.askAnalysis(USER_ID, GptUsageKind.ASSIST, QUESTION, Answer.class))
        .contains(new Answer(List.of("분석")));
    openAi.verify();
  }
}
