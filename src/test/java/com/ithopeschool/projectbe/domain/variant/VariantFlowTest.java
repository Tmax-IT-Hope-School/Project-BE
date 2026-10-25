package com.ithopeschool.projectbe.domain.variant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.variant.generate.ClassificationDraft;
import com.ithopeschool.projectbe.domain.variant.generate.FakeVariantAi;
import com.ithopeschool.projectbe.domain.variant.generate.VariantDrafts;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 변형 문제 기능을 서버 전체로 켜서 이어 붙인 흐름을 확인합니다. (VAR-03 · 05 · 12 · 13)
//   - AI 는 가짜(FakeVariantAi), 샌드박스는 진짜 프로세스, DB 는 이 테스트만 쓰는 H2 입니다.
//   - 시계(MutableClock)를 옮겨 가며 "다음 날" 을 흉내 냅니다. 09:00 KST = 같은 날 00:00 UTC
//   - 로컬 테스트 토큰(local-test-token)은 1번 사용자로 로그인한 것으로 봅니다.
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:variant-flow;MODE=PostgreSQL",
      // 전체 빌드 중에도 샌드박스가 넉넉히 끝나도록
      "app.variant.sandbox-timeout-sec=30"
    })
@AutoConfigureMockMvc
@Import(VariantFlowTest.TestBeans.class)
class VariantFlowTest {

  private static final String TOKEN = "local-test-token";
  private static final String JOB_KEY = "local-job-key";

  @TestConfiguration
  static class TestBeans {

    @Bean
    @Primary
    MutableClock variantTestClock() {
      return new MutableClock(Instant.parse("2026-10-24T12:00:00Z"));
    }

