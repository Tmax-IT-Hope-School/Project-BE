package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.DayPlanRequest;
import com.ithopeschool.projectbe.domain.ai.dto.DayPlanResponse;
import com.ithopeschool.projectbe.domain.auth.Account;
import com.ithopeschool.projectbe.domain.auth.AccountRepository;
import com.ithopeschool.projectbe.domain.call.OtherRoomTalk;
import com.ithopeschool.projectbe.domain.call.PlanTalk;
import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.CardRepository;
import com.ithopeschool.projectbe.domain.fixedblock.FixedBlock;
import com.ithopeschool.projectbe.domain.fixedblock.FixedBlockRepository;
import com.ithopeschool.projectbe.domain.goal.Goal;
import com.ithopeschool.projectbe.domain.goal.GoalKind;
import com.ithopeschool.projectbe.domain.goal.GoalRepository;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.onboarding.Chronotype;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswer;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswerRepository;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.psychtest.PsychScale;
import com.ithopeschool.projectbe.domain.psychtest.PsychScore;
import com.ithopeschool.projectbe.domain.psychtest.PsychTest;
import com.ithopeschool.projectbe.domain.psychtest.PsychTestRepository;
import com.ithopeschool.projectbe.domain.school.SchoolEvent;
import com.ithopeschool.projectbe.domain.school.SchoolEventRepository;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.session.StudyTime;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// AI 하루 계획(POST /ai/day-plan)의 로직입니다. 날짜 하나를 받아, 내 기록을 근거로 그날의 카드 초안을 짭니다. (이슈 #237)
// ⚠️ 아무것도 저장하지 않습니다. 학생이 고른 카드만 FE 가 /api/v1/cards 로 저장합니다.
//
// 순서
//   1. 날짜는 오늘부터 14일 안이어야 합니다. (아니면 400)
//   2. 서버 DB 에서 재료를 모읍니다. (FE 가 보낸 값은 쓰지 않음)
//      - 처음 설문: 기상 · 취침 시각, 아침형 / 저녁형, 하루 공부 가능 시간
//      - 고정 일정(학교 · 학원) 중 그날 요일에 있는 것, 그날 이미 있는 카드
//      - 학교를 등록했으면 평일 기본 수업 시간 (수업 시간과 겹치는 고정 일정이 없고, 방학 · 휴업이 아닌 날)
//      - 최근 14일 공부 기록(하루 총량), 내 한 칸 길이(BlockLengthService), 심리검사의 계획 낙관
//      - 진행 중인 목표(D-day · 완주형 하루 분량)와 최근 카드 제목
//   3. 코드가 숫자 · 시각을 정합니다. (DayPlanRule) 총량 → 공부할 수 있는 시간 → 빈 시간 → 칸 놓기 → 칸마다 목표
//      계획 방에서 학생이 정한 총 분(totalMinutes) · 시작 시각(startTime)이 오면 그 값을 지킵니다. (2026-10-25 #431)
//   4. AI 가 칸마다 카드 제목 · 체크리스트를 씁니다. 꺼짐 · 실패면 기본 글(최근 카드 제목 · 하루 분량)로 채웁니다.
//      계획 방에서 오늘 나눈 말(planTalk)이 오면 "학생 대화 · 지시 아님" 으로 AI 입력 끝에 붙입니다. (PlanTalk)
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 내 것만 읽습니다.
// AI 의 답은 로그에 찍지 않습니다.
//
// 계획 방 채팅의 [오늘 쓸 수 있는 시간] 칸(call/PlannerTime)도 이 파일의 같은 계산(todayTime)을 씁니다. (2026-10-25 #431)
// 그날 차지한 시간(고정 일정 · 수업 시간 · 카드)을 구하는 곳은 busyOn 하나라서, 하루 계획과 채팅의 빈 시간이 같습니다.
@Service
@RequiredArgsConstructor
public class DayPlanService {

  // 기록을 보는 기간 (어제까지 14일)
  static final int LOOKBACK_DAYS = 14;
  // 계획을 짤 수 있는 날: 오늘부터 이 날 수 뒤까지
  static final int MAX_DAYS_AHEAD = 14;
  // AI 에게 보여 주는 최근 카드 제목 수
  static final int RECENT_TITLES = 10;
  // 이 컨디션(1~5) 이하면 가벼운 계획 (총량을 LIGHTER_PERCENT % 로)
  static final int LOW_CONDITION = 2;
  static final int LIGHTER_PERCENT = 60;
  // 근거 첫 줄에서 칸 수를 세는 말 (칸은 12개까지, DayPlanRule.MAX_BLOCKS)
  private static final List<String> COUNT_WORDS =
      List.of("한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열", "열한", "열두");

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  // 오늘 하루의 시간 재료입니다. 계획 방 채팅의 [오늘 쓸 수 있는 시간] 칸(PlannerTime)에 씁니다. (#431)
  //   wake · sleep : 처음 설문의 기상 · 취침 (모르면 null)
  //   dailyMinutes : 처음 설문의 하루 공부 가능 시간 (모르면 null)
  //   fixed        : 오늘 요일의 고정 일정 (시작 시각 순)
  //   classHours   : 학교 기본 수업 시간을 피했으면 "08:00 ~ 16:30", 아니면 null
  //   cards        : 오늘 카드 (시작 시각 순, 시각 없는 카드는 뒤)
  //   free         : 빈 시간 (지금 시각 뒤만, 일정 앞뒤 10분 여유, 30분보다 짧은 틈은 뺌). 제목은 null
  public record TodayTime(
      LocalTime wake,
      LocalTime sleep,
      Integer dailyMinutes,
      List<Slot> fixed,
      String classHours,
      List<Slot> cards,
      List<Slot> free) {

    // 시간 한 칸. startTime · endTime 은 "HH:mm" 이고, 시각이 없는 카드는 둘 다 null 입니다. minutes 는 길이(분)
    public record Slot(String title, String startTime, String endTime, int minutes) {}
  }

