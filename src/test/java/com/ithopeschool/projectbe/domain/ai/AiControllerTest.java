package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.ai.dto.AnalysisRequest;
import com.ithopeschool.projectbe.domain.ai.dto.AnalysisResponse;
import com.ithopeschool.projectbe.domain.ai.dto.CardDefaultsRequest;
import com.ithopeschool.projectbe.domain.ai.dto.CardDefaultsResponse;
import com.ithopeschool.projectbe.domain.ai.dto.CardParseRequest;
import com.ithopeschool.projectbe.domain.ai.dto.CardParseResponse;
import com.ithopeschool.projectbe.domain.ai.dto.CardPhotoRequest;
import com.ithopeschool.projectbe.domain.ai.dto.CardPhotoResponse;
import com.ithopeschool.projectbe.domain.ai.dto.ChecklistResponse;
import com.ithopeschool.projectbe.domain.ai.dto.CoachNudgeRequest;
import com.ithopeschool.projectbe.domain.ai.dto.CoachNudgeResponse;
import com.ithopeschool.projectbe.domain.ai.dto.DailyMissionsResponse;
import com.ithopeschool.projectbe.domain.ai.dto.DayPlanRequest;
import com.ithopeschool.projectbe.domain.ai.dto.DayPlanResponse;
import com.ithopeschool.projectbe.domain.ai.dto.DayPlanReviseResponse;
import com.ithopeschool.projectbe.domain.ai.dto.ExamNextStepResponse;
import com.ithopeschool.projectbe.domain.ai.dto.FailCauseResponse;
import com.ithopeschool.projectbe.domain.ai.dto.FirstStepRequest;
import com.ithopeschool.projectbe.domain.ai.dto.FirstStepResponse;
import com.ithopeschool.projectbe.domain.ai.dto.LongPlanOutline;
import com.ithopeschool.projectbe.domain.ai.dto.LongPlanResponse;
import com.ithopeschool.projectbe.domain.ai.dto.ProfileLinesResponse;
import com.ithopeschool.projectbe.domain.ai.dto.ProofPhotoRequest;
import com.ithopeschool.projectbe.domain.ai.dto.ProofPhotoResponse;
import com.ithopeschool.projectbe.domain.ai.dto.QuickCardResponse;
import com.ithopeschool.projectbe.domain.ai.dto.RoutineCardRequest;
import com.ithopeschool.projectbe.domain.ai.dto.RoutineCardResponse;
import com.ithopeschool.projectbe.domain.ai.dto.SettingsFromTextResponse;
import com.ithopeschool.projectbe.domain.ai.dto.StudyQuizRequest;
import com.ithopeschool.projectbe.domain.ai.dto.StudyQuizResponse;
import com.ithopeschool.projectbe.domain.ai.dto.TextLabelResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Controller 만 띄워서 요청 모양 검사와 응답 모양을 확인합니다. 진짜 로직(…Service)은 모두 가짜입니다.
@WebMvcTest(AiController.class)
class AiControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ChecklistService checklistService;
  @MockitoBean private FirstStepService firstStepService;
  @MockitoBean private FailCauseService failCauseService;
  @MockitoBean private ExamNextStepService examNextStepService;
  @MockitoBean private TextLabelService textLabelService;
  @MockitoBean private RoutineCardService routineCardService;
  @MockitoBean private QuickCardService quickCardService;
  @MockitoBean private SettingsFromTextService settingsFromTextService;
  @MockitoBean private ProfileLinesService profileLinesService;
  @MockitoBean private CardParseService cardParseService;
  @MockitoBean private CardPhotoService cardPhotoService;
  @MockitoBean private ProofPhotoService proofPhotoService;
  @MockitoBean private CardDefaultsService cardDefaultsService;
  @MockitoBean private SelfAnalysisService selfAnalysisService;
  @MockitoBean private DayPlanService dayPlanService;
  @MockitoBean private DailyMissionsService dailyMissionsService;
  @MockitoBean private DayPlanReviseService dayPlanReviseService;
  @MockitoBean private LongPlanService longPlanService;
  @MockitoBean private StudyQuizService studyQuizService;
  @MockitoBean private CoachNudgeService coachNudgeService;

  private ResultActions checklist(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/ai/checklist").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void expectRejected(String body, String message) throws Exception {
    checklist(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(checklistService);
  }

  @Test
  @DisplayName("보내면 200 과 항목 목록이다 (저장하지 않으므로 201 이 아니다)")
  void suggests() throws Exception {
    given(checklistService.suggest(any()))
        .willReturn(ChecklistResponse.of(List.of("42~45쪽 문제 풀기", "틀린 문제 표시하기", "채점하기")));

    checklist("{\"title\":\"수학 문제집\",\"goal\":\"6월 모의고사 수학 2등급\"}")
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"showHelp\":false,\"items\":[\"42~45쪽 문제 풀기\",\"틀린 문제 표시하기\",\"채점하기\"]}",
                    true));
  }

  @Test
  @DisplayName("AI 가 못 만들었으면 에러가 아니라 200 과 items: null 이다")
  void noItemsIsNull() throws Exception {
    given(checklistService.suggest(any())).willReturn(ChecklistResponse.of(null));

    checklist("{\"title\":\"수학 문제집\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":false,\"items\":null}", true));
  }

  @Test
  @DisplayName("위기 표현이면 200 과 showHelp: true, items: null 이다")
  void crisisShowsHelp() throws Exception {
    given(checklistService.suggest(any())).willReturn(ChecklistResponse.help());

    checklist("{\"title\":\"그냥 사라지고 싶어\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":true,\"items\":null}", true));
  }

  @Test
  @DisplayName("제목이 비었거나 200자를 넘거나, 목표가 100자를 넘으면 400 이고 서비스를 부르지 않는다")
  void rejectsBadRequests() throws Exception {
    expectRejected("{\"title\":\"\"}", "카드 제목을 적어 주세요");
    expectRejected("{\"title\":\"   \"}", "카드 제목을 적어 주세요");
    expectRejected("{}", "카드 제목을 적어 주세요");
    expectRejected("{\"title\":\"" + "가".repeat(201) + "\"}", "카드 제목은 200자 이하로 적어 주세요");
    expectRejected(
        "{\"title\":\"수학\",\"goal\":\"" + "가".repeat(101) + "\"}", "목표는 100자 이하로 적어 주세요");
  }

  @Test
  @DisplayName("로그인하지 않았으면 401 이다 (서비스가 던진 그대로)")
  void loginRequired() throws Exception {
    given(checklistService.suggest(any()))
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    checklist("{\"title\":\"수학 문제집\"}")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  // ---- 첫 준비 행동 ----

  private ResultActions firstStep(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/ai/first-step").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void expectFirstStepRejected(String body, String message) throws Exception {
    firstStep(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(firstStepService);
  }

  @Test
  @DisplayName("첫 준비 행동: 보내면 200 과 행동 하나이고, 단계를 안 보내면 0 으로 받는다")
  void firstStepSuggests() throws Exception {
    given(firstStepService.suggest(eq(new FirstStepRequest("42~45쪽 문제 풀기", "수학 문제집", 0, null))))
        .willReturn(FirstStepResponse.of("수학 문제집 42쪽 펴기"));

    firstStep("{\"item\":\" 42~45쪽 문제 풀기 \",\"title\":\"수학 문제집\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":false,\"step\":\"수학 문제집 42쪽 펴기\"}", true));
  }

  @Test
  @DisplayName("첫 준비 행동: AI 가 못 만들면 200 과 step: null, 위기 표현이면 showHelp: true 다")
  void firstStepNullAndHelp() throws Exception {
    given(firstStepService.suggest(any())).willReturn(FirstStepResponse.of(null));
    firstStep("{\"item\":\"단어 외우기\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":false,\"step\":null}", true));

    given(firstStepService.suggest(any())).willReturn(FirstStepResponse.help());
    firstStep("{\"item\":\"죽고 싶다\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":true,\"step\":null}", true));
  }

  @Test
  @DisplayName("첫 준비 행동: 첫 항목이 비었거나 100자를 넘거나, 단계가 0 ~ 2 밖이거나, 앞의 행동이 40자를 넘으면 400 이다")
  void firstStepRejectsBadRequests() throws Exception {
    expectFirstStepRejected("{\"item\":\" \"}", "체크리스트 첫 항목을 보내 주세요");
    expectFirstStepRejected("{}", "체크리스트 첫 항목을 보내 주세요");
    expectFirstStepRejected("{\"item\":\"" + "가".repeat(101) + "\"}", "첫 항목은 100자 이하로 보내 주세요");
    expectFirstStepRejected("{\"item\":\"단어\",\"level\":3}", "단계는 0 ~ 2 사이로 보내 주세요");
    expectFirstStepRejected("{\"item\":\"단어\",\"level\":-1}", "단계는 0 ~ 2 사이로 보내 주세요");
    expectFirstStepRejected(
        "{\"item\":\"단어\",\"level\":1,\"previousStep\":\"" + "가".repeat(41) + "\"}",
        "앞의 행동은 40자 이하로 보내 주세요");
    expectFirstStepRejected(
        "{\"item\":\"단어\",\"title\":\"" + "가".repeat(201) + "\"}", "카드 제목은 200자 이하로 적어 주세요");
  }

  // ---- 예상 실패 원인 선지 ----

  private ResultActions failCauseOptions(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/ai/fail-cause-options")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  @Test
  @DisplayName("예상 실패 원인 선지: 보내면 200 과 선지 3개 (reason · text · inner), 못 만들면 options: null")
  void failCauseOptionsSuggests() throws Exception {
    given(failCauseService.suggest(any()))
        .willReturn(
            FailCauseResponse.of(
                List.of(
                    new FailCauseResponse.Option("tired", "학원 갔다 와서 졸림", false),
                    new FailCauseResponse.Option("event", "친구 연락에 나가기", false),
                    new FailCauseResponse.Option("phone", "문제 막힐 때 폰부터 집기", true))));
    failCauseOptions("{\"title\":\"수학 문제집\"}")
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"showHelp\":false,\"options\":["
                        + "{\"reason\":\"tired\",\"text\":\"학원 갔다 와서 졸림\",\"inner\":false},"
                        + "{\"reason\":\"event\",\"text\":\"친구 연락에 나가기\",\"inner\":false},"
                        + "{\"reason\":\"phone\",\"text\":\"문제 막힐 때 폰부터 집기\",\"inner\":true}]}",
                    true));

    given(failCauseService.suggest(any())).willReturn(FailCauseResponse.of(null));
    failCauseOptions("{\"title\":\"수학 문제집\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":false,\"options\":null}", true));
  }

  @Test
  @DisplayName("예상 실패 원인 선지: 제목이 비었거나 200자를 넘으면 400 이고 서비스를 부르지 않는다")
  void failCauseOptionsRejectsBadRequests() throws Exception {
    failCauseOptions("{\"title\":\" \"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("카드 제목을 적어 주세요"));
    failCauseOptions("{\"title\":\"" + "가".repeat(201) + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("카드 제목은 200자 이하로 적어 주세요"));
    verifyNoInteractions(failCauseService);
  }

  // ---- 시험 회고 → 다음 카드에 넣을 한 가지 ----

  private ResultActions examNextStep(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/ai/exam-next-step").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  @DisplayName("시험 회고 → 다음 한 가지: 200 과 행동·과목, 못 만들면 nextStep: null, 회고가 없으면 404")
  void examNextStepSuggests() throws Exception {
    given(examNextStepService.suggest(any()))
        .willReturn(
            ExamNextStepResponse.of(new ExamNextStepResponse.NextStep("수학 기출 1회분 시간 재고 풀기", "3")));
    examNextStep("{\"examId\":\"9\"}")
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"showHelp\":false,\"nextStep\":{\"text\":\"수학 기출 1회분 시간 재고 풀기\",\"subjectId\":\"3\"}}",
                    true));

    given(examNextStepService.suggest(any())).willReturn(ExamNextStepResponse.of(null));
    examNextStep("{\"examId\":\"9\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":false,\"nextStep\":null}", true));

    given(examNextStepService.suggest(any()))
        .willThrow(new ApiException(ErrorCode.NOT_FOUND, "이 시험의 회고가 아직 없어요. 회고를 먼저 써 주세요"));
    examNextStep("{\"examId\":\"9\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.message").value("이 시험의 회고가 아직 없어요. 회고를 먼저 써 주세요"));
  }

  @Test
  @DisplayName("시험 회고 → 다음 한 가지: 시험 번호가 없거나 숫자가 아니면 400 이고 서비스를 부르지 않는다")
  void examNextStepRejectsBadRequests() throws Exception {
    examNextStep("{}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("시험 번호를 보내 주세요"));
    examNextStep("{\"examId\":\"abc\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("시험 번호가 올바르지 않아요"));
    verifyNoInteractions(examNextStepService);
  }

  // ---- 글에 이름표 붙이기 ----

  private ResultActions classifyText(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/ai/classify-text").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  @DisplayName("글에 이름표 붙이기: 200 과 이름표, 못 붙이면 label: null")
  void classifyTextLabels() throws Exception {
    given(textLabelService.label(any())).willReturn(TextLabelResponse.of("outer"));
    classifyText("{\"kind\":\"reason\",\"text\":\"동생이 떠들었다\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":false,\"label\":\"outer\"}", true));

    given(textLabelService.label(any())).willReturn(TextLabelResponse.of(null));
    classifyText("{\"kind\":\"memo\",\"text\":\"문자 보내기\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":false,\"label\":null}", true));
  }

  @Test
  @DisplayName("글에 이름표 붙이기: 종류가 reason · memo 가 아니거나, 글이 비었거나 100자를 넘으면 400 이다")
  void classifyTextRejectsBadRequests() throws Exception {
    classifyText("{\"text\":\"문자\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("글의 종류를 보내 주세요"));
    classifyText("{\"kind\":\"diary\",\"text\":\"문자\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("글의 종류는 reason 또는 memo 로 보내 주세요"));
    classifyText("{\"kind\":\"memo\",\"text\":\" \"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("분류할 글을 보내 주세요"));
    classifyText("{\"kind\":\"memo\",\"text\":\"" + "가".repeat(101) + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("글은 100자 이하로 보내 주세요"));
    verifyNoInteractions(textLabelService);
  }

  // ---- 루틴 카드 제안 (이슈 #239) ----

  private ResultActions routineCards(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/ai/routine-cards").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void expectRoutineCardsRejected(String body, String message) throws Exception {
    routineCards(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(routineCardService);
  }

  @Test
  @DisplayName("루틴 카드: 보내면 200 과 카드 목록이고, dDay · totalAmount · amountUnit 이 서비스까지 간다")
  void routineCardsSuggests() throws Exception {
    given(routineCardService.suggest(any()))
        .willReturn(
            new RoutineCardResponse(
                false,
                List.of(
                    new RoutineCardResponse.Suggestion(
                        "영단어 30쪽",
                        List.of(1, 2, 3, 4, 5),
                        "19:00",
                        50,
                        List.of("폰을 다른 방에 두고 오기")))));

    routineCards(
            "{\"goal\":\" 영단어장 1회독 \",\"obstacle\":\"\",\"plan\":\"폰을 다른 방에 두기\","
                + "\"dDay\":\"2026-10-26\",\"totalAmount\":300,\"amountUnit\":\"쪽\"}")
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"showHelp\":false,\"cards\":[{\"title\":\"영단어 30쪽\",\"repeatDays\":[1,2,3,4,5],"
                        + "\"startTime\":\"19:00\",\"durationMinutes\":50,\"checklist\":[\"폰을 다른 방에 두고 오기\"]}]}",
                    true));
    // 앞뒤 공백은 지우고, 빈 방해물은 없는 것(null)으로 받습니다.
    verify(routineCardService)
        .suggest(
            new RoutineCardRequest(
                "영단어장 1회독", null, "폰을 다른 방에 두기", LocalDate.of(2026, 10, 26), 300, "쪽"));
  }

  @Test
  @DisplayName("루틴 카드: AI 가 못 만들었으면 200 과 cards: null, 위기 표현이면 showHelp: true")
  void routineCardsNullAndHelp() throws Exception {
    given(routineCardService.suggest(any())).willReturn(new RoutineCardResponse(false, null));
    routineCards("{\"goal\":\"수학 3등급\",\"plan\":\"문제집부터 펴기\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":false,\"cards\":null}", true));

    given(routineCardService.suggest(any())).willReturn(RoutineCardResponse.help());
    routineCards("{\"goal\":\"수학 3등급\",\"plan\":\"그냥 사라지고 싶어\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":true,\"cards\":null}", true));
  }

  @Test
  @DisplayName("루틴 카드: 목표·대처가 비었거나 길거나, 분량·단위가 범위를 벗어나면 400 이고 서비스를 부르지 않는다")
  void routineCardsRejectsBadRequests() throws Exception {
    expectRoutineCardsRejected("{\"plan\":\"대처\"}", "목표를 적어 주세요");
    expectRoutineCardsRejected("{\"goal\":\"목표\",\"plan\":\"  \"}", "대처를 적어 주세요");
    expectRoutineCardsRejected(
        "{\"goal\":\"" + "가".repeat(101) + "\",\"plan\":\"대처\"}", "목표는 100자 이하로 적어 주세요");
    expectRoutineCardsRejected(
        "{\"goal\":\"목표\",\"obstacle\":\"" + "가".repeat(101) + "\",\"plan\":\"대처\"}",
        "방해물은 100자 이하로 적어 주세요");
    expectRoutineCardsRejected(
        "{\"goal\":\"목표\",\"plan\":\"" + "가".repeat(101) + "\"}", "대처는 100자 이하로 적어 주세요");
    expectRoutineCardsRejected(
        "{\"goal\":\"목표\",\"plan\":\"대처\",\"totalAmount\":0}", "전체 분량은 1 ~ 100000 사이로 보내 주세요");
    expectRoutineCardsRejected(
        "{\"goal\":\"목표\",\"plan\":\"대처\",\"amountUnit\":\"가나다라마바\"}", "분량 단위는 5자 이하로 보내 주세요");
  }

  // ---- 즉석 카드 ----

  private ResultActions quickCard(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/ai/quick-card").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  @DisplayName("즉석 카드: 200 과 제목·항목, 못 만들면 card: null")
  void quickCardSuggests() throws Exception {
    given(quickCardService.suggest(any()))
        .willReturn(
            QuickCardResponse.of(
                new QuickCardResponse.Card("수학 문제집", List.of("42쪽 펴기", "5문제 풀기"))));
    quickCard("{\"text\":\"수학 하자\"}")
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"showHelp\":false,\"card\":{\"title\":\"수학 문제집\",\"items\":[\"42쪽 펴기\",\"5문제 풀기\"]}}",
                    true));

    given(quickCardService.suggest(any())).willReturn(QuickCardResponse.of(null));
    quickCard("{\"text\":\"수학 하자\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":false,\"card\":null}", true));
  }

  @Test
  @DisplayName("즉석 카드: 한 줄이 비었거나 100자를 넘으면 400 이고 서비스를 부르지 않는다")
  void quickCardRejectsBadRequests() throws Exception {
    quickCard("{\"text\":\" \"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("한 줄을 적어 주세요"));
    quickCard("{\"text\":\"" + "가".repeat(101) + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("한 줄은 100자 이하로 적어 주세요"));
    verifyNoInteractions(quickCardService);
  }

  // ---- 말로 카드 만들기 ----

  private ResultActions parseCards(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/ai/cards/parse").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  @DisplayName("말로 카드 만들기: 200 과 초안들(날짜 · 시각 · 길이 · 체크리스트 · 목표), 못 만들면 drafts: null")
  void parseCardsReturnsDrafts() throws Exception {
    given(cardParseService.parse(any()))
        .willReturn(
            new CardParseResponse(
                false,
                List.of(
                    new CardParseResponse.Draft(
                        LocalDate.of(2026, 10, 18),
                        "19:00",
                        25,
                        true,
                        "수학 문제집",
                        List.of("문제집 2장 풀기"),
                        "4")),
                "기록 이유"));
    parseCards("{\"date\":\"2026-10-17\",\"text\":\" 내일 7시에 수학 문제집 2장 \"}")
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"showHelp\":false,\"drafts\":[{\"date\":\"2026-10-18\",\"startTime\":\"19:00\",\"durationMinutes\":25,\"durationFromRecord\":true,\"title\":\"수학 문제집\",\"checklist\":[\"문제집 2장 풀기\"],\"goalId\":\"4\"}],\"durationEvidence\":\"기록 이유\"}",
                    true));
    verify(cardParseService)
        .parse(new CardParseRequest(LocalDate.of(2026, 10, 17), "내일 7시에 수학 문제집 2장"));

    given(cardParseService.parse(any())).willReturn(CardParseResponse.notMade());
    parseCards("{\"date\":\"2026-10-17\",\"text\":\"수학\"}")
        .andExpect(status().isOk())
        .andExpect(
            content().json("{\"showHelp\":false,\"drafts\":null,\"durationEvidence\":null}", true));
  }

  @Test
  @DisplayName("말로 카드 만들기: 날짜가 없거나 문장이 비었거나 200자를 넘으면 400 이고 서비스를 부르지 않는다")
  void parseCardsRejectsBadRequests() throws Exception {
    parseCards("{\"text\":\"수학\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("기준 날짜를 보내 주세요"));
    parseCards("{\"date\":\"2026-10-17\",\"text\":\" \"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("어떤 카드를 만들지 말해 주세요"));
    parseCards("{\"date\":\"2026-10-17\",\"text\":\"" + "가".repeat(201) + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("문장은 200자 이하로 보내 주세요"));
    verifyNoInteractions(cardParseService);
  }

  // ---- 사진으로 카드 만들기 ----

  private ResultActions cardsFromPhoto(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/ai/cards/from-photo").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  @DisplayName("사진으로 카드 만들기: 200 과 초안들 · 읽은 범위 한 줄, 메모의 앞뒤 공백은 지워서 넘긴다")
  void cardsFromPhotoReturnsDrafts() throws Exception {
    given(cardPhotoService.make(any()))
        .willReturn(
            new CardPhotoResponse(
                false,
                List.of(
                    new CardParseResponse.Draft(
                        LocalDate.of(2026, 10, 18),
                        null,
                        25,
                        true,
                        "수학 3단원 개념",
                        List.of("교과서 52~60쪽 읽기"),
                        "4")),
                "기록 이유",
                "수학 3~4단원"));
    cardsFromPhoto(
            "{\"image\":\"data:image/jpeg;base64,AQID\",\"date\":\"2026-10-18\",\"examDate\":\"2026-11-02\",\"note\":\" 수학만 \"}")
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"showHelp\":false,\"drafts\":[{\"date\":\"2026-10-18\",\"startTime\":null,\"durationMinutes\":25,\"durationFromRecord\":true,\"title\":\"수학 3단원 개념\",\"checklist\":[\"교과서 52~60쪽 읽기\"],\"goalId\":\"4\"}],\"durationEvidence\":\"기록 이유\",\"scopeText\":\"수학 3~4단원\"}",
                    true));
    verify(cardPhotoService)
        .make(
            new CardPhotoRequest(
                "data:image/jpeg;base64,AQID",
                LocalDate.of(2026, 10, 18),
                LocalDate.of(2026, 11, 2),
                "수학만"));
  }

  @Test
  @DisplayName("사진으로 카드 만들기: 사진 · 날짜가 없거나 메모가 100자를 넘으면 400 이고 서비스를 부르지 않는다")
  void cardsFromPhotoRejectsBadRequests() throws Exception {
    cardsFromPhoto("{\"date\":\"2026-10-18\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("사진을 보내 주세요"));
    cardsFromPhoto("{\"image\":\"data:image/jpeg;base64,AQID\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("기준 날짜를 보내 주세요"));
    cardsFromPhoto(
            "{\"image\":\"data:image/jpeg;base64,AQID\",\"date\":\"2026-10-18\",\"note\":\""
                + "가".repeat(101)
                + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("메모는 100자 이하로 보내 주세요"));
    verifyNoInteractions(cardPhotoService);
  }

  // ---- 사진으로 증빙 적기 ----

  private ResultActions proofFromPhoto(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/ai/proof-from-photo").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  @DisplayName("사진으로 증빙 적기: 200 과 한 줄, 카드 제목의 앞뒤 공백은 지워서 넘긴다")
  void proofFromPhotoReturnsSummary() throws Exception {
    given(proofPhotoService.summarize(any()))
        .willReturn(ProofPhotoResponse.of("수학 문제집 42~45쪽 풀고 틀린 3문제 표시함"));
    proofFromPhoto("{\"image\":\"data:image/jpeg;base64,AQID\",\"cardTitle\":\" 수학 문제집 \"}")
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json("{\"showHelp\":false,\"summary\":\"수학 문제집 42~45쪽 풀고 틀린 3문제 표시함\"}", true));
    verify(proofPhotoService)
        .summarize(new ProofPhotoRequest("data:image/jpeg;base64,AQID", "수학 문제집"));
  }

  @Test
  @DisplayName("사진으로 증빙 적기: 사진이 없거나 카드 제목이 100자를 넘으면 400 이고 서비스를 부르지 않는다")
  void proofFromPhotoRejectsBadRequests() throws Exception {
    proofFromPhoto("{\"cardTitle\":\"수학\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("사진을 보내 주세요"));
    proofFromPhoto(
            "{\"image\":\"data:image/jpeg;base64,AQID\",\"cardTitle\":\"" + "가".repeat(101) + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("카드 제목은 100자 이하로 보내 주세요"));
    verifyNoInteractions(proofPhotoService);
  }

  // ---- 카드 폼 기본값 ----

  @Test
  @DisplayName("카드 폼 기본값: 200 과 목표 · 유형 · 길이 · 이유, 제목이 비면 400")
  void cardDefaults() throws Exception {
    given(cardDefaultsService.suggest(any()))
        .willReturn(new CardDefaultsResponse(false, "4", "lecture", 60, List.of("이유")));
    mockMvc
        .perform(
            post("/api/v1/ai/card-defaults")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\" 수학 인강 \"}"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"showHelp\":false,\"goalId\":\"4\",\"type\":\"lecture\",\"durationMinutes\":60,\"evidence\":[\"이유\"]}",
                    true));
    verify(cardDefaultsService).suggest(new CardDefaultsRequest("수학 인강"));

    mockMvc
        .perform(
            post("/api/v1/ai/card-defaults")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("카드 제목을 입력해 주세요"));
  }

  // ---- 말로 설정 바꾸기 ----

  private ResultActions settingsFromText(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/ai/settings-from-text")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  @Test
  @DisplayName("말로 설정 바꾸기: 200 과 변경안(바뀌는 칸만), 못 만들면 proposal: null")
  void settingsFromTextSuggests() throws Exception {
    given(settingsFromTextService.suggest(any()))
        .willReturn(
            SettingsFromTextResponse.of(
                new SettingsFromTextResponse.Proposal(
                    null, null, null, "09:00", "연락 금지 끝을 09:00 으로 바꿔요")));
    settingsFromText("{\"text\":\"아침엔 알림 꺼 줘\"}")
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"showHelp\":false,\"proposal\":{\"callTime\":null,\"dailyCount\":null,"
                        + "\"quietStart\":null,\"quietEnd\":\"09:00\",\"summary\":\"연락 금지 끝을 09:00 으로 바꿔요\"}}",
                    true));

    given(settingsFromTextService.suggest(any())).willReturn(SettingsFromTextResponse.of(null));
    settingsFromText("{\"text\":\"수위 5로\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":false,\"proposal\":null}", true));
  }

  @Test
  @DisplayName("말로 설정 바꾸기: 부탁이 비었거나 100자를 넘으면 400 이고 서비스를 부르지 않는다")
  void settingsFromTextRejectsBadRequests() throws Exception {
    settingsFromText("{\"text\":\"\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("바꾸고 싶은 것을 적어 주세요"));
    settingsFromText("{\"text\":\"" + "가".repeat(101) + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("부탁은 100자 이하로 적어 주세요"));
    verifyNoInteractions(settingsFromTextService);
  }

  // ---- 성향 요약 3줄 ----

  @Test
  @DisplayName("성향 요약 3줄: 본문 없이 불러 200 과 3줄, 못 만들면 lines: null")
  void profileLines() throws Exception {
    given(profileLinesService.suggest())
        .willReturn(new ProfileLinesResponse(List.of("하나", "둘", "셋")));
    mockMvc
        .perform(post("/api/v1/ai/profile-lines"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"lines\":[\"하나\",\"둘\",\"셋\"]}", true));

    given(profileLinesService.suggest()).willReturn(new ProfileLinesResponse(null));
    mockMvc
        .perform(post("/api/v1/ai/profile-lines"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"lines\":null}", true));
  }

  // ---- AI 자기 분석 ----

  private ResultActions analysis(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/ai/analysis").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  // 숫자 하나의 JSON. 바꿀 칸만 넘깁니다.
  private static String fact(String key, String group, String label, String value, String basis) {
    return "{\"key\":\""
        + key
        + "\",\"group\":\""
        + group
        + "\",\"label\":\""
        + label
        + "\",\"value\":\""
        + value
        + "\",\"basis\":\""
        + basis
        + "\"}";
  }

  private void expectAnalysisRejected(String body, String message) throws Exception {
    analysis(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(selfAnalysisService);
  }

  @Test
  @DisplayName("AI 자기 분석: 숫자를 보내면 200 과 서버가 더한 숫자 · 묶음별 분석 · 실험 제안")
  void analysisAnswers() throws Exception {
    given(selfAnalysisService.analyze(any()))
        .willReturn(
            AnalysisResponse.of(
                List.of(
                    new AnalysisRequest.Fact(
                        "callHabit1",
                        "distortion",
                        "통화에서 자주 보인 생각 습관",
                        "계획 오류 · 최근 14일 중 3일",
                        "AI 통화 정리")),
                new AnalysisResponse.Analysis(
                    "아침에 더 잘 되는 편이야",
                    List.of(
                        new AnalysisResponse.Section(
                            "tendency",
                            "아침 카드가 더 잘 돼",
                            "아침 95%, 밤 63%야. 밤엔 지쳐 있을 수 있어.",
                            List.of("slotRate"))),
                    new AnalysisResponse.Experiment(
                        "만약 학원에서 돌아오면", "폰을 다른 방에 둔다", "놓친 날 원인 1위가 폰이었어"))));

    analysis(
            "{\"facts\":["
                + fact("slotRate", "tendency", "시간대별 실행률", "아침 95% · 밤 63%", "카드 64번")
                + "]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.showHelp").value(false))
        .andExpect(jsonPath("$.serverFacts[0].key").value("callHabit1"))
        .andExpect(jsonPath("$.analysis.summary").value("아침에 더 잘 되는 편이야"))
        .andExpect(jsonPath("$.analysis.sections[0].group").value("tendency"))
        .andExpect(jsonPath("$.analysis.sections[0].evidenceKeys[0]").value("slotRate"))
        .andExpect(jsonPath("$.analysis.nextExperiment.thenText").value("폰을 다른 방에 둔다"));

    given(selfAnalysisService.analyze(any())).willReturn(AnalysisResponse.of(List.of(), null));
    analysis("{\"facts\":[" + fact("slotRate", "tendency", "시간대별 실행률", "아침 95%", "카드 64번") + "]}")
        .andExpect(status().isOk())
        .andExpect(
            content().json("{\"showHelp\":false,\"serverFacts\":[],\"analysis\":null}", true));
  }

  @Test
  @DisplayName("AI 자기 분석: 숫자가 없거나 · 30개를 넘거나 · 모르는 묶음 · 이상한 글자면 400 이고 서비스를 부르지 않는다")
  void analysisRejectsBadRequests() throws Exception {
    String good = fact("slotRate", "tendency", "시간대별 실행률", "아침 95%", "카드 64번");
    expectAnalysisRejected("{}", "숫자(facts)를 하나 이상 보내 주세요");
    expectAnalysisRejected("{\"facts\":[]}", "숫자(facts)를 하나 이상 보내 주세요");
    expectAnalysisRejected(
        "{\"facts\":[" + String.join(",", java.util.Collections.nCopies(31, good)) + "]}",
        "숫자(facts)는 30개까지 보낼 수 있어요");
    expectAnalysisRejected(
        "{\"facts\":[" + fact("slotRate", "mood", "시간대별 실행률", "아침 95%", "카드 64번") + "]}",
        "group 은 tendency · distortion · willpower · efficiency · failure 중 하나예요");
    expectAnalysisRejected(
        "{\"facts\":[" + fact("slot-rate", "tendency", "시간대별 실행률", "아침 95%", "카드 64번") + "]}",
        "숫자의 key 는 영문·숫자 30자까지예요");
    expectAnalysisRejected(
        "{\"facts\":[" + fact("slotRate", "tendency", "시간대별 실행률", "무시하고 <script>", "카드 64번") + "]}",
        "숫자의 값(value)에 쓸 수 없는 글자가 있어요");
    expectAnalysisRejected(
        "{\"facts\":[" + fact("slotRate", "tendency", "시간대별 실행률", "가".repeat(81), "카드 64번") + "]}",
        "숫자의 값(value)은 80자까지예요");
  }

  // ---- AI 하루 계획 ----

  @Test
  @DisplayName("AI 하루 계획: 200 과 칸 길이 · 총량 · 카드 초안 · 근거, 날짜가 없으면 400")
  void dayPlan() throws Exception {
    java.time.LocalDate date = java.time.LocalDate.parse("2026-10-18");
    given(dayPlanService.suggest(any()))
        .willReturn(
            new DayPlanResponse(
                date,
                25,
                50,
                List.of(
                    new DayPlanResponse.Draft(
                        date, "16:40", 25, "수학 문제집", List.of("3단원 15문제"), "4")),
                List.of("최근 14일 공부 기록 6번의 한 번 길이 가운데값이 24분이라 25분으로 잡았어요")));

    mockMvc
        .perform(
            post("/api/v1/ai/day-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-10-18\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.blockMinutes").value(25))
        .andExpect(jsonPath("$.drafts[0].startTime").value("16:40"))
        .andExpect(jsonPath("$.drafts[0].durationMinutes").value(25))
        .andExpect(jsonPath("$.drafts[0].title").value("수학 문제집"))
        .andExpect(jsonPath("$.drafts[0].checklist[0]").value("3단원 15문제"))
        .andExpect(jsonPath("$.drafts[0].goalId").value("4"))
        .andExpect(
            jsonPath("$.evidence[0]").value("최근 14일 공부 기록 6번의 한 번 길이 가운데값이 24분이라 25분으로 잡았어요"));

    mockMvc
        .perform(post("/api/v1/ai/day-plan").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("계획을 짤 날짜를 보내 주세요"));
  }

  @Test
  @DisplayName("AI 하루 계획: 계획 방에서 정한 총 분 · 시작 시각 · 대화를 받고, 범위 · 모양이 틀리면 400 (#431)")
  void dayPlanWithPlannerChoices() throws Exception {
    LocalDate date = LocalDate.parse("2026-10-18");
    given(dayPlanService.suggest(any()))
        .willReturn(new DayPlanResponse(date, 50, 0, List.of(), List.of("그날은 카드를 넣을 빈 시간이 없어요")));

    mockMvc
        .perform(
            post("/api/v1/ai/day-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"date\":\"2026-10-18\",\"totalMinutes\":120,\"startTime\":\"21:00\","
                        + "\"planTalk\":[\"나: 오늘 2시간만, 수학 먼저\"]}"))
        .andExpect(status().isOk());
    ArgumentCaptor<DayPlanRequest> sent = ArgumentCaptor.forClass(DayPlanRequest.class);
    verify(dayPlanService).suggest(sent.capture());
    assertThat(sent.getValue().totalMinutes()).isEqualTo(120);
    assertThat(sent.getValue().startTime()).isEqualTo("21:00");
    assertThat(sent.getValue().planTalk()).containsExactly("나: 오늘 2시간만, 수학 먼저");

    mockMvc
        .perform(
            post("/api/v1/ai/day-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-10-18\",\"totalMinutes\":10}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error.message").value("하루 총 공부 분(totalMinutes)은 20 ~ 600 으로 보내 주세요"));
    mockMvc
        .perform(
            post("/api/v1/ai/day-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-10-18\",\"startTime\":\"25:00\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("시작 시각(startTime)은 HH:mm 으로 보내 주세요"));
    mockMvc
        .perform(
            post("/api/v1/ai/day-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"date\":\"2026-10-18\",\"planTalk\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\",\"9\"]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("계획 방 대화(planTalk)는 8줄까지 보낼 수 있어요"));
  }

  // ---- AI 오늘의 미션 ----

  @Test
  @DisplayName("AI 오늘의 미션: 200 과 미션 목록, 날짜가 없거나 준비하는 것의 종류가 틀리면 400")
  void dailyMissions() throws Exception {
    given(dailyMissionsService.suggest(any()))
        .willReturn(
            DailyMissionsResponse.of(
                List.of(new DailyMissions.Mission("수능 기출 미적분 3문제", 15, "미적분Ⅰ"))));

    mockMvc
        .perform(
            post("/api/v1/ai/daily-missions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"date\":\"2026-10-19\",\"focus\":{\"kind\":\"SUNEUNG\"},"
                        + "\"subjects\":[\"미적분Ⅰ\"],\"exam\":{\"name\":\"정기시험\",\"dDay\":14},\"condition\":3}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.showHelp").value(false))
        .andExpect(jsonPath("$.missions[0].title").value("수능 기출 미적분 3문제"))
        .andExpect(jsonPath("$.missions[0].minutes").value(15))
        .andExpect(jsonPath("$.missions[0].subject").value("미적분Ⅰ"));

    mockMvc
        .perform(
            post("/api/v1/ai/daily-missions").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("날짜를 보내 주세요"));
    mockMvc
        .perform(
            post("/api/v1/ai/daily-missions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-10-19\",\"focus\":{\"kind\":\"GAME\"}}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error.message")
                .value("준비하는 것은 SCHOOL_EXAM · SUNEUNG · CONTEST · OTHER 중 하나예요"));
  }

  // ---- 계획 핑퐁 ----

  @Test
  @DisplayName("계획 핑퐁: 200 과 코치의 한 줄 · 고친 계획, 요청이 없으면 400")
  void reviseDayPlan() throws Exception {
    given(dayPlanReviseService.revise(any()))
        .willReturn(
            DayPlanReviseResponse.of(
                new DayPlanRevise.Result(
                    "영어는 뺐어.",
                    List.of(
                        new DayPlanRevise.Card(
                            java.time.LocalDate.parse("2026-10-19"),
                            "19:00",
                            30,
                            "미적분 문제집",
                            List.of("3단원 10문제"))))));
    String drafts =
        "\"drafts\":[{\"date\":\"2026-10-19\",\"startTime\":\"19:00\",\"durationMinutes\":30,"
            + "\"title\":\"미적분 문제집\",\"checklist\":[\"3단원 10문제\"]}]";

    mockMvc
        .perform(
            post("/api/v1/ai/day-plan/revise")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + drafts + ",\"request\":\"영어는 빼 줘\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reply").value("영어는 뺐어."))
        .andExpect(jsonPath("$.drafts[0].startTime").value("19:00"))
        .andExpect(jsonPath("$.drafts[0].durationMinutes").value(30));

    mockMvc
        .perform(
            post("/api/v1/ai/day-plan/revise")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + drafts + "}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("어떻게 고칠지 적어 주세요"));

    // 계획 방 대화(planTalk, #431)는 한 줄 200자까지
    mockMvc
        .perform(
            post("/api/v1/ai/day-plan/revise")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{"
                        + drafts
                        + ",\"request\":\"줄여 줘\",\"planTalk\":[\""
                        + "가".repeat(201)
                        + "\"]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("계획 방 대화 한 줄은 200자 이하로 보내 주세요"));
  }

  // ---- 확인 문제 ----

  @Test
  @DisplayName("확인 문제: 200 과 문제 · 보기 · 정답 위치 · 해설, 앞뒤 공백을 지우고 문제 수는 기본 3으로 넘긴다")
  void quizReturnsQuestions() throws Exception {
    given(studyQuizService.make(any()))
        .willReturn(
            StudyQuizResponse.of(
                List.of(
                    new StudyQuiz.Question(
                        "lim (n→∞) 1/n 의 값은?", List.of("0", "1", "∞", "없다"), 0, "0 에 가까워져.", null)),
                "함수"));

    mockMvc
        .perform(
            post("/api/v1/ai/quiz")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"topic\":\" 수열의 극한 \",\"material\":\" \"}"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"showHelp\":false,\"questions\":[{\"question\":\"lim (n→∞) 1/n 의 값은?\",\"choices\":[\"0\",\"1\",\"∞\",\"없다\"],\"answerIndex\":0,\"explanation\":\"0 에 가까워져.\",\"passage\":null}],\"unit\":\"함수\",\"reason\":null}",
                    true));
    verify(studyQuizService).make(new StudyQuizRequest("수열의 극한", List.of(), null, null, 3));
  }

  @Test
  @DisplayName("확인 문제: 주제가 없거나 · 문제 수가 1~5 밖이거나 · 자료 글이 2000자를 넘으면 400 이고 서비스를 부르지 않는다")
  void quizRejectsBadRequests() throws Exception {
    mockMvc
        .perform(post("/api/v1/ai/quiz").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("무엇을 공부했는지 보내 주세요"));
    mockMvc
        .perform(
            post("/api/v1/ai/quiz")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"topic\":\"수학\",\"count\":6}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("문제 수는 1 ~ 5 로 보내 주세요"));
    mockMvc
        .perform(
            post("/api/v1/ai/quiz")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"topic\":\"수학\",\"material\":\"" + "가".repeat(2001) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("자료 글은 2000자 이하로 보내 주세요"));
    verifyNoInteractions(studyQuizService);
  }

  // ---- 긴 계획(30일) ----

  @Test
  @DisplayName("긴 계획: 200 과 뼈대 · 날짜별 카드, 목표가 없거나 모든 요일을 쉬면 400")
  void longPlan() throws Exception {
    LongPlanOutline plan =
        new LongPlanOutline(
            "중간고사 미적분",
            java.time.LocalDate.parse("2026-10-19"),
            java.time.LocalDate.parse("2026-11-02"),
            60,
            "19:00",
            List.of("SUN"),
            List.of(new LongPlanOutline.Topic("미적분 1단원 개념", 90, List.of("교과서 읽기"))));
    given(longPlanService.make(any()))
        .willReturn(
            LongPlanResponse.of(
                "개념부터 나눴어.",
                plan,
                List.of(
                    new DayPlanRevise.Card(
                        java.time.LocalDate.parse("2026-10-19"),
                        "19:00",
                        60,
                        "미적분 1단원 개념 (1/2)",
                        List.of("교과서 읽기"))),
                false));

    mockMvc
        .perform(
            post("/api/v1/ai/long-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"goal\":\"중간고사 미적분\",\"endDate\":\"2026-11-02\",\"restDays\":[\"SUN\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reply").value("개념부터 나눴어."))
        .andExpect(jsonPath("$.plan.restDays[0]").value("SUN"))
        .andExpect(jsonPath("$.plan.topics[0].minutes").value(90))
        .andExpect(jsonPath("$.drafts[0].title").value("미적분 1단원 개념 (1/2)"))
        .andExpect(jsonPath("$.overflow").value(false));

    mockMvc
        .perform(
            post("/api/v1/ai/long-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endDate\":\"2026-11-02\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("무엇을 준비하는지 적어 주세요"));

    mockMvc
        .perform(
            post("/api/v1/ai/long-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"goal\":\"중간고사\",\"endDate\":\"2026-11-02\","
                        + "\"restDays\":[\"MON\",\"TUE\",\"WED\",\"THU\",\"FRI\",\"SAT\",\"SUN\"]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("모든 요일을 쉴 수는 없어요"));
  }

  @Test
  @DisplayName("긴 계획 고치기: 200 과 고친 뼈대, 요청이 없거나 할 일 시간이 틀리면 400")
  void reviseLongPlan() throws Exception {
    given(longPlanService.revise(any())).willReturn(LongPlanResponse.notMade());
    String plan =
        "\"plan\":{\"goal\":\"중간고사\",\"startDate\":\"2026-10-19\",\"endDate\":\"2026-11-02\","
            + "\"dailyMinutes\":60,\"startTime\":\"19:00\",\"restDays\":[],"
            + "\"topics\":[{\"title\":\"미적분 1단원\",\"minutes\":90,\"checklist\":[]}]}";

    mockMvc
        .perform(
            post("/api/v1/ai/long-plan/revise")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + plan + ",\"request\":\"주말은 빼 줘\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.plan").doesNotExist())
        .andExpect(jsonPath("$.showHelp").value(false));

    mockMvc
        .perform(
            post("/api/v1/ai/long-plan/revise")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + plan + "}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("어떻게 고칠지 적어 주세요"));

    mockMvc
        .perform(
            post("/api/v1/ai/long-plan/revise")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + plan.replace("90", "500") + ",\"request\":\"줄여 줘\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("할 일 시간은 30 ~ 240분이에요"));
  }

  // ---- 코치 알림 문장 ----

  @Test
  @DisplayName("코치 알림 문장: 200 과 아침 · 밤 문장, 과목 · 카드 제목의 앞뒤 공백을 지우고 안 보낸 목록은 빈 목록으로 넘긴다")
  void nudgesReturnsTexts() throws Exception {
    given(coachNudgeService.make(any()))
        .willReturn(
            CoachNudgeResponse.of(
                new CoachNudge.Result(
                    new CoachNudge.Nudge("오늘 6교시 미적분", "쉬는 시간에 한 문제만 보자."), null)));

    mockMvc
        .perform(
            post("/api/v1/ai/nudges")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"date\":\"2026-10-19\",\"subjects\":[\" 미적분 \"],"
                        + "\"cards\":[{\"startTime\":\"19:00\",\"title\":\" 문제집 \",\"minutes\":40}]}"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"morning\":{\"title\":\"오늘 6교시 미적분\",\"body\":\"쉬는 시간에 한 문제만 보자.\"},"
                        + "\"evening\":null,\"comfort\":false}",
                    true));
    verify(coachNudgeService)
        .make(
            new CoachNudgeRequest(
                LocalDate.of(2026, 10, 19),
                List.of("미적분"),
                List.of(new CoachNudgeRequest.Card("19:00", "문제집", 40)),
                null,
                List.of(),
                null,
                null));
  }

  @Test
  @DisplayName("코치 알림 문장: 날짜가 없거나 · 과목이 8개를 넘거나 · 전날 숫자가 범위 밖이면 400 이고 서비스를 부르지 않는다")
  void nudgesRejectsBadRequests() throws Exception {
    mockMvc
        .perform(post("/api/v1/ai/nudges").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("알림을 보낼 날짜를 보내 주세요"));
    mockMvc
        .perform(
            post("/api/v1/ai/nudges")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"date\":\"2026-10-19\",\"subjects\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\",\"9\"]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("과목은 8개까지 보낼 수 있어요"));
    mockMvc
        .perform(
            post("/api/v1/ai/nudges")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"date\":\"2026-10-19\",\"yesterday\":{\"plannedCards\":3,\"doneCards\":21,"
                        + "\"studyMinutes\":60}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("전날 끝낸 카드 수는 0 ~ 20 이에요"));
    verifyNoInteractions(coachNudgeService);
  }
}
