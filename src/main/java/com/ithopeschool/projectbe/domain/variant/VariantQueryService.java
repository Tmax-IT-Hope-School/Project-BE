package com.ithopeschool.projectbe.domain.variant;

import com.ithopeschool.projectbe.domain.variant.dto.DailyResponse;
import com.ithopeschool.projectbe.domain.variant.dto.ReviewItemResponse;
import com.ithopeschool.projectbe.domain.variant.dto.WeeklyResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 학생이 보는 조회 API 들(일일 과제 · 주간 세트 · 오답노트)의 응답을 만듭니다. (docs/VARIANT-SPEC.md 7장)
//
// ⚠️ 절대 규칙: 답을 내기 전에는 정답 · 해설 · 검증 코드 · 선지 해설을 응답에 넣지 않습니다.
//    선지는 샌드박스가 만든 LaTeX 만 보냅니다. (AI 가 쓴 Symja 식도 보내지 않음)
@Service
@RequiredArgsConstructor
public class VariantQueryService {

  private final CurrentUserProvider currentUserProvider;
  private final DailyAssignmentService dailyAssignmentService;
  private final AssignmentRepository assignmentRepository;
  private final VariantRepository variantRepository;
  private final WeeklySetRepository weeklySetRepository;
  private final ReviewItemRepository reviewItemRepository;
  private final SourceProblemRepository sourceProblemRepository;
  private final VariantTaxonomy taxonomy;
  private final Clock clock;

  // 그날의 일일 과제. date 가 없으면 오늘(새벽 4시 기준).
  // 낼 차례인 항목 중 검증된 변형이 준비된 것은 지금 배정하고(멱등), 아직 없는 것은 preparingCount 로 알려 줍니다.
  // 지난 날짜는 이미 낸 과제만 보여 줍니다. (새로 배정하지 않음)
  public DailyResponse daily(LocalDate date) {
    Long userId = currentUserProvider.getCurrentUserId();
    LocalDate today = DayRule.dateOf(clock.instant());
    LocalDate target = date == null ? today : date;
    if (target.isAfter(today)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "아직 오지 않은 날의 과제는 볼 수 없어요");
    }

    int preparing = 0;
    // 조회(GET)지만 과제를 새로 배정(저장)하므로, 최신 동의 문구에 동의한 사람만 배정합니다. (이슈 #71 의 규칙)
    // 동의하지 않았으면 이미 낸 과제만 보여 줍니다.
    if (target.equals(today) && currentUserProvider.canSaveForCurrentUser()) {
      preparing = dailyAssignmentService.fill(userId, today, false, null).preparing();
    }

