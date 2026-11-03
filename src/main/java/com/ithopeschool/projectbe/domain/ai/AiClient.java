package com.ithopeschool.projectbe.domain.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ithopeschool.projectbe.domain.gpt.GptUsageGuard;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// 입력 칸마다 "AI 가 먼저 채워 두고 사람이 고르게" 하는 작은 AI 기능들이 같이 쓰는 GPT 호출 틀입니다. (이슈 #237)
// 체크리스트 추천(#236)처럼 기능이 늘어도 부르는 방법은 이 파일 하나입니다.
// 기능마다 다른 것은 "무엇을 묻고 어떤 모양으로 받을지"(AiQuestion) 뿐입니다.
//
// ask 한 번에 하는 일 (순서대로)
//   1. 꺼져 있으면(키·모델 이름이 없음) 빈 값. 내 컴퓨터(local)에서는 항상 여기서 멈춥니다.
//   2. 부를 때마다 gpt_usages 에 한 줄 남깁니다. (GptUsageGuard. 하루 한도는 2026-10-24 에 없앴습니다)
//   3. 칸 모양(JSON schema)을 같이 보내서 그 모양으로만 답하게 합니다. (Structured Outputs)
//   4. 10초 안에 답이 없거나, 에러로 답하거나, 모양이 다른 답이면 빈 값.
//
// 빈 값이면 FE 는 기본값(직접 쓰기 등)으로 대신합니다. 그래서 이 파일은 에러를 던지지 않습니다.
// 받은 값이 쓸 만한지(개수·글자 수·금지 표현)는 기능마다 달라서 부르는 쪽이 검사합니다.
//
// ⚠️ 학생이 쓴 글이 들어가므로, 부르는 쪽이 먼저 위기 표현(CrisisText)을 봅니다. 위기면 AI 대신 안전 브레이크입니다.
// ⚠️ 키, 보낸 글, 받은 답은 로그에 찍지 않습니다. 실패하면 실패한 종류(예외 이름)만 찍습니다.
//
// 키와 모델 이름은 코치(CoachAiClient)·말로 계획 세우기(PlanParseClient)와 같은 설정(app.ai)이고, 배포 서버에만 있습니다.
// 부르는 방법은 OpenAI 공식 문서의 Responses API 와 Structured Outputs 를 따랐습니다. (PlanParseClient 와 같음)
//   POST https://api.openai.com/v1/responses
//   { model, instructions, input, store, text: { format: { type: "json_schema", name, strict,
// schema } } }
@Component
public class AiClient {

  private static final Logger log = LoggerFactory.getLogger(AiClient.class);

  private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";

  // OpenAI 에 연결되기를 기다리는 최대 시간
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  // 답을 기다리는 최대 시간. 학생이 입력 칸 앞에서 기다리는 중이라 길게 잡지 않습니다.
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
  // 긴 답(긴 계획처럼 문단 여러 개)을 기다리는 최대 시간. 화면은 그동안 코드가 쓴 기본 문장을 먼저 보여 줍니다.
  private static final Duration LONG_READ_TIMEOUT = Duration.ofSeconds(25);
  // AI 자기 분석의 답을 기다리는 최대 시간. 분석은 더 큰 모델(app.ai.analysis-model)이라 느려서 28초까지 기다립니다.
  // (FE aiAnalysisApi.ts 는 30초까지 기다립니다. 2026-10-24)
  private static final Duration ANALYSIS_READ_TIMEOUT = Duration.ofSeconds(28);
  // 사진 채점의 답을 기다리는 최대 시간. 16문제가 빽빽한 시험지 한 장은 25초를 넘겨서 "지금은 채점할 수 없어요" 가 났습니다.
  // (2026-10-31 시연 준비 중) 그래서 사진 채점만 50초까지 기다립니다. (FE answerChecksApi.ts 는 95초까지 기다립니다)
  private static final Duration PHOTO_GRADING_READ_TIMEOUT = Duration.ofSeconds(50);