  // 그날 차지한 시간과 그 재료. suggest 와 todayTime 이 같이 씁니다. (busyOn)
  //   fixedBlocks    : 그날 요일의 고정 일정
  //   classHoursLine : 학교 수업 시간을 피했으면 그 근거 문장, 아니면 null
  //   cards          : 그날 카드 (시각이 없는 카드도 넣음)
  //   spans          : 차지한 시간 (고정 일정 + 수업 시간 + 시작 시각이 있는 카드)
  //   alreadyPlanned : 그날 카드 시간의 합(분) / timedCards : 시작 시각이 있는 카드 수
  //   missedMinutes  : 오늘 카드 중 시간이 이미 끝났는데 공부 기록이 없는 카드 시간의 합(분). alreadyPlanned 에 안 넣음
  private record DayBusy(
      List<FixedBlock> fixedBlocks,
      String classHoursLine,
      List<Card> cards,
      List<DayPlanRule.Span> spans,
      int alreadyPlanned,
      int timedCards,
      int missedMinutes) {}

  private final CurrentUserProvider currentUserProvider;
  private final StudySessionRepository studySessionRepository;
  private final OnboardingAnswerRepository onboardingAnswerRepository;
  private final PsychTestRepository psychTestRepository;
  private final FixedBlockRepository fixedBlockRepository;
  private final CardRepository cardRepository;
  private final GoalRepository goalRepository;
  private final BlockLengthService blockLengthService;
  private final AiClient aiClient;
  private final Clock clock;
  // 학교를 등록했는지(수업 시간을 피할지)와 그날 학사일정(방학 · 휴업)을 봅니다.
  private final AccountRepository accountRepository;
  private final SchoolEventRepository schoolEventRepository;
  // 내 과목. 계획 카드마다 과목을 붙이려고 읽습니다. (#457)
  private final SubjectRepository subjectRepository;

  public DayPlanResponse suggest(DayPlanRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    LocalDate today = DayRule.dateOf(clock.instant());
    LocalDate date = request.date();

    // 1) 날짜
    if (date.isBefore(today) || date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "오늘부터 " + MAX_DAYS_AHEAD + "일 안의 날짜를 보내 주세요");
    }
    // 시간 이유(공부할 수 있는 시간 · 수업 · 고정 일정 · 그날 카드 · 저녁형)의 근거 문장들. 응답의 순서는 evidenceOf 에 있습니다.
    List<String> timeLines = new ArrayList<>();

    // 2) 재료
    Optional<OnboardingAnswer> onboarding =
        onboardingAnswerRepository.findFirstByUserIdOrderByAnsweredAtDescIdDesc(userId);
    LocalTime sleep = onboarding.map(OnboardingAnswer::getSleepTime).orElse(null);
    LocalTime wake = onboarding.map(OnboardingAnswer::getWakeUpTime).orElse(null);
    boolean night =
        onboarding.map(OnboardingAnswer::getChronotype).orElse(null) == Chronotype.NIGHT;
    Integer selfDaily =
        onboarding.map(OnboardingAnswer::getDailyAvailableMinutes).filter(m -> m > 0).orElse(null);
    boolean planOptimismHigh =
        psychTestRepository
            .findFirstByUserIdOrderByIdDesc(userId)
            // 답이 거의 다 같아서 믿기 어려운 심리검사는 없는 것으로 봅니다. (PsychTest.isReliable)
            .filter(PsychTest::isReliable)
            .map(
                test ->
                    PsychScore.levelOf(
                            PsychScore.scoresOf(test.getAnswers()).get(PsychScale.PLAN_OPTIMISM))
                        == PsychScore.Level.HIGH)
            .orElse(false);