    @Bean
    @Primary
    FakeVariantAi fakeVariantAi() {
      return new FakeVariantAi();
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private MutableClock clock;
  @Autowired private FakeVariantAi ai;
  @Autowired private SourceProblemRepository sourceProblemRepository;
  @Autowired private ReviewItemRepository reviewItemRepository;
  @Autowired private VariantRepository variantRepository;
  @Autowired private AssignmentRepository assignmentRepository;
  @Autowired private AttemptRepository attemptRepository;
  @Autowired private WeeklySetRepository weeklySetRepository;
  @Autowired private WeeklySetService weeklySetService;

  @BeforeEach
  void cleanUp() {
    attemptRepository.deleteAll();
    assignmentRepository.deleteAll();
    weeklySetRepository.deleteAll();
    variantRepository.deleteAll();
    reviewItemRepository.deleteAll();
    sourceProblemRepository.deleteAll();
    ai.reset();
    at("2026-10-24");
  }

  // ---- 도우미 ----

  // 그날 09:00 (한국 시간)으로 시계를 옮깁니다.
  private void at(String date) {
    clock.set(Instant.parse(date + "T00:00:00Z"));
  }

  private ResultActions send(MockHttpServletRequestBuilder builder, String body) throws Exception {
    builder.header("Authorization", "Bearer " + TOKEN);
    if (body != null) {
      builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    return mockMvc.perform(builder);
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  private static String problemJson(
      String text, boolean correct, String unitCode, String typeCode, Instant solvedAt) {
    return """
        {"problemText":"%s","isCorrect":%s,"hasFigure":false,%s"solvedAt":%d}
        """
        .formatted(
            text,
            correct,
            unitCode == null
                ? ""
                : "\"unitCode\":\"%s\",\"typeCode\":\"%s\",".formatted(unitCode, typeCode),
            solvedAt.toEpochMilli());
  }

  // 원본을 등록하고 응답 JSON 을 돌려줍니다. (코드를 같이 보내면 SUPPORTED)
  private String register(String text, boolean correct, String typeCode) throws Exception {
    String unitCode = typeCode == null ? null : typeCode.substring(0, 7);
    ResultActions result =
        send(
            post("/api/v1/variant/source-problems"),
            problemJson(text, correct, unitCode, typeCode, clock.instant()));
    result.andExpect(status().isCreated());
    return body(result);
  }

  private void runJob(String name) throws Exception {
    mockMvc
        .perform(post("/api/v1/jobs/" + name).header("X-Job-Key", JOB_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DONE"));
  }

  private String daily() throws Exception {
    ResultActions result = send(get("/api/v1/variant/daily"), null);
    result.andExpect(status().isOk());
    return body(result);
  }

  private ResultActions answer(String assignmentId, String answer) throws Exception {
    return send(
        post("/api/v1/variant/assignments/" + assignmentId + "/attempts"),
        "{\"answer\":\"" + answer + "\",\"timeSpentSec\":60}");
  }

  private ReviewItem onlyItem() {
    List<ReviewItem> items = reviewItemRepository.findAll();
    assertThat(items).hasSize(1);
    return items.getFirst();
  }

  // ---- 원본 등록 (VAR-03 · 05) ----

  @Test
  @DisplayName("틀린 원본을 코드와 함께 등록하면 SUPPORTED · ACTIVE L0 항목이 생기고 다음 날부터 낸다")
  void registerWrongWithCodes() throws Exception {
    String json = register("$x^2-5x+k=0$ 의 두 근의 곱이 6", false, "CM1-U05-T03");

    assertThat((String) JsonPath.read(json, "$.supportStatus")).isEqualTo("supported");
    assertThat((String) JsonPath.read(json, "$.reviewItemId")).isNotNull();
    ReviewItem item = onlyItem();
    assertThat(item.getStatus()).isEqualTo(ReviewItemStatus.ACTIVE);
    assertThat(item.getCurrentLevel()).isZero();
    assertThat(item.getNextDueDate()).isEqualTo(LocalDate.parse("2026-10-25"));

    send(get("/api/v1/variant/review-items?status=active"), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].typeName").value("근과 계수의 관계"))
        .andExpect(jsonPath("$[0].status").value("active"))
        .andExpect(jsonPath("$[0].nextDueDate").value("2026-10-25"));
  }

  @Test
  @DisplayName("맞은 원본은 항목이 없고, 그림이 필요한 원본은 저장만 한다")
  void correctAndFigure() throws Exception {
    String correct = register("맞은 문제", true, "CM1-U05-T03");
    assertThat((Object) JsonPath.read(correct, "$.reviewItemId")).isNull();

    ResultActions figure =
        send(
            post("/api/v1/variant/source-problems"),
            "{\"problemText\":\"그래프 문제\",\"isCorrect\":false,\"hasFigure\":true,"
                + "\"solvedAt\":1792841400000}");
    figure
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.supportStatus").value("unsupported_figure"));
    assertThat(reviewItemRepository.count()).isZero();
    assertThat(sourceProblemRepository.count()).isEqualTo(2);
  }

  @Test
  @DisplayName("분류체계에 없는 코드 · 짝이 안 맞는 코드 · 한쪽만 보낸 코드는 400")
  void invalidCodes() throws Exception {
    send(
            post("/api/v1/variant/source-problems"),
            problemJson("문제", false, "CM1-U05", "CM1-U99-T01", clock.instant()))
        .andExpect(status().isBadRequest());
    send(
            post("/api/v1/variant/source-problems"),
            problemJson("문제", false, "CM1-U04", "CM1-U05-T03", clock.instant()))
        .andExpect(status().isBadRequest());
    send(
            post("/api/v1/variant/source-problems"),
            "{\"problemText\":\"문제\",\"isCorrect\":false,\"unitCode\":\"CM1-U05\",\"solvedAt\":1}")
        .andExpect(status().isBadRequest());
    send(post("/api/v1/variant/source-problems"), "{\"isCorrect\":false,\"solvedAt\":1}")
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("코드 없이 AI 가 꺼져 있으면 분류 대기 → 사람이 PATCH 로 정하면 SUPPORTED 와 학습 항목이 생긴다")
  void pendingThenPatched() throws Exception {
    ai.disable();
    String json = register("코드 없는 틀린 문제", false, null);
    assertThat((String) JsonPath.read(json, "$.supportStatus")).isEqualTo("pending_classification");
    assertThat(reviewItemRepository.count()).isZero();
    String id = JsonPath.read(json, "$.id");

    send(
            patch("/api/v1/variant/source-problems/" + id + "/classification"),
            "{\"unitCode\":\"CM1-U05\",\"typeCode\":\"CM1-U05-T03\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.supportStatus").value("supported"))
        .andExpect(jsonPath("$.reviewItemId").isNotEmpty());
    assertThat(onlyItem().getStatus()).isEqualTo(ReviewItemStatus.ACTIVE);

    // 이미 SUPPORTED 가 된 원본은 다시 정할 수 없습니다.
    send(
            patch("/api/v1/variant/source-problems/" + id + "/classification"),
            "{\"unitCode\":\"CM1-U05\",\"typeCode\":\"CM1-U05-T03\"}")
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("분류기: 정상 분류 · 없는 코드는 한 번 더 묻고 성공 · 두 번 틀리면 확인 필요 · 자신감 낮음 · 그림 필요")
  void classifier() throws Exception {
    ai.willClassify(new ClassificationDraft("CM1-U05", "CM1-U05-T03", 0.9, false, "근과 계수"));
    assertThat((String) JsonPath.read(register("분류1", false, null), "$.supportStatus"))
        .isEqualTo("supported");

    ai.willClassify(new ClassificationDraft("CM1-U05", "CM1-U99-T01", 0.9, false, "없는 코드"))
        .willClassify(new ClassificationDraft("CM1-U04", "CM1-U04-T01", 0.8, false, "고침"));
    assertThat((String) JsonPath.read(register("분류2", false, null), "$.typeCode"))
        .isEqualTo("CM1-U04-T01");
    assertThat(ai.classifyInputs.getLast()).contains("CM1-U99-T01");

    ai.willClassify(new ClassificationDraft("X", "Y", 0.9, false, "틀림"))
        .willClassify(new ClassificationDraft("X", "Z", 0.9, false, "또 틀림"));
    assertThat((String) JsonPath.read(register("분류3", false, null), "$.supportStatus"))
        .isEqualTo("needs_review");

    ai.willClassify(new ClassificationDraft("CM1-U05", "CM1-U05-T03", 0.3, false, "애매"));
    assertThat((String) JsonPath.read(register("분류4", false, null), "$.supportStatus"))
        .isEqualTo("needs_review");

    ai.willClassify(new ClassificationDraft("CM1-U06", "CM1-U06-T01", 0.9, true, "그래프"));
    assertThat((String) JsonPath.read(register("분류5", false, null), "$.supportStatus"))
        .isEqualTo("unsupported_figure");

    // SUPPORTED 가 된 두 문항(분류1 · 분류2)만 학습 항목이 생깁니다.
    assertThat(reviewItemRepository.count()).isEqualTo(2);
  }

  // ---- 일일 출제 · 채점 (VAR-12) ----

  @Test
  @DisplayName("흐름: 오답 등록 → 다음 날 작업 → 조회(정답 없음) → 오답 → 다음 날 같은 레벨 새 변형 → 또 오답 → 다음 날 L-1")
  void dailyFlowDropsToScaffold() throws Exception {
    at("2026-10-24");
    register("원본 문제", false, "CM1-U05-T03");

    // 오늘(24일)은 아직 낼 차례가 아닙니다.
    assertThat((List<?>) JsonPath.read(daily(), "$.items")).isEmpty();

    // 25일: L0 변형 하나 → 작업을 두 번 불러도 과제는 하나
    at("2026-10-25");
    ai.willGenerate(VariantDrafts.shortRootProduct("25일 변형: 두 근의 합이 $7$, 차가 $3$일 때 곱은?"));
    runJob(VariantDailyJob.NAME);
    runJob(VariantDailyJob.NAME);
    String day25 = daily();
    assertThat((List<?>) JsonPath.read(day25, "$.items")).hasSize(1);
    assertThat((Integer) JsonPath.read(day25, "$.items[0].level")).isZero();
    // 절대 규칙 9: 답을 내기 전 응답에는 정답 · 해설 · 검증 코드 · 선지 해설이 없습니다.
    assertThat(day25)
        .doesNotContain("shortAnswer")
        .doesNotContain("solution")
        .doesNotContain("verificationCode")
        .doesNotContain("answerIndex")
        .doesNotContain("rationale")
        .doesNotContain("Solve(");
    String first = JsonPath.read(day25, "$.items[0].assignmentId");

    answer(first, "11")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isCorrect").value(false))
        .andExpect(jsonPath("$.correctAnswer").value("10"))
        .andExpect(jsonPath("$.solution").isNotEmpty())
        .andExpect(jsonPath("$.reviewItem.currentLevel").value(0));
    // 같은 과제에 다시 내면 409
    answer(first, "10").andExpect(status().isConflict());

    // 26일: 같은 레벨(L0)의 새 변형 → 또 오답 → L-1
    at("2026-10-26");
    ai.willGenerate(VariantDrafts.shortRootProduct("26일 변형: 합이 $7$이고 차가 $3$인 두 근의 곱?"));
    runJob(VariantDailyJob.NAME);
    String day26 = daily();
    assertThat((Integer) JsonPath.read(day26, "$.items[0].level")).isZero();
    assertThat((String) JsonPath.read(day26, "$.items[0].problemText")).startsWith("26일");
    answer(JsonPath.read(day26, "$.items[0].assignmentId"), "1")
        .andExpect(jsonPath("$.reviewItem.currentLevel").value(-1));

    // 27일: L-1 비계 변형
    at("2026-10-27");
    ai.willGenerate(VariantDrafts.scaffolded());
    runJob(VariantDailyJob.NAME);
    String day27 = daily();
    assertThat((Integer) JsonPath.read(day27, "$.items[0].level")).isEqualTo(-1);
    assertThat((String) JsonPath.read(day27, "$.items[0].problemText")).contains("[1단계]");
  }

  @Test
  @DisplayName("흐름: L0 정답 → L1 → L1 정답 → 졸업 (서로 다른 날)")
  void dailyFlowGraduates() throws Exception {
    register("원본 문제", false, "CM1-U05-T03");

    at("2026-10-25");
    ai.alwaysGenerate(VariantDrafts.shortRootProduct());
    runJob(VariantDailyJob.NAME);
    answer(JsonPath.read(daily(), "$.items[0].assignmentId"), "10")
        .andExpect(jsonPath("$.isCorrect").value(true))
        .andExpect(jsonPath("$.reviewItem.currentLevel").value(1));

    at("2026-10-26");
    ai.reset();
    ai.alwaysGenerate(VariantDrafts.shortRootProduct("L1 변형: 두 근의 합 $7$, 차 $3$ → 곱"));
    runJob(VariantDailyJob.NAME);
    String day26 = daily();
    assertThat((Integer) JsonPath.read(day26, "$.items[0].level")).isEqualTo(1);
    answer(JsonPath.read(day26, "$.items[0].assignmentId"), "10")
        .andExpect(jsonPath("$.reviewItem.status").value("graduated"));

    // 졸업하면 더 이상 일일 출제하지 않습니다.
    at("2026-10-27");
    runJob(VariantDailyJob.NAME);
    assertThat((List<?>) JsonPath.read(daily(), "$.items")).isEmpty();
  }

  @Test
  @DisplayName("하루 상한(10)을 넘으면 주의 표시 → 오래 밀린 순으로 10개만 내고 나머지는 이월한다")
  void dailyCapAndPriority() throws Exception {
    for (int day = 1; day <= 12; day++) {
      clock.set(Instant.parse("2026-10-%02dT00:00:00Z".formatted(day)));
      register("상한 문제 " + day, false, "CM1-U05-T03");
    }
    // 12일에 푼(가장 늦게 밀린) 항목에 주의 표시를 붙입니다.
    ReviewItem attention =
        reviewItemRepository.findAll().stream()
            .filter(item -> item.getNextDueDate().equals(LocalDate.parse("2026-10-13")))
            .findFirst()
            .orElseThrow();
    ReviewItemState state = attention.state();
    attention.apply(
        new ReviewItemState(state.status(), -1, 3, true, state.nextDueDate(), null, null),
        clock.instant());
    reviewItemRepository.save(attention);

    at("2026-10-25");
    // 우선순위 1번인 주의 표시 항목(L-1)이 먼저 만들어지므로 비계 답을 먼저 주고, 나머지(L0)는 보통 답으로 채웁니다.
    ai.willGenerate(VariantDrafts.scaffolded()).alwaysGenerate(VariantDrafts.shortRootProduct());
    runJob(VariantDailyJob.NAME);

    String json = daily();
    assertThat((List<?>) JsonPath.read(json, "$.items")).hasSize(10);
    List<Assignment> assigned =
        assignmentRepository.findByUserIdAndKindAndAssignedDateOrderByIdAsc(
            1L, AssignmentKind.DAILY, LocalDate.parse("2026-10-25"));
    List<Long> itemIds = assigned.stream().map(Assignment::getReviewItemId).toList();
    assertThat(itemIds).contains(attention.getId());
    // 주의 표시 1개 + 가장 오래 밀린 9개(1 ~ 9일에 푼 것). 10 · 11일에 푼 것은 이월
    List<LocalDate> carriedOver =
        reviewItemRepository.findAll().stream()
            .filter(item -> !itemIds.contains(item.getId()))
            .map(ReviewItem::getNextDueDate)
            .sorted()
            .toList();
    assertThat(carriedOver)
        .containsExactly(LocalDate.parse("2026-10-11"), LocalDate.parse("2026-10-12"));
  }

  @Test
  @DisplayName("생성이 3번 모두 실패하면 과제가 없고 상태는 그대로이며, 그날은 다시 만들지 않는다")
  void generationFailureCarriesOver() throws Exception {
    register("원본 문제", false, "CM1-U05-T03");
    at("2026-10-25");
    ai.alwaysGenerate(VariantDrafts.shortWrongAnswer());

    runJob(VariantDailyJob.NAME);
    runJob(VariantDailyJob.NAME);

    String json = daily();
    assertThat((List<?>) JsonPath.read(json, "$.items")).isEmpty();
    assertThat((Integer) JsonPath.read(json, "$.preparingCount")).isEqualTo(1);
    // 두 번째 작업은 오늘 이미 3번 실패해서 더 부르지 않았습니다. (AI 호출 3번뿐)
    assertThat(ai.variantInputs).hasSize(3);
    ReviewItem item = onlyItem();
    assertThat(item.getCurrentLevel()).isZero();
    assertThat(item.getNextDueDate()).isEqualTo(LocalDate.parse("2026-10-25"));
  }

  @Test
  @DisplayName("미응답 과제는 다음 날 만료되고 409, 항목 상태는 그대로이며 다시 출제된다")
  void unansweredExpires() throws Exception {
    register("원본 문제", false, "CM1-U05-T03");
    at("2026-10-25");
    ai.alwaysGenerate(VariantDrafts.shortRootProduct());
    runJob(VariantDailyJob.NAME);
    String assignmentId = JsonPath.read(daily(), "$.items[0].assignmentId");

    at("2026-10-26");
    // 만료 작업 전에도 마감이 지났으면 409 입니다.
    answer(assignmentId, "10").andExpect(status().isConflict());
    runJob(VariantExpireJob.NAME);
    assertThat(assignmentRepository.findById(Long.valueOf(assignmentId)).orElseThrow().getStatus())
        .isEqualTo(AssignmentStatus.EXPIRED);
    assertThat(onlyItem().getCurrentLevel()).isZero();

    ai.reset();
    ai.alwaysGenerate(VariantDrafts.shortRootProduct("다시 낸 변형: 합 $7$ 차 $3$ 두 근의 곱"));
    runJob(VariantDailyJob.NAME);
    assertThat((List<?>) JsonPath.read(daily(), "$.items")).hasSize(1);
  }

  @Test
  @DisplayName("답의 모양이 틀리면 400 (단답형에 글자, 5지선다에 7)")
  void badAnswerShape() throws Exception {
    register("원본 1", false, "CM1-U05-T03");
    register("원본 2", false, "CM1-U08-T03");
    at("2026-10-25");
    ai.willGenerate(VariantDrafts.shortRootProduct()).alwaysGenerate(VariantDrafts.mc5Inequality());
    runJob(VariantDailyJob.NAME);
    String json = daily();
    String shortId = JsonPath.read(json, "$.items[?(@.format == 'short')].assignmentId").toString();
    String mcId = JsonPath.read(json, "$.items[?(@.format == 'mc5')].assignmentId").toString();
    shortId = shortId.replaceAll("[\\[\\]\"]", "");
    mcId = mcId.replaceAll("[\\[\\]\"]", "");

    answer(shortId, "열").andExpect(status().isBadRequest());
    answer(mcId, "7").andExpect(status().isBadRequest());
    send(get("/api/v1/variant/daily?date=2026-10-25"), null)
        .andExpect(jsonPath("$.items[?(@.format == 'mc5')].choicesLatex[0]").exists());
  }

  // ---- 주간 심화 (VAR-13) ----

  @Test
  @DisplayName("주간 (A): 지난주(월 ~ 일)에 맞힌 것만 유형별로 세어 많은 순 · 동률이면 최근 순으로 5개, 유형마다 가장 최근 원본")
  void weeklyCorrectTypes() throws Exception {
    // 돌아보는 주: 2026-10-19(월) ~ 10-25(일). 시각은 UTC 라 한국 시간으로는 9시간 뒤입니다.
    // T01 3개, T02 2개(21일), T03 2개(23일, 더 최근), U04 의 T01 · T02 · T03 1개씩 → 위 5개는 T01, T03, T02, 그다음
    // 1개짜리 중 최근 둘. 주 밖(18일 일요일 · 26일 월요일)에 푼 U03 · U04-T04 는 세지 않습니다.
    String[][] plan = {
      {"CM1-U03-T01", "x1", "2026-10-18T10:00:00Z"},
      {"CM1-U05-T01", "01", "2026-10-19T00:00:00Z"},
      {"CM1-U05-T01", "02", "2026-10-19T01:00:00Z"},
      {"CM1-U05-T01", "03", "2026-10-19T02:00:00Z"},
      {"CM1-U05-T02", "09", "2026-10-20T00:00:00Z"},
      {"CM1-U05-T02", "10", "2026-10-21T00:00:00Z"},
      {"CM1-U05-T03", "11", "2026-10-22T00:00:00Z"},
      {"CM1-U05-T03", "12", "2026-10-23T00:00:00Z"},
      {"CM1-U04-T01", "13", "2026-10-24T00:00:00Z"},
      {"CM1-U04-T02", "14", "2026-10-25T00:00:00Z"},
      {"CM1-U04-T03", "15", "2026-10-25T01:00:00Z"},
      {"CM1-U04-T04", "x2", "2026-10-26T00:00:00Z"}
    };
    for (String[] row : plan) {
      clock.set(Instant.parse(row[2]));
      register("맞은 " + row[0] + " " + row[1], true, row[0]);
    }
    List<WeeklySetService.Pick> picks =
        weeklySetService.pickCorrectTypes(1L, LocalDate.parse("2026-10-19"));

    List<String> types =
        picks.stream()
            .map(
                p ->
                    sourceProblemRepository
                        .findById(p.sourceProblemId())
                        .orElseThrow()
                        .getTypeCode())
            .toList();
    assertThat(types)
        .containsExactly("CM1-U05-T01", "CM1-U05-T03", "CM1-U05-T02", "CM1-U04-T03", "CM1-U04-T02");
    // T01 의 대표는 가장 최근(19일 11시)에 푼 원본
    assertThat(
            sourceProblemRepository
                .findById(picks.getFirst().sourceProblemId())
                .orElseThrow()
                .getProblemText())
        .endsWith("03");
  }

  @Test
  @DisplayName("주간 세트: 월요일에 (A)·(B) 를 만들고 두 번 불러도 하나, ACTIVE 항목은 빠지고, 정답은 답한 뒤에만 보인다")
  void weeklyFlow() throws Exception {
    // 10-19(월) 주: 맞은 문제 하나, 졸업한 오답 하나, 아직 ACTIVE 인 오답 하나
    at("2026-10-20");
    register("맞은 원본", true, "CM1-U05-T03");
    register("졸업할 원본", false, "CM1-U05-T01");
    register("아직 연습 중인 원본", false, "CM1-U05-T02");
    ReviewItem graduated =
        reviewItemRepository.findAll().stream()
            .filter(
                i ->
                    sourceProblemRepository
                        .findById(i.getSourceProblemId())
                        .orElseThrow()
                        .getProblemText()
                        .equals("졸업할 원본"))
            .findFirst()
            .orElseThrow();
    graduated.apply(
        new ReviewItemState(ReviewItemStatus.GRADUATED, 1, 0, false, null, clock.instant(), null),
        clock.instant());
    reviewItemRepository.save(graduated);

    // 월요일이 아닌 날(일요일)은 아무것도 하지 않습니다.
    at("2026-10-25");
    runJob(VariantWeeklyJob.NAME);
    assertThat(weeklySetRepository.count()).isZero();

    at("2026-10-26");
    ai.willGenerate(VariantDrafts.shortRootProduct("심화 A"))
        .willGenerate(VariantDrafts.shortRootProduct("심화 B"));
    runJob(VariantWeeklyJob.NAME);
    runJob(VariantWeeklyJob.NAME);
    assertThat(weeklySetRepository.count()).isEqualTo(1);

    String set = body(send(get("/api/v1/variant/weekly?week=2026-10-19"), null));
    assertThat((List<?>) JsonPath.read(set, "$.items")).hasSize(2);
    assertThat((String) JsonPath.read(set, "$.items[0].source")).isEqualTo("a");
    assertThat((String) JsonPath.read(set, "$.items[1].source")).isEqualTo("b");
    assertThat((String) JsonPath.read(set, "$.weekStart")).isEqualTo("2026-10-19");
    assertThat((String) JsonPath.read(set, "$.dueDate")).isEqualTo("2026-11-02");
    assertThat(set).doesNotContain("solution").doesNotContain("shortAnswer");
    // week 를 빼면 가장 최근 세트입니다.
    assertThat(body(send(get("/api/v1/variant/weekly"), null))).isEqualTo(set);
    // includeAnswers 여도 아직 답하지 않은 문항은 정답이 없습니다.
    String withAnswers =
        body(send(get("/api/v1/variant/weekly?week=2026-10-19&includeAnswers=true"), null));
    assertThat((Object) JsonPath.read(withAnswers, "$.items[0].answerKey")).isNull();

    // (A) 오답 → L1 ACTIVE 항목이 새로 생김
    answer(JsonPath.read(set, "$.items[0].assignmentId"), "1")
        .andExpect(jsonPath("$.isCorrect").value(false))
        .andExpect(jsonPath("$.reviewItem.status").value("active"))
        .andExpect(jsonPath("$.reviewItem.currentLevel").value(1));
    // (B) 정답 → MASTERED
    answer(JsonPath.read(set, "$.items[1].assignmentId"), "10")
        .andExpect(jsonPath("$.reviewItem.status").value("mastered"));

    String after =
        body(send(get("/api/v1/variant/weekly?week=2026-10-19&includeAnswers=true"), null));
    assertThat((String) JsonPath.read(after, "$.items[0].answerKey.correctAnswer")).isEqualTo("10");

    send(get("/api/v1/variant/weekly?week=2026-10-12"), null).andExpect(status().isNotFound());
    // 월요일이 아닌 날짜 · 날짜가 아닌 글자는 400
    send(get("/api/v1/variant/weekly?week=2026-10-20"), null).andExpect(status().isBadRequest());
    send(get("/api/v1/variant/weekly?week=10월"), null).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("주간 (B) 오답 → ACTIVE L0 로 돌아와 다음 날부터 일일 출제, (A) 오답이라도 이미 ACTIVE 면 새로 안 만든다")
  void weeklyWrongReturnsToDaily() throws Exception {
    at("2026-10-20");
    register("졸업할 원본", false, "CM1-U05-T01");
    ReviewItem item = onlyItem();
    item.apply(
        new ReviewItemState(ReviewItemStatus.GRADUATED, 1, 0, false, null, clock.instant(), null),
        clock.instant());
    reviewItemRepository.save(item);
    // 맞은 원본인데 지난번 (A) 를 틀려서 이미 ACTIVE 항목이 있는 경우
    Long correctId = Long.valueOf(JsonPath.read(register("맞은 원본", true, "CM1-U05-T03"), "$.id"));
    ReviewItem existing =
        reviewItemRepository.save(
            new ReviewItem(
                1L,
                correctId,
                ReviewItemOrigin.WEEKLY_CORRECT_FAIL,
                ReviewItemState.newWeeklyCorrectFail(LocalDate.parse("2026-10-20")),
                clock.instant()));

    at("2026-10-26");
    ai.willGenerate(VariantDrafts.shortRootProduct("심화 A"))
        .willGenerate(VariantDrafts.shortRootProduct("심화 B"));
    runJob(VariantWeeklyJob.NAME);
    String set = body(send(get("/api/v1/variant/weekly?week=2026-10-19"), null));

    // (A) 오답: 이미 있는 ACTIVE 항목을 그대로 돌려주고 새로 만들지 않습니다.
    answer(JsonPath.read(set, "$.items[0].assignmentId"), "3")
        .andExpect(jsonPath("$.reviewItem.id").value(String.valueOf(existing.getId())));
    assertThat(reviewItemRepository.count()).isEqualTo(2);

    // (B) 오답: ACTIVE L0 로 복귀, 다음 날부터
    answer(JsonPath.read(set, "$.items[1].assignmentId"), "3")
        .andExpect(jsonPath("$.reviewItem.status").value("active"))
        .andExpect(jsonPath("$.reviewItem.currentLevel").value(0));
    ReviewItem back = reviewItemRepository.findById(item.getId()).orElseThrow();
    assertThat(back.getNextDueDate()).isEqualTo(LocalDate.parse("2026-10-27"));
  }

  @Test
  @DisplayName("아무것도 없는 학생은 주간 세트를 만들지 않는다")
  void noWeeklySetWithoutPicks() throws Exception {
    at("2026-10-26");
    runJob(VariantWeeklyJob.NAME);

    assertThat(weeklySetRepository.count()).isZero();
  }

  @Test
  @DisplayName("아직 오지 않은 날의 일일 과제는 400, 다른 사람의 과제는 404")
  void dateAndOwnership() throws Exception {
    send(get("/api/v1/variant/daily?date=2099-01-01"), null).andExpect(status().isBadRequest());
    answer("999999", "1").andExpect(status().isNotFound());
  }
}
