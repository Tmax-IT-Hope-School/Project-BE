package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.AnalysisRequest;
import com.ithopeschool.projectbe.domain.ai.dto.AnalysisResponse;
import com.ithopeschool.projectbe.domain.ai.dto.CardDefaultsRequest;
import com.ithopeschool.projectbe.domain.ai.dto.CardDefaultsResponse;
import com.ithopeschool.projectbe.domain.ai.dto.CardParseRequest;
import com.ithopeschool.projectbe.domain.ai.dto.CardParseResponse;
import com.ithopeschool.projectbe.domain.ai.dto.CardPhotoRequest;
import com.ithopeschool.projectbe.domain.ai.dto.CardPhotoResponse;
import com.ithopeschool.projectbe.domain.ai.dto.ChecklistRequest;
import com.ithopeschool.projectbe.domain.ai.dto.ChecklistResponse;
import com.ithopeschool.projectbe.domain.ai.dto.CoachNudgeRequest;
import com.ithopeschool.projectbe.domain.ai.dto.CoachNudgeResponse;
import com.ithopeschool.projectbe.domain.ai.dto.DailyMissionsRequest;
import com.ithopeschool.projectbe.domain.ai.dto.DailyMissionsResponse;
import com.ithopeschool.projectbe.domain.ai.dto.DayPlanRequest;
import com.ithopeschool.projectbe.domain.ai.dto.DayPlanResponse;
import com.ithopeschool.projectbe.domain.ai.dto.DayPlanReviseRequest;
import com.ithopeschool.projectbe.domain.ai.dto.DayPlanReviseResponse;
import com.ithopeschool.projectbe.domain.ai.dto.ExamNextStepRequest;
import com.ithopeschool.projectbe.domain.ai.dto.ExamNextStepResponse;
import com.ithopeschool.projectbe.domain.ai.dto.FailCauseRequest;
import com.ithopeschool.projectbe.domain.ai.dto.FailCauseResponse;
import com.ithopeschool.projectbe.domain.ai.dto.FirstStepRequest;
import com.ithopeschool.projectbe.domain.ai.dto.FirstStepResponse;
import com.ithopeschool.projectbe.domain.ai.dto.LongPlanRequest;
import com.ithopeschool.projectbe.domain.ai.dto.LongPlanResponse;
import com.ithopeschool.projectbe.domain.ai.dto.LongPlanReviseRequest;
import com.ithopeschool.projectbe.domain.ai.dto.ProfileLinesResponse;
import com.ithopeschool.projectbe.domain.ai.dto.ProofPhotoRequest;
import com.ithopeschool.projectbe.domain.ai.dto.ProofPhotoResponse;
import com.ithopeschool.projectbe.domain.ai.dto.QuickCardRequest;
import com.ithopeschool.projectbe.domain.ai.dto.QuickCardResponse;
import com.ithopeschool.projectbe.domain.ai.dto.RoutineCardRequest;
import com.ithopeschool.projectbe.domain.ai.dto.RoutineCardResponse;
import com.ithopeschool.projectbe.domain.ai.dto.SettingsFromTextRequest;
import com.ithopeschool.projectbe.domain.ai.dto.SettingsFromTextResponse;
import com.ithopeschool.projectbe.domain.ai.dto.StudyQuizRequest;
import com.ithopeschool.projectbe.domain.ai.dto.StudyQuizResponse;
import com.ithopeschool.projectbe.domain.ai.dto.TextLabelRequest;
import com.ithopeschool.projectbe.domain.ai.dto.TextLabelResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 작은 AI 기능(입력 칸을 AI 가 먼저 채우기)의 입구입니다. 요청을 받아 Service 를 부르기만 합니다. (이슈 #236 · #237)
// 모두 저장하지 않고 제안만 돌려주므로 200 입니다. AI 가 못 만들면 에러 대신 빈 칸(null)이고, FE 가 기본값을 씁니다.
// 로그인이 필요합니다. (docs/API.md "AI 도우미")
@Tag(name = "AI 도우미")
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

  private final ChecklistService checklistService;
  private final FirstStepService firstStepService;
  private final FailCauseService failCauseService;
  private final ExamNextStepService examNextStepService;
  private final TextLabelService textLabelService;
  private final RoutineCardService routineCardService;
  private final QuickCardService quickCardService;
  private final SettingsFromTextService settingsFromTextService;
  private final ProfileLinesService profileLinesService;
  private final CardParseService cardParseService;
  private final CardPhotoService cardPhotoService;
  private final ProofPhotoService proofPhotoService;
  private final CardDefaultsService cardDefaultsService;
  private final SelfAnalysisService selfAnalysisService;
  private final DayPlanService dayPlanService;
  private final DailyMissionsService dailyMissionsService;
  private final DayPlanReviseService dayPlanReviseService;
  private final LongPlanService longPlanService;
  private final StudyQuizService studyQuizService;
  private final CoachNudgeService coachNudgeService;

  @Operation(
      summary = "카드 체크리스트 추천: 카드 제목 → 항목 3~6개 (저장 안 함 · 내 지난 기록을 봄 · 못 만들면 items: null · 토큰 필요)")
  @PostMapping("/checklist")
  public ChecklistResponse checklist(@Valid @RequestBody ChecklistRequest request) {
    return checklistService.suggest(request);
  }

  @Operation(
      summary =
          "첫 준비 행동: 체크리스트 첫 항목 → 아주 작은 행동 하나 ([더 작게] 는 level 1·2 · 저장 안 함 · 못 만들면 step: null · 토큰 필요)")
  @PostMapping("/first-step")
  public FirstStepResponse firstStep(@Valid @RequestBody FirstStepRequest request) {
    return firstStepService.suggest(request);
  }

  @Operation(
      summary =
          "예상 실패 원인 선지: 카드 제목 → 선지 3개 (내 최근 30일 실패 원인으로 고름 · 내 행동 원인 1개 이상 · 기록이 모자라면 options: null · 토큰 필요)")
  @PostMapping("/fail-cause-options")
  public FailCauseResponse failCauseOptions(@Valid @RequestBody FailCauseRequest request) {
    return failCauseService.suggest(request);
  }

  @Operation(
      summary =
          "시험 회고 → 다음 카드에 넣을 한 가지 (저장된 회고·점수를 봄 · 과목은 예상보다 가장 아쉬운 것 · 못 만들면 nextStep: null · 토큰 필요)")
  @PostMapping("/exam-next-step")
  public ExamNextStepResponse examNextStep(@Valid @RequestBody ExamNextStepRequest request) {
    return examNextStepService.suggest(request);
  }

  @Operation(
      summary =
          "글에 이름표 붙이기: '기타' 사유 → 내 행동/바깥 일, 딴생각 메모 → 연락·다른과제·걱정·생활 (참고 칸 · 못 붙이면 label: null · 토큰 필요)")
  @PostMapping("/classify-text")
  public TextLabelResponse textLabel(@Valid @RequestBody TextLabelRequest request) {
    return textLabelService.label(request);
  }

  @Operation(
      summary =
          "루틴 카드 제안: 목표 + 방해물 + 대처 → 루틴 카드 2~3장 (요일·시각·길이는 코드가 정함 · 저장 안 함 · 못 만들면 cards: null · 토큰 필요)")
  @PostMapping("/routine-cards")
  public RoutineCardResponse routineCards(@Valid @RequestBody RoutineCardRequest request) {
    return routineCardService.suggest(request);
  }

  @Operation(
      summary =
          "즉석 카드: 한 줄 → 카드 제목 + 체크리스트 2~4개 (평소 쓰던 제목으로 맞춤 · 저장 안 함 · 못 만들면 card: null · 토큰 필요)")
  @PostMapping("/quick-card")
  public QuickCardResponse quickCard(@Valid @RequestBody QuickCardRequest request) {
    return quickCardService.suggest(request);
  }

  @Operation(
      summary =
          "말로 카드 만들기: 문장 하나 → 카드 초안 여러 장 (날짜 · 시각 · 길이 · 체크리스트 · 목표, 길이를 안 말하면 내 기록의 한 칸 길이 · 저장 안 함 · 못 만들면 drafts: null · 토큰 필요)")
  @PostMapping("/cards/parse")
  public CardParseResponse parseCards(@Valid @RequestBody CardParseRequest request) {
    return cardParseService.parse(request);
  }

  @Operation(
      summary =
          "사진으로 카드 만들기: 시험 범위표 · 학습지 사진 → 시험 전날까지 나눈 카드 초안 (image 는 data URL · 4MB 이하 · 사진도 카드도 저장 안 함 · 못 만들면 drafts: null · 토큰 필요)")
  @PostMapping("/cards/from-photo")
  public CardPhotoResponse cardsFromPhoto(@Valid @RequestBody CardPhotoRequest request) {
    return cardPhotoService.make(request);
  }

  @Operation(
      summary =
          "사진으로 증빙 적기: 공부한 사진 한 장 → 무엇을 했는지 한 줄 (image 는 data URL · 4MB 이하 · 사진 저장 안 함 · 공부 흔적이 안 보이면 summary: null · 토큰 필요)")
  @PostMapping("/proof-from-photo")
  public ProofPhotoResponse proofFromPhoto(@Valid @RequestBody ProofPhotoRequest request) {
    return proofPhotoService.summarize(request);
  }

  @Operation(
      summary =
          "카드 폼 기본값: 제목 → 목표 연결 · 유형 · 길이 (같은 제목 카드가 있으면 그대로 · 처음이면 AI 가 목표 · 유형, 길이는 내 기록 · 저장 안 함 · 토큰 필요)")
  @PostMapping("/card-defaults")
  public CardDefaultsResponse cardDefaults(@Valid @RequestBody CardDefaultsRequest request) {
    return cardDefaultsService.suggest(request);
  }

  @Operation(
      summary =
          "말로 설정 바꾸기: 부탁 한 줄 → 관리 설정 변경안 (바꾸지 않음 · 전화 시각·메시지 수·연락 금지만 · 수위·한도·잠금은 못 바꿈 · 토큰 필요)")
  @PostMapping("/settings-from-text")
  public SettingsFromTextResponse settingsFromText(
      @Valid @RequestBody SettingsFromTextRequest request) {
    return settingsFromTextService.suggest(request);
  }

  @Operation(
      summary =
          "성향 요약 3줄: 내 가장 최근 심리검사에서 두드러진 척도 3개를 AI 가 한 줄씩 (본문 없음 · 저장 안 함 · 못 만들면 lines: null · 토큰 필요)")
  @PostMapping("/profile-lines")
  public ProfileLinesResponse profileLines() {
    return profileLinesService.suggest();
  }

  @Operation(
      summary =
          "AI 자기 분석: 기록에서 계산한 숫자 → 성향 · 생각과 실제 · 약속 지키기 · 공부 효율 · 실패 원인 문단과 다음 실험 제안 (통화 생각 습관은 서버가 더함 · 저장 안 함 · 못 만들면 analysis: null · 토큰 필요)")
  @PostMapping("/analysis")
  public AnalysisResponse analysis(@Valid @RequestBody AnalysisRequest request) {
    return selfAnalysisService.analyze(request);
  }

  @Operation(summary = "AI 하루 계획: 내 기록으로 한 칸 길이·총량·과목을 정하고 AI 가 할 일을 씀 (근거 문장 포함 · 저장 안 함 · 토큰 필요)")
  @PostMapping("/day-plan")
  public DayPlanResponse dayPlan(@Valid @RequestBody DayPlanRequest request) {
    return dayPlanService.suggest(request);
  }

  @Operation(
      summary =
          "AI 오늘의 미션: 오늘 과목 · 가까운 시험 · 준비하는 것(내신·수능·대회·기타) · 컨디션으로 공부 미션 1~3개 (저장 안 함 · 못 만들면 missions: null · 토큰 필요)")
  @PostMapping("/daily-missions")
  public DailyMissionsResponse dailyMissions(@Valid @RequestBody DailyMissionsRequest request) {
    return dailyMissionsService.suggest(request);
  }

  @Operation(
      summary =
          "계획 핑퐁: 지금 계획(카드 초안)과 학생의 요청 한 줄로 AI 가 계획을 고침 (시각 · 길이 · 겹침은 서버가 검사 · 저장 안 함 · 못 고치면 drafts: null · 토큰 필요)")
  @PostMapping("/day-plan/revise")
  public DayPlanReviseResponse reviseDayPlan(@Valid @RequestBody DayPlanReviseRequest request) {
    return dayPlanReviseService.revise(request);
  }

  @Operation(
      summary =
          "긴 계획(30일): 목표 한 줄(과 교과서 · 목차 · 시험 범위 사진)로 AI 가 할 일 목록을 쓰고, 날짜 · 시각은 서버 코드가 나눔 (사진 · 계획 저장 안 함 · 못 만들면 plan: null · 토큰 필요)")
  @PostMapping("/long-plan")
  public LongPlanResponse longPlan(@Valid @RequestBody LongPlanRequest request) {
    return longPlanService.make(request);
  }

  @Operation(
      summary =
          "긴 계획 고치기: 지금 계획의 뼈대와 학생의 요청 한 줄로 AI 가 바꿀 칸(하루 시간 · 시작 시각 · 쉬는 요일 · 시험 날 · 할 일)만 정하고 서버 코드가 다시 나눔 (저장 안 함 · 못 고치면 plan: null · 토큰 필요)")
  @PostMapping("/long-plan/revise")
  public LongPlanResponse reviseLongPlan(@Valid @RequestBody LongPlanReviseRequest request) {
    return longPlanService.revise(request);
  }

  @Operation(
      summary =
          "확인 문제: 공부한 주제 · 자료 글 · 교과서 사진으로 4지선다 1~5문제 (정답 · 해설 포함, 채점은 FE · 저장 안 함 · 못 만들면 questions: null · 토큰 필요)")
  @PostMapping("/quiz")
  public StudyQuizResponse quiz(@Valid @RequestBody StudyQuizRequest request) {
    return studyQuizService.make(request);
  }

  @Operation(
      summary =
          "코치 알림 문장: 그날 시간표 · 계획 카드 · 전날 기록 · 틀린 문제 · 시험으로 아침 · 밤 알림 문장을 AI 가 미리 씀 (숫자는 입력에 있는 것만 · 저장 안 함 · 못 만든 칸은 null · 토큰 필요)")
  @PostMapping("/nudges")
  public CoachNudgeResponse nudges(@Valid @RequestBody CoachNudgeRequest request) {
    return coachNudgeService.make(request);
  }
}