  // 받은 JSON 글자를 자바 값으로 바꾸는 도구
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final String apiKey;
  private final String model;
  // AI 자기 분석만 쓰는 모델 (askAnalysis). 따로 정하지 않았으면 위의 model 과 같습니다.
  private final String analysisModel;
  private final GptUsageGuard gptUsageGuard;
  private final RestClient restClient;
  // 긴 답용 (askLong). 기다리는 시간만 다르고 나머지는 restClient 와 같습니다.
  private final RestClient longRestClient;
  // 자기 분석용 (askAnalysis). 기다리는 시간만 다르고 나머지는 restClient 와 같습니다.
  private final RestClient analysisRestClient;
  // 사진 채점용 (askPhotoGrading). 기다리는 시간만 다르고 나머지는 restClient 와 같습니다.
  private final RestClient photoGradingRestClient;

  // Spring 이 쓰는 생성자입니다. 키와 모델 이름 중 하나라도 비어 있으면 꺼진 상태입니다.
  @Autowired
  public AiClient(
      @Value("${app.ai.openai-api-key:}") String apiKey,
      @Value("${app.ai.model:}") String model,
      @Value("${app.ai.analysis-model:}") String analysisModel,
      GptUsageGuard gptUsageGuard) {
    this(
        apiKey,
        model,
        analysisModel,
        gptUsageGuard,
        RestClient.builder().requestFactory(requestFactoryWithTimeouts(READ_TIMEOUT)),
        RestClient.builder().requestFactory(requestFactoryWithTimeouts(LONG_READ_TIMEOUT)),
        RestClient.builder().requestFactory(requestFactoryWithTimeouts(ANALYSIS_READ_TIMEOUT)),
        RestClient.builder()
            .requestFactory(requestFactoryWithTimeouts(PHOTO_GRADING_READ_TIMEOUT)));
  }

  // 테스트에서 가짜 OpenAI 를 끼워 넣을 때 쓰는 생성자입니다. (AiClientTest) 긴 답 · 분석 · 사진 채점도 같은 가짜 서버로 갑니다.
  // 분석 모델은 따로 주지 않아서 model 과 같습니다.
  AiClient(String apiKey, String model, GptUsageGuard gptUsageGuard, RestClient.Builder builder) {
    this(apiKey, model, null, gptUsageGuard, builder);
  }

  // 테스트에서 분석 모델을 따로 줄 때 쓰는 생성자입니다. (AiClientTest)
  AiClient(
      String apiKey,
      String model,
      String analysisModel,
      GptUsageGuard gptUsageGuard,
      RestClient.Builder builder) {
    this(apiKey, model, analysisModel, gptUsageGuard, builder, builder, builder, builder);
  }

  private AiClient(
      String apiKey,
      String model,
      String analysisModel,
      GptUsageGuard gptUsageGuard,
      RestClient.Builder builder,
      RestClient.Builder longBuilder,
      RestClient.Builder analysisBuilder,
      RestClient.Builder photoGradingBuilder) {
    this.apiKey = apiKey == null ? "" : apiKey.trim();
    this.model = model == null ? "" : model.trim();
    // 분석 모델을 따로 정하지 않았으면(비었으면) 글 모델을 씁니다.
    this.analysisModel =
        analysisModel == null || analysisModel.isBlank() ? this.model : analysisModel.trim();
    this.gptUsageGuard = gptUsageGuard;
    this.restClient = builder.baseUrl(OPENAI_BASE_URL).build();
    this.longRestClient = longBuilder.baseUrl(OPENAI_BASE_URL).build();
    this.analysisRestClient = analysisBuilder.baseUrl(OPENAI_BASE_URL).build();
    this.photoGradingRestClient = photoGradingBuilder.baseUrl(OPENAI_BASE_URL).build();
  }

  // GPT 를 부를 수 있는 서버인지 알려줍니다. 키와 모델 이름이 둘 다 있어야 true 입니다.
  public boolean isEnabled() {
    return !apiKey.isEmpty() && !model.isEmpty();
  }