    // 그날 차지한 시간: 고정 일정 + 수업 시간 + 시작 시각이 있는 카드 (busyOn)
    DayBusy day = busyOn(userId, date);
    List<DayPlanRule.Span> busy = day.spans();
    for (FixedBlock block : day.fixedBlocks()) {
      timeLines.add(
          "고정 일정 「"
              + block.getTitle()
              + "」 "
              + block.getStartTime()
              + " ~ "
              + block.getEndTime()
              + " 을 피했어요");
    }
    if (day.classHoursLine() != null) {
      timeLines.add(day.classHoursLine());
    } else if (day.fixedBlocks().isEmpty()) {
      timeLines.add("그날 고정 일정이 없어요. 학교 · 학원 시간을 고정 일정에 넣으면 피해서 짜요");
    }
    int alreadyPlanned = day.alreadyPlanned();
    if (day.timedCards() > 0) {
      timeLines.add("그날 이미 있는 카드 " + day.timedCards() + "장의 앞뒤 10분을 비웠어요");
    }
    if (day.missedMinutes() > 0) {
      timeLines.add("시간이 지났는데 못 한 카드 " + day.missedMinutes() + "분은 이미 계획한 시간으로 세지 않았어요");
    }

    // 3) 숫자 · 시각
    BlockLength.Choice block = blockLengthService.choose(userId);
    // 학생이 계획 방에서 총량을 직접 정했으면 그 값을 씁니다. 아니면 기록으로 정합니다. (#431)
    boolean chosenByStudent = request.totalMinutes() != null;
    DayPlanRule.Total total =
        chosenByStudent
            ? DayPlanRule.chosenTotalOf(request.totalMinutes(), alreadyPlanned)
            : DayPlanRule.totalMinutesOf(
                dailyMinutesOf(userId, today), selfDaily, planOptimismHigh, alreadyPlanned);
    int target = total.minutes();
    // 오늘 컨디션이 낮다고 했으면(1~2) 총량을 60% 로 줄여 가벼운 계획을 짭니다. (2026-10-18 계획 핑퐁)
    // 학생은 코치 대화에서 [더 늘려 줘] 로 다시 늘릴 수 있습니다. (DayPlanReviseService)
    // 한 칸 길이는 그대로 두고 총량만 줄이므로, 칸 수가 줄어듭니다. (한 칸은 30분 이상, StudyBlock)
    // 단, 학생이 총량을 직접 정했으면 그 값이 먼저라 줄이지 않습니다. (#431)
    boolean lighter =
        !chosenByStudent
            && request.condition() != null
            && request.condition() <= LOW_CONDITION
            && target >= DayPlanRule.MIN_BLOCK;
    if (lighter) {
      // 총량처럼 10분 단위로 반올림합니다.   120분의 60% = 72 → 70
      int reduced = target * LIGHTER_PERCENT / 100;
      target = Math.max(DayPlanRule.MIN_BLOCK, (int) Math.round(reduced / 10.0) * 10);
    }
    if (target < DayPlanRule.MIN_BLOCK) {
      return emptyPlan(date, block, "그날은 이미 충분히 계획했어요", timeLines, total);
    }

    // 오늘이면 지금 시각 뒤부터 짭니다. 근거 문장은 늦춘 뒤의 실제 범위를 말합니다.
    DayPlanRule.Span window = DayPlanRule.studyWindowOf(sleep, wake);
    boolean fromNow = date.equals(today) && nowDayMinutes() > window.start();
    if (fromNow) {
      window = new DayPlanRule.Span(nowDayMinutes(), window.end());
    }
    timeLines.add(0, windowEvidence(sleep, wake, window, fromNow));
    // 학생이 시작 시각을 정했으면 그 전에는 카드를 두지 않습니다. 이미 그보다 늦은 범위면 그대로입니다. (#431)
    LocalTime chosenStart = startTimeOf(request.startTime());
    if (chosenStart != null) {
      DayPlanRule.Span fromChosen = DayPlanRule.startingAt(window, chosenStart);
      if (fromChosen.start() > window.start()) {
        timeLines.add(
            1,
            DayPlanRule.timeTextOf(DayPlanRule.dayMinutesOf(chosenStart))
                + " 부터 하기로 해서 그 전에는 카드를 두지 않았어요");
      }
      window = fromChosen;
    }
    List<DayPlanRule.Span> free = DayPlanRule.freeSlotsOf(window, busy);
    List<DayPlanRule.Placed> placed = DayPlanRule.placeBlocks(free, block.minutes(), target, night);
    if (night) {
      timeLines.add("저녁형이라고 해서 늦은 시간부터 채웠어요");
    }
    if (placed.isEmpty()) {
      return emptyPlan(date, block, "그날은 카드를 넣을 빈 시간이 없어요", timeLines, total);
    }
    int placedMinutes = placed.stream().mapToInt(DayPlanRule.Placed::minutes).sum();
    String headline = headlineOf(total, target, lighter, alreadyPlanned > 0, placed);