    List<Assignment> assignments =
        assignmentRepository.findByUserIdAndKindAndAssignedDateOrderByIdAsc(
            userId, AssignmentKind.DAILY, target);
    Map<Long, Variant> variants = variantsOf(assignments);
    Map<Long, SourceProblem> sources = sourcesOf(assignments);
    List<DailyResponse.Item> items = new ArrayList<>();
    for (Assignment assignment : assignments) {
      Variant variant = variants.get(assignment.getVariantId());
      items.add(
          new DailyResponse.Item(
              String.valueOf(assignment.getId()),
              variant.getLevel(),
              variant.getFormat().toJson(),
              passageOf(sources, assignment),
              variant.getProblemText(),
              choicesLatex(variant),
              effectiveStatus(assignment, today)));
    }
    return new DailyResponse(target, items, preparing);
  }

  // 그 주의 주간 세트. week 는 돌아보는 주의 월요일이고, 없으면 가장 최근 세트입니다.
  // includeAnswers 여도 이미 답했거나 마감된 문항에만 정답 · 해설을 붙입니다.
  public WeeklyResponse weekly(String week, boolean includeAnswers) {
    Long userId = currentUserProvider.getCurrentUserId();
    Optional<WeeklySet> found =
        week == null || week.isBlank()
            ? weeklySetRepository.findFirstByUserIdOrderByWeekStartDesc(userId)
            : weeklySetRepository.findByUserIdAndWeekStart(userId, parseWeek(week));
    WeeklySet set =
        found.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "그 주의 주간 세트가 아직 없어요"));
    LocalDate today = DayRule.dateOf(clock.instant());

    List<Assignment> assignments =
        new ArrayList<>(assignmentRepository.findByWeeklySetIdOrderByIdAsc(set.getId()));
    // (A) 를 먼저, 같은 출처 안에서는 낸 순서
    assignments.sort(
        Comparator.comparing((Assignment a) -> a.getKind() == AssignmentKind.WEEKLY_WRONG_DEEP)
            .thenComparing(Assignment::getId));
    Map<Long, Variant> variants = variantsOf(assignments);
    Map<Long, SourceProblem> sources = sourcesOf(assignments);

    List<WeeklyResponse.Item> items = new ArrayList<>();
    for (int i = 0; i < assignments.size(); i++) {
      Assignment assignment = assignments.get(i);
      Variant variant = variants.get(assignment.getVariantId());
      String status = effectiveStatus(assignment, today);
      WeeklyResponse.AnswerKey answerKey = null;
      if (includeAnswers && !status.equals(AssignmentStatus.PENDING.toJson())) {
        answerKey =
            new WeeklyResponse.AnswerKey(
                variant.getFormat() == VariantFormat.MC5
                    ? String.valueOf(variant.getAnswerIndex())
                    : String.valueOf(variant.getShortAnswer()),
                variant.answerLatex(),
                variant.getSolution());
      }
      items.add(
          new WeeklyResponse.Item(
              i + 1,
              String.valueOf(assignment.getId()),
              assignment.getKind() == AssignmentKind.WEEKLY_CORRECT_TYPE ? "a" : "b",
              variant.getFormat().toJson(),
              passageOf(sources, assignment),
              variant.getProblemText(),
              choicesLatex(variant),
              status,
              answerKey));
    }
    return new WeeklyResponse(set.getWeekStart(), set.getDueDate(), items);
  }

  // 오답노트 현황. status 가 없으면 전부, 있으면 그 상태만 (만든 순서)
  public List<ReviewItemResponse> reviewItems(String status) {
    Long userId = currentUserProvider.getCurrentUserId();
    List<ReviewItem> items =
        status == null || status.isBlank()
            ? reviewItemRepository.findByUserIdOrderByCreatedAtAscIdAsc(userId)
            : reviewItemRepository.findByUserIdAndStatusOrderByCreatedAtAscIdAsc(
                userId, ReviewItemStatus.fromJson(status));
    Map<Long, SourceProblem> sources =
        sourceProblemRepository
            .findAllById(items.stream().map(ReviewItem::getSourceProblemId).toList())
            .stream()
            .collect(Collectors.toMap(SourceProblem::getId, Function.identity()));

    List<ReviewItemResponse> out = new ArrayList<>();
    for (ReviewItem item : items) {
      SourceProblem source = sources.get(item.getSourceProblemId());
      String unitCode = source == null ? null : source.getUnitCode();
      String typeCode = source == null ? null : source.getTypeCode();
      out.add(
          new ReviewItemResponse(
              String.valueOf(item.getId()),
              String.valueOf(item.getSourceProblemId()),
              source == null ? null : source.getProblemText(),
              unitCode,
              taxonomy.findUnit(unitCode).map(VariantTaxonomy.Unit::name).orElse(null),
              typeCode,
              taxonomy.findType(typeCode).map(VariantTaxonomy.Type::name).orElse(null),
              item.getStatus().toJson(),
              item.getOrigin().toJson(),
              item.getCurrentLevel(),
              item.isNeedsAttention(),
              item.getNextDueDate()));
    }
    return out;
  }

  private Map<Long, Variant> variantsOf(List<Assignment> assignments) {
    return variantRepository
        .findAllById(assignments.stream().map(Assignment::getVariantId).toList())
        .stream()
        .collect(Collectors.toMap(Variant::getId, Function.identity()));
  }

  // 과제들의 원본 (지문을 보여 주려고 읽습니다)
  private Map<Long, SourceProblem> sourcesOf(List<Assignment> assignments) {
    return sourceProblemRepository
        .findAllById(assignments.stream().map(Assignment::getSourceProblemId).toList())
        .stream()
        .collect(Collectors.toMap(SourceProblem::getId, Function.identity()));
  }

  // 과제의 원본 지문. 수학이 아닌 과목은 같은 지문에 다른 문제를 내므로 지문을 함께 보여 줘야 풀 수 있습니다. 없으면 null
  private static String passageOf(Map<Long, SourceProblem> sources, Assignment assignment) {
    SourceProblem source = sources.get(assignment.getSourceProblemId());
    return source == null ? null : source.getPassageText();
  }

  private static List<String> choicesLatex(Variant variant) {
    return variant.getChoices().stream().map(VariantChoice::getLatex).toList();
  }

  // 화면에 보여 줄 상태. 마감이 지났는데 예약 작업이 아직 EXPIRED 로 바꾸지 않았어도 expired 로 보여 줍니다.
  private static String effectiveStatus(Assignment assignment, LocalDate today) {
    if (assignment.getStatus() == AssignmentStatus.PENDING && assignment.isOverdue(today)) {
      return AssignmentStatus.EXPIRED.toJson();
    }
    return assignment.getStatus().toJson();
  }

  // week 는 "2026-10-19" 처럼 월요일 날짜만 받습니다. 다른 요일이면 어느 주인지 헷갈려서 400 입니다.
  private static LocalDate parseWeek(String week) {
    LocalDate date;
    try {
      date = LocalDate.parse(week);
    } catch (DateTimeParseException e) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "week 는 2026-10-19 처럼 날짜로 보내 주세요");
    }
    if (date.getDayOfWeek() != DayOfWeek.MONDAY) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "week 는 그 주의 월요일 날짜로 보내 주세요");
    }
    return date;
  }
}