  // 질문을 보내고, 칸 모양대로 받은 답을 answerType 으로 바꿔 돌려줍니다. 못 받으면 빈 값입니다.
  //   userId : 부르게 한 사람 (gpt_usages 에 이 사람으로 남습니다)
  //   kind   : 무슨 기능인지 (gpt_usages 에 이 종류로 남습니다)
  public <T> Optional<T> ask(
      Long userId, GptUsageKind kind, AiQuestion question, Class<T> answerType) {
    return send(restClient, model, userId, kind, question, question.input(), answerType);
  }

  // ask 와 같고, 답을 25초까지 기다립니다. 답이 긴 기능(긴 계획 LongPlanService · 확인 문제 StudyQuizService)에서 씁니다.
  public <T> Optional<T> askLong(
      Long userId, GptUsageKind kind, AiQuestion question, Class<T> answerType) {
    return send(longRestClient, model, userId, kind, question, question.input(), answerType);
  }

  // ask 와 같고, 분석 모델(app.ai.analysis-model)로 묻고 답을 28초까지 기다립니다. AI 자기 분석(SelfAnalysisService)에서만
  // 씁니다.
  // 분석은 "왜 그런지 · 다음에 뭘 해 볼지" 를 길게 쓰는 일이라, 2026-10-24 에 다른 작은 기능보다 좋은 모델로 올렸습니다.
  public <T> Optional<T> askAnalysis(
      Long userId, GptUsageKind kind, AiQuestion question, Class<T> answerType) {
    return send(
        analysisRestClient, analysisModel, userId, kind, question, question.input(), answerType);
  }

  // ask 와 같고, 질문 글과 함께 사진 한 장을 보냅니다. 사진을 읽느라 오래 걸려서 답을 25초까지 기다립니다.
  //   imageDataUrl : "data:image/jpeg;base64,..." 모양의 사진 (OpenAI 문서 Images and vision 의
  // input_image)
  // ⚠️ 사진은 여기서 OpenAI 로 보내고 끝입니다. 저장하거나 로그에 찍지 않습니다. (store=false 로 OpenAI 에도 남기지 않음)
  public <T> Optional<T> askWithImage(
      Long userId,
      GptUsageKind kind,
      AiQuestion question,
      String imageDataUrl,
      Class<T> answerType) {
    return send(
        longRestClient,
        model,
        userId,
        kind,
        question,
        imageInput(question, imageDataUrl),
        answerType);
  }

  // askWithImage 와 같고, 답을 50초까지 기다립니다. 사진 채점(AnswerCheckService)에서만 씁니다.
  // 문제 16개쯤 되는 시험지 한 장을 읽으면 25초를 넘길 때가 있어서 따로 두었습니다. (PHOTO_GRADING_READ_TIMEOUT)
  public <T> Optional<T> askPhotoGrading(
      Long userId,
      GptUsageKind kind,
      AiQuestion question,
      String imageDataUrl,
      Class<T> answerType) {
    return send(
        photoGradingRestClient,
        model,
        userId,
        kind,
        question,
        imageInput(question, imageDataUrl),
        answerType);
  }

  // 질문 글과 사진 한 장을 묶은 입력입니다. (askWithImage · askPhotoGrading)
  //   보내는 모양: [ { "role": "user", "content": [ { "type": "input_text", "text": "..." },
  //                   { "type": "input_image", "image_url": "data:...", "detail": "auto" } ] } ]
  private static List<Map<String, Object>> imageInput(AiQuestion question, String imageDataUrl) {
    return List.of(
        Map.of(
            "role",
            "user",
            "content",
            List.of(
                Map.of("type", "input_text", "text", question.input()),
                Map.of("type", "input_image", "image_url", imageDataUrl, "detail", "auto"))));
  }