    // 칸마다 목표
    List<Goal> activeGoals = new ArrayList<>();
    for (Goal goal : goalRepository.findByUserIdOrderById(userId)) {
      if (goal.isActive()) {
        activeGoals.add(goal);
      }
    }
    Map<String, Goal> goalsById = new HashMap<>();
    List<DayPlanRule.GoalInfo> goalInfos = new ArrayList<>();
    for (Goal goal : activeGoals) {
      int daysLeft = (int) Math.max(0, ChronoUnit.DAYS.between(date, goal.getDDay()));
      String pace =
          goal.getKind() == GoalKind.COMPLETION && goal.getTotalAmount() != null
              ? DayPlanRule.dailyPaceOf(goal.getTotalAmount(), daysLeft) + goal.getAmountUnit()
              : null;
      String id = String.valueOf(goal.getId());
      goalInfos.add(new DayPlanRule.GoalInfo(id, goal.getTitle(), daysLeft, pace));
      goalsById.put(id, goal);
    }
    // 학생이 계획 방에서 먼저 할 것을 정했으면 첫 칸을 그것으로 합니다. (#454)
    List<DayPlanRule.GoalInfo> rotation =
        DayPlanRule.withFirstTask(
            DayPlanRule.goalRotation(goalInfos, placed.size()), request.firstTask());
    String goalLine =
        rotation.isEmpty() ? "진행 중인 목표가 없어서 최근 카드로 채웠어요" : "D-day 가 가까운 목표를 더 많이 넣었어요";
    List<String> evidence = evidenceOf(headline, timeLines, block, goalLine, total);
    if (request.firstTask() != null) {
      evidence.add("계획 방에서 「" + request.firstTask() + "」 을 먼저 하기로 해서 첫 칸에 넣었어요");
    }

    // 4) 글 (AI, 안 되면 기본 글)
    List<Card> myCards = cardRepository.findByUserIdOrderById(userId);
    List<String> recentTitles = recentTitlesOf(myCards);
    List<DayPlanPrompt.Block> blocks = new ArrayList<>();
    List<DayPlanPrompt.Text> fallbacks = new ArrayList<>();
    for (int i = 0; i < placed.size(); i++) {
      DayPlanRule.Placed spot = placed.get(i);
      DayPlanRule.GoalInfo goal = rotation.isEmpty() ? null : rotation.get(i);
      blocks.add(
          new DayPlanPrompt.Block(
              DayPlanRule.timeTextOf(spot.start()),
              spot.minutes(),
              goal == null ? null : goal.title(),
              goal == null ? null : goal.daysLeft(),
              goal == null ? null : goal.pace()));
      fallbacks.add(fallbackOf(goal, myCards, recentTitles, i));
    }
    // 내 과목 이름 → 번호. AI 가 칸마다 이 중에서 과목을 고릅니다. (#457)
    Map<String, String> subjectIdsByName = new LinkedHashMap<>();
    for (Subject subject : subjectRepository.findByUserIdOrderById(userId)) {
      subjectIdsByName.putIfAbsent(subject.getName().trim(), String.valueOf(subject.getId()));
    }
    List<String> subjectNames = new ArrayList<>(subjectIdsByName.keySet());
    List<DayPlanPrompt.Text> texts =
        DayPlanPrompt.textsOf(
            aiClient
                .ask(
                    userId,
                    GptUsageKind.ASSIST,
                    DayPlanPrompt.question(
                        date, blocks, recentTitles, extraLinesOf(request), subjectNames),
                    DayPlanPrompt.Answer.class)
                .orElse(null),
            fallbacks,
            subjectNames);

    List<DayPlanResponse.Draft> drafts = new ArrayList<>();
    for (int i = 0; i < placed.size(); i++) {
      DayPlanRule.GoalInfo goal = rotation.isEmpty() ? null : rotation.get(i);
      // 첫 칸 제목은 학생이 정한 그대로 둡니다. (AI 가 목표 이름으로 바꿔 쓰지 않게)
      String title =
          i == 0 && request.firstTask() != null ? request.firstTask() : texts.get(i).title();
      // 과목: AI 가 고른 것 → 없으면 제목에 든 과목 이름 → 그래도 없으면 null (#457)
      String subjectName =
          texts.get(i).subject() != null
              ? texts.get(i).subject()
              : DayPlanRule.subjectInTitle(title, subjectNames);
      drafts.add(
          new DayPlanResponse.Draft(
              date,
              DayPlanRule.timeTextOf(placed.get(i).start()),
              placed.get(i).minutes(),
              title,
              texts.get(i).checklist(),
              goal == null ? null : goal.id(),
              subjectName == null ? null : subjectIdsByName.get(subjectName)));
    }
    return new DayPlanResponse(date, block.minutes(), placedMinutes, drafts, evidence);
  }

  // 계획 방 채팅의 [오늘 쓸 수 있는 시간] 칸 재료입니다. (2026-10-25 #431)
  // 하루 계획(suggest)과 같은 계산으로 오늘의 고정 일정 · 수업 시간 · 카드 · 빈 시간을 구합니다.
  // 아무것도 저장하지 않고 AI 도 부르지 않습니다. 로그인한 사람의 것만 읽습니다.
  public TodayTime todayTime() {
    Long userId = currentUserProvider.getCurrentUserId();
    LocalDate today = DayRule.dateOf(clock.instant());
    Optional<OnboardingAnswer> onboarding =
        onboardingAnswerRepository.findFirstByUserIdOrderByAnsweredAtDescIdDesc(userId);
    LocalTime sleep = onboarding.map(OnboardingAnswer::getSleepTime).orElse(null);
    LocalTime wake = onboarding.map(OnboardingAnswer::getWakeUpTime).orElse(null);
    Integer selfDaily =
        onboarding.map(OnboardingAnswer::getDailyAvailableMinutes).filter(m -> m > 0).orElse(null);
    DayBusy day = busyOn(userId, today);

    // 고정 일정 (시작 시각 순)
    List<FixedBlock> blocks = new ArrayList<>(day.fixedBlocks());
    blocks.sort(Comparator.comparingInt(block -> DayPlanRule.dayMinutesOf(block.getStartTime())));
    List<TodayTime.Slot> fixed = new ArrayList<>();
    for (FixedBlock block : blocks) {
      int start = DayPlanRule.dayMinutesOf(block.getStartTime());
      int end = DayPlanRule.dayMinutesOf(block.getEndTime());
      fixed.add(
          new TodayTime.Slot(
              block.getTitle(),
              DayPlanRule.timeTextOf(start),
              DayPlanRule.timeTextOf(end),
              end - start));
    }

    // 오늘 카드 (시작 시각 순, 시각 없는 카드는 뒤)
    List<Card> dayCards = new ArrayList<>(day.cards());
    dayCards.sort(
        Comparator.comparingInt(
            card ->
                card.getStartTime() == null
                    ? Integer.MAX_VALUE
                    : DayPlanRule.dayMinutesOf(card.getStartTime())));
    List<TodayTime.Slot> cards = new ArrayList<>();
    for (Card card : dayCards) {
      if (card.getStartTime() == null) {
        cards.add(new TodayTime.Slot(card.getTitle(), null, null, card.getDurationMinutes()));
        continue;
      }
      int start = DayPlanRule.dayMinutesOf(card.getStartTime());
      cards.add(
          new TodayTime.Slot(
              card.getTitle(),
              DayPlanRule.timeTextOf(start),
              DayPlanRule.timeTextOf(start + card.getDurationMinutes()),
              card.getDurationMinutes()));
    }

    // 빈 시간: 하루 계획과 같이 공부할 수 있는 시간에서 차지한 시간을 빼고, 지금 시각 뒤만 봅니다.
    DayPlanRule.Span window = DayPlanRule.studyWindowOf(sleep, wake);
    if (nowDayMinutes() > window.start()) {
      window = new DayPlanRule.Span(nowDayMinutes(), window.end());
    }
    List<TodayTime.Slot> free = new ArrayList<>();
    for (DayPlanRule.Span span : DayPlanRule.freeSlotsOf(window, day.spans())) {
      free.add(
          new TodayTime.Slot(
              null,
              DayPlanRule.timeTextOf(span.start()),
              DayPlanRule.timeTextOf(span.end()),
              span.length()));
    }

    String classHours =
        day.classHoursLine() == null
            ? null
            : DayPlanRule.CLASS_START + " ~ " + DayPlanRule.CLASS_END;
    return new TodayTime(wake, sleep, selfDaily, fixed, classHours, cards, free);
  }

  // 그날 차지한 시간을 모읍니다. 하루 계획(suggest)과 계획 방 채팅(todayTime)이 같이 씁니다.
  //   1. 그날 요일의 고정 일정
  //   2. 학교를 등록했으면 평일 기본 수업 시간 (classHoursLineOf 가 정함. 고정 일정만 넣은 뒤에 봐야 합니다)
  //   3. 그날 카드 (시작 시각이 있는 카드만 시간을 차지합니다)
  private DayBusy busyOn(Long userId, LocalDate date) {
    List<DayPlanRule.Span> spans = new ArrayList<>();
    List<FixedBlock> fixedBlocks = new ArrayList<>();
    for (FixedBlock block : fixedBlockRepository.findByUserIdOrderById(userId)) {
      if (block.isOnDate(date)) {
        fixedBlocks.add(block);
        spans.add(
            new DayPlanRule.Span(
                DayPlanRule.dayMinutesOf(block.getStartTime()),
                DayPlanRule.dayMinutesOf(block.getEndTime())));
      }
    }
    String classHoursLine = classHoursLineOf(userId, date, spans);
    List<Card> cards = new ArrayList<>();
    int alreadyPlanned = 0;
    int timedCards = 0;
    int missedMinutes = 0;
    // 오늘이면, 오늘 공부 기록이 있는 카드 번호를 모읍니다. (시간이 지난 카드를 했는지 보려고)
    boolean isToday = date.equals(DayRule.dateOf(clock.instant()));
    Set<Long> studiedCardIds = isToday ? studiedCardIdsOn(userId, date) : Set.of();
    int nowMinutes = DayPlanRule.dayMinutesOf(clock.instant().atZone(SEOUL).toLocalTime());
    for (Card card : cardRepository.findCandidatesOn(userId, date)) {
      if (!card.isOnDate(date)) {
        continue;
      }
      cards.add(card);
      if (card.getStartTime() != null) {
        int start = DayPlanRule.dayMinutesOf(card.getStartTime());
        int end = start + card.getDurationMinutes();
        spans.add(new DayPlanRule.Span(start, end));
        timedCards++;
        // 오늘 카드 중 시간이 이미 끝났는데 기록이 없으면(못 한 카드) 이미 계획한 시간에 넣지 않습니다.
        // 넣으면 "지금부터 1시간" 을 부탁해도 놓친 카드 시간만큼 빠져서 "이미 충분히 계획했어요" 가 됩니다. (#447)
        if (isToday && end <= nowMinutes && !studiedCardIds.contains(card.getId())) {
          missedMinutes += card.getDurationMinutes();
          continue;
        }
      }
      alreadyPlanned += card.getDurationMinutes();
    }
    return new DayBusy(
        fixedBlocks, classHoursLine, cards, spans, alreadyPlanned, timedCards, missedMinutes);
  }

  // 그날 시작한 공부 기록이 있는 카드 번호들. 노는 기록 · 카드 없이 켠 기록은 뺍니다.
  private Set<Long> studiedCardIdsOn(Long userId, LocalDate date) {
    Set<Long> cardIds = new HashSet<>();
    for (StudySession session :
        studySessionRepository.findStartedBetween(
            userId, DayRule.startOf(date), DayRule.startOf(date.plusDays(1)))) {
      if (session.getKind() == PlanKind.STUDY && session.getCardId() != null) {
        cardIds.add(session.getCardId());
      }
    }
    return cardIds;
  }

  // 놓을 칸이 없을 때의 응답. totalMinutes 는 놓은 칸이 없으니 0 입니다.
  private static DayPlanResponse emptyPlan(
      LocalDate date,
      BlockLength.Choice block,
      String headline,
      List<String> timeLines,
      DayPlanRule.Total total) {
    return new DayPlanResponse(
        date, block.minutes(), 0, List.of(), evidenceOf(headline, timeLines, block, null, total));
  }

  // 응답의 근거 문장 순서. FE 는 앞의 몇 줄만 보여 주므로 최종 결과와 가까운 것부터 둡니다.
  //   1. 최종 결과 한 문장 (headlineOf)
  //   2. 시간 이유: 공부할 수 있는 시간 범위 → 수업 시간 · 고정 일정 → 그날 카드 → 저녁형
  //   3. 한 칸 길이 → 4. 목표 순서 (없으면 뺌) → 5. 하루 양을 정한 자세한 이유 (DayPlanRule.totalMinutesOf)
  private static List<String> evidenceOf(
      String headline,
      List<String> timeLines,
      BlockLength.Choice block,
      String goalLine,
      DayPlanRule.Total total) {
    List<String> evidence = new ArrayList<>();
    evidence.add(headline);
    evidence.addAll(timeLines);
    evidence.add(block.evidence());
    if (goalLine != null) {
      evidence.add(goalLine);
    }
    evidence.addAll(total.evidence());
    return evidence;
  }

  // 근거 첫 줄: 최종 결과를 한 문장으로. 총량 → 컨디션 → 남은 시간 버림을 최종 숫자로 합칩니다.
  //   "최근 평균으로 하루 120분을 잡아 50분 두 칸이 됐어요"
  //   "오늘 컨디션이 낮아 70분으로 줄여, 30분이 안 되는 20분은 빼서 50분 한 칸이 됐어요"
  //   "그날 이미 있는 카드를 빼고 60분이 남아, 30분이 안 되는 10분은 빼서 50분 한 칸이 됐어요"
  static String headlineOf(
      DayPlanRule.Total total,
      int target,
      boolean lighter,
      boolean hasDayCards,
      List<DayPlanRule.Placed> placed) {
    String amount;
    if (lighter) {
      amount = "오늘 컨디션이 낮아 " + target + "분으로 줄여";
    } else if (hasDayCards) {
      amount = "그날 이미 있는 카드를 빼고 " + target + "분이 남아";
    } else {
      amount = total.basis() + " 하루 " + target + "분을 잡아";
    }
    int left = target - placed.stream().mapToInt(DayPlanRule.Placed::minutes).sum();
    String dropped = "";
    if (left >= DayPlanRule.MIN_BLOCK) {
      dropped = ", 빈 시간이 모자라 " + left + "분은 못 넣고";
    } else if (left > 0) {
      dropped = ", " + DayPlanRule.MIN_BLOCK + "분이 안 되는 " + left + "분은 빼서";
    }
    return amount + dropped + " " + blocksTextOf(placed) + "이 됐어요";
  }

  // 놓은 칸을 짧게   [50, 50] → "50분 두 칸",  [50, 50, 40] → "50 · 50 · 40분 세 칸"
  static String blocksTextOf(List<DayPlanRule.Placed> placed) {
    List<String> lengths = new ArrayList<>();
    boolean sameLength = true;
    for (DayPlanRule.Placed spot : placed) {
      lengths.add(String.valueOf(spot.minutes()));
      if (spot.minutes() != placed.get(0).minutes()) {
        sameLength = false;
      }
    }
    // 길이가 모두 같으면 한 번만 씁니다.
    if (sameLength) {
      lengths = List.of(lengths.get(0));
    }
    String count =
        placed.size() <= COUNT_WORDS.size()
            ? COUNT_WORDS.get(placed.size() - 1)
            : String.valueOf(placed.size());
    return String.join(" · ", lengths) + "분 " + count + " 칸";
  }

  // 학교 수업 시간을 피해야 하는 날이면 기본 수업 시간(08:00 ~ 16:30)을 busy 에 넣고 근거 문장을 돌려줍니다.
  // 넣지 않는 날은 null 입니다.
  //   - 학교를 등록하지 않았거나, 주말이거나
  //   - 고정 일정이 수업 시간의 절반 넘게 덮으면 (학생이 학교 시간을 직접 넣은 것이라 그게 더 정확함)
  //     학원처럼 저녁에만 있거나, 16:00 ~ 18:00 처럼 수업 끝과 조금만 겹치는 일정은 학교 시간이 아니라서 넣습니다.
  //     (전에는 조금만 겹쳐도 넣지 않아서, 평일 오전 9시에 카드가 들어갔습니다. 2026-10-22 QA)
  //   - 그날 학사일정이 학교 안 가는 날이면 (isNoSchoolEvent: 방학 · 휴업 · 공휴일)
  private String classHoursLineOf(Long userId, LocalDate date, List<DayPlanRule.Span> busy) {
    String schoolCode =
        accountRepository.findByUserId(userId).map(Account::getSchoolCode).orElse(null);
    if (schoolCode == null || schoolCode.isBlank()) {
      return null;
    }
    DayOfWeek day = date.getDayOfWeek();
    if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
      return null;
    }
    DayPlanRule.Span classHours = DayPlanRule.classHours();
    int coveredMinutes = 0;
    for (DayPlanRule.Span span : busy) {
      coveredMinutes += overlapMinutes(span, classHours);
    }
    if (coveredMinutes * 2 > classHours.end() - classHours.start()) {
      return null;
    }
    for (SchoolEvent event :
        schoolEventRepository.findBySchoolCodeAndEventDateBetweenOrderByEventDateAscIdAsc(
            schoolCode, date, date)) {
      if (isNoSchoolEvent(event.getName())) {
        return null;
      }
    }
    busy.add(classHours);
    return "학교 수업 시간(평일 "
        + DayPlanRule.CLASS_START
        + " ~ "
        + DayPlanRule.CLASS_END
        + ")을 피했어요. 시간표가 다르면 학교 시간을 고정 일정에 넣어 주세요";
  }

  // 두 시간 칸이 겹치는 분. 안 겹치면 0
  //   16:00 ~ 18:00 과 08:00 ~ 16:30  →  30
  static int overlapMinutes(DayPlanRule.Span a, DayPlanRule.Span b) {
    return Math.max(0, Math.min(a.end(), b.end()) - Math.max(a.start(), b.start()));
  }

  // 학사일정 이름에 이 말이 들어 있으면 학교 안 가는 공휴일로 봅니다. (NEIS 학사일정에 쓰이는 이름, 2026-10-22 QA)
  static final List<String> HOLIDAY_WORDS =
      List.of(
          "공휴일", "신정", "설날", "삼일절", "3·1절", "어린이날", "부처님", "석가탄신일", "현충일", "광복절", "추석", "개천절",
          "한글날", "성탄절", "기독탄신일");

  // 학교 안 가는 날의 학사일정인지
  //   "재량휴업일" · "여름방학" · "추석" · "대체공휴일"  →  true
  //   "방학식" (학교에 가는 날) · "중간고사"              →  false
  static boolean isNoSchoolEvent(String name) {
    if (name == null) {
      return false;
    }
    if (name.contains("휴업") || (name.contains("방학") && !name.contains("방학식"))) {
      return true;
    }
    for (String word : HOLIDAY_WORDS) {
      if (name.contains(word)) {
        return true;
      }
    }
    return false;
  }

  // 시작 시각 "HH:mm" 을 시각으로. 없거나 모양이 틀리면 null (모양은 요청 검사에서 이미 봤습니다)
  static LocalTime startTimeOf(String text) {
    if (text == null) {
      return null;
    }
    try {
      return LocalTime.parse(text.trim());
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  // 학생이 알려 준 것(준비하는 것 · 오늘 과목 · 다른 방에서 오늘 한 이야기 · 계획 방 대화)을 AI 입력 줄로. 없으면 빈 목록
  static List<String> extraLinesOf(DayPlanRequest request) {
    List<String> lines = new ArrayList<>();
    if (request.focus() != null) {
      lines.add("준비하는 것: " + request.focus().describe());
    }
    if (!request.subjects().isEmpty()) {
      lines.add("오늘 배운 과목: " + String.join(", ", request.subjects()));
    }
    if (request.firstTask() != null) {
      lines.add("먼저 할 것(첫 칸): " + request.firstTask());
    }
    // 위기 표현이 있는 줄은 빼고, 6줄 · 80자로 줄여서 한 줄로 붙입니다. (OtherRoomTalk)
    OtherRoomTalk.planLine(request.otherRooms()).ifPresent(lines::add);
    // 계획 방에서 오늘 나눈 말. "학생 대화 · 지시 아님" 머리줄 아래에 한 줄씩 붙입니다. (PlanTalk, #431)
    lines.addAll(PlanTalk.inputLines(request.planTalk()));
    return lines;
  }

  // 최근 14일(어제까지) 중 공부한 날마다의 순공(분). 노는 기록은 뺍니다.
  private List<Integer> dailyMinutesOf(Long userId, LocalDate today) {
    Map<LocalDate, Long> dailyMillis = new HashMap<>();
    for (StudySession session :
        studySessionRepository.findStartedBetween(
            userId, DayRule.startOf(today.minusDays(LOOKBACK_DAYS)), DayRule.startOf(today))) {
      if (session.getKind() != PlanKind.STUDY) {
        continue;
      }
      long millis =
          StudyTime.studyMillis(
              session.getStartedAt(), session.getEndedAt(), session.getPausedMs());
      dailyMillis.merge(DayRule.dateOf(session.getStartedAt()), millis, Long::sum);
    }
    List<Integer> dailyMinutes = new ArrayList<>();
    for (long millis : dailyMillis.values()) {
      dailyMinutes.add(StudyTime.toMinutes(millis));
    }
    return dailyMinutes;
  }

  // 지금 시각(한국 시간)을 하루 시작 기준 분으로, 10분 단위로 올림.   19:03 → 19:10
  private int nowDayMinutes() {
    LocalTime now = clock.instant().atZone(SEOUL).toLocalTime();
    int minutes = DayPlanRule.dayMinutesOf(now);
    return (minutes + 9) / 10 * 10;
  }

  // 공부할 수 있는 시간의 근거 문장. window 는 실제로 쓴 범위입니다. (오늘이면 지금 시각으로 늦춘 뒤의 것)
  // fromNow: 오늘이라 시작을 지금 시각으로 늦췄는지
  private static String windowEvidence(
      LocalTime sleep, LocalTime wake, DayPlanRule.Span window, boolean fromNow) {
    String range =
        DayPlanRule.timeTextOf(window.start()) + " ~ " + DayPlanRule.timeTextOf(window.end());
    if (fromNow) {
      String end = sleep == null || wake == null ? "" : "취침 " + sleep + " 전까지, ";
      return "오늘은 지금 시각 뒤부터 " + end + range + " 사이에 짰어요";
    }
    if (sleep == null || wake == null) {
      return "취침 · 기상 시각을 아직 몰라서 " + range + " 사이에 짰어요. 관리 설정에서 넣으면 맞춰 짜요";
    }
    return "취침 "
        + sleep
        + " · 기상 "
        + wake
        + " 이라 수면 시간(최소 6시간)과 일어난 뒤 1시간을 빼고 "
        + range
        + " 사이에 짰어요";
  }

  // AI 가 못 쓴 칸의 기본 글
  //   목표가 있는 칸: 그 목표로 만든 가장 최근 카드의 제목(없으면 목표 이름), 체크리스트는 하루 분량 또는 제목
  //   목표가 없는 칸: 최근 카드 제목을 차례로 (없으면 "공부")
  private static DayPlanPrompt.Text fallbackOf(
      DayPlanRule.GoalInfo goal, List<Card> myCards, List<String> recentTitles, int index) {
    String title;
    if (goal != null) {
      title = cut(goal.title());
      for (int i = myCards.size() - 1; i >= 0; i--) {
        Card card = myCards.get(i);
        if (card.getGoalId() != null && String.valueOf(card.getGoalId()).equals(goal.id())) {
          title = cut(card.getTitle().trim());
          break;
        }
      }
      return new DayPlanPrompt.Text(
          title, List.of(goal.pace() == null ? title : goal.pace() + " 하기"));
    }
    title = recentTitles.isEmpty() ? "공부" : cut(recentTitles.get(index % recentTitles.size()));
    return new DayPlanPrompt.Text(title, List.of(title));
  }

  // 카드 제목 길이(30자)에 맞게 자릅니다.
  private static String cut(String text) {
    return text.length() <= CardParse.MAX_TITLE_LENGTH
        ? text
        : text.substring(0, CardParse.MAX_TITLE_LENGTH);
  }

  // 최근에 만든 카드의 제목들. 최근 것부터, 같은 제목은 한 번만, 10개까지
  private static List<String> recentTitlesOf(List<Card> cards) {
    List<String> titles = new ArrayList<>();
    for (int i = cards.size() - 1; i >= 0 && titles.size() < RECENT_TITLES; i--) {
      String title = cards.get(i).getTitle().trim();
      if (!titles.contains(title)) {
        titles.add(title);
      }
    }
    return titles;
  }
}