  // input 은 글(String)이거나, 사진이 섞인 메시지 목록(askWithImage · askPhotoGrading)입니다.
  // modelName 은 보낼 모델 이름입니다. (보통 model, 자기 분석만 analysisModel)
  private <T> Optional<T> send(
      RestClient client,
      String modelName,
      Long userId,
      GptUsageKind kind,
      AiQuestion question,
      Object input,
      Class<T> answerType) {
    // 1) 꺼져 있음. 부르지 않았으니 기록도 남기지 않습니다.
    if (!isEnabled()) {
      return Optional.empty();
    }
    // 2) 부른 것을 기록
    gptUsageGuard.recordText(userId, kind);

    try {
      // store=false: OpenAI 쪽에 이 요청과 답을 보관하지 말라는 뜻입니다. (미성년자의 글이라 남기지 않습니다)
      OpenAiRequest request =
          new OpenAiRequest(
              modelName,
              question.instructions(),
              input,
              false,
              new TextOption(new Format("json_schema", question.name(), true, question.schema())));
      OpenAiResponse response =
          client
              .post()
              .uri("/responses")
              .header("Authorization", "Bearer " + apiKey)
              .body(request)
              .retrieve()
              .body(OpenAiResponse.class);

      Optional<String> text = textOf(response);
      if (text.isEmpty()) {
        return Optional.empty();
      }
      return Optional.ofNullable(JSON.readValue(text.get(), answerType));
    } catch (RestClientException e) {
      // 시간 초과, 연결 실패, 4xx·5xx 응답이 모두 여기로 옵니다. 내용이 담겨 있을 수 있어서 종류만 찍습니다.
      log.warn("[AI] {} 호출에 실패했어요: {}", question.name(), e.getClass().getSimpleName());
      return Optional.empty();
    } catch (JacksonException e) {
      // 약속한 JSON 모양이 아닌 답입니다. 받은 글은 찍지 않습니다.
      log.warn("[AI] {} 답의 모양이 달라요: {}", question.name(), e.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  // 응답에서 글만 꺼냅니다. type 이 "message" 인 것 안의 type 이 "output_text" 인 조각만 모읍니다. (PlanParseClient 와 같음)
  private static Optional<String> textOf(OpenAiResponse response) {
    if (response == null || response.output() == null) {
      return Optional.empty();
    }
    StringBuilder text = new StringBuilder();
    for (OpenAiResponse.Item item : response.output()) {
      if (!"message".equals(item.type()) || item.content() == null) {
        continue;
      }
      for (OpenAiResponse.Content content : item.content()) {
        if ("output_text".equals(content.type()) && content.text() != null) {
          text.append(content.text());
        }
      }
    }
    String result = text.toString().trim();
    return result.isEmpty() ? Optional.empty() : Optional.of(result);
  }

  // 기다리는 시간을 정한 연결 도구를 만듭니다. 정하지 않으면 GPT 가 멈췄을 때 끝없이 기다립니다.
  private static JdkClientHttpRequestFactory requestFactoryWithTimeouts(Duration readTimeout) {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(readTimeout);
    return factory;
  }

  // ---- OpenAI 와 주고받는 JSON 모양 (이 파일 안에서만 씁니다) ----

  // 보내는 모양:
  //   { "model": "...", "instructions": "...", "input": "...", "store": false,
  //     "text": { "format": { "type": "json_schema", "name": "checklist", "strict": true,
  //               "schema": {…} } } }
  // input 은 글 하나이거나, 사진을 보낼 때는 메시지 목록입니다. (askWithImage)
  record OpenAiRequest(
      String model, String instructions, Object input, boolean store, TextOption text) {}

  record TextOption(Format format) {}

  record Format(String type, String name, boolean strict, Map<String, Object> schema) {}

  // 받는 모양에서 우리가 읽는 부분만 적었습니다. 나머지 필드는 무시합니다. (ignoreUnknown)
  @JsonIgnoreProperties(ignoreUnknown = true)
  record OpenAiResponse(List<Item> output) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String type, List<Content> content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(String type, String text) {}
  }
}
