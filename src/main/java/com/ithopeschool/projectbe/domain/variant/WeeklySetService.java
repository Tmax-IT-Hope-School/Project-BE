package com.ithopeschool.projectbe.domain.variant;

import com.ithopeschool.projectbe.domain.variant.generate.VariantPipeline;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

// 주간 심화 세트를 만드는 곳입니다. (docs/VARIANT-SPEC.md 3-4)
//
//   (A) 맞은 문제의 대표 유형 심화: 지난주(월 ~ 일)에 맞힌 원본을 유형별로 세어 많은 순으로 weekly-correct-types 개
//       (수학이 아닌 과목은 성취기준 코드를 유형으로 봅니다)
//       (동률이면 최근에 푼 유형 먼저). 유형마다 가장 최근 원본으로 L2 변형.
//   (B) 극복한 오답의 심화: GRADUATED 항목을 졸업이 오래된 순으로 weekly-wrong-max 개. L2 변형.
//       ACTIVE 항목은 넣지 않습니다. (L1 도 못 넘은 항목에 심화는 너무 어렵습니다)
//
// 멱등: 같은 주 세트는 하나(DB 유니크)이고, 세트 안에서 같은 원본 · 같은 종류의 과제도 하나입니다.
// 예약 작업이 여러 번 불러도 빠진 과제만 채웁니다.
@Service
@RequiredArgsConstructor
public class WeeklySetService {

  private static final Logger log = LoggerFactory.getLogger(WeeklySetService.class);

  // 주간 심화의 레벨
  static final int DEEP_LEVEL = 2;

  private final SourceProblemRepository sourceProblemRepository;
  private final ReviewItemRepository reviewItemRepository;
  private final VariantRepository variantRepository;
  private final AssignmentRepository assignmentRepository;
  private final WeeklySetRepository weeklySetRepository;
  private final VariantPipeline pipeline;
  private final VariantSettings settings;
  private final Clock clock;

  // 세트에 넣을 문항 하나: 원본과 출처, (B) 면 학습 항목 번호
  public record Pick(Long sourceProblemId, AssignmentKind kind, Long reviewItemId) {}

  // 세트를 만들 학생들: 그 주에 맞힌 원본이 있거나, 졸업한 항목이 있는 사람
  //   weekStart : 돌아보는 주의 월요일
  public List<Long> candidateUserIds(LocalDate weekStart) {
    List<Long> users =
        new ArrayList<>(
            sourceProblemRepository.findUserIdsWithCorrectBetween(
                SupportStatus.SUPPORTED, startOf(weekStart), startOf(weekStart.plusWeeks(1))));
    for (Long userId : reviewItemRepository.findUserIdsWithStatus(ReviewItemStatus.GRADUATED)) {
      if (!users.contains(userId)) {
        users.add(userId);
      }
    }
    return users;
  }

  // 그 학생의 그 주 세트를 만들거나(없으면) 빠진 과제를 채웁니다.
  //   weekStart : 돌아보는 주의 월요일
  //   today     : 세트를 만드는 날 (새벽 4시 기준). 마감 = today + weekly-due-days
  //   deadline  : 이 순간이 지나면 새 변형 만들기를 시작하지 않습니다 (null 이면 제한 없음)
  // 세트에 넣을 문항이 하나도 없으면 세트를 만들지 않고 빈 값입니다.
  public Optional<WeeklySet> fill(
      Long userId, LocalDate weekStart, LocalDate today, Instant deadline) {
    List<Pick> picks = new ArrayList<>(pickCorrectTypes(userId, weekStart));
    picks.addAll(pickGraduated(userId));
    if (picks.isEmpty()) {
      return Optional.empty();
    }
    WeeklySet set = findOrCreateSet(userId, weekStart, today);

    for (Pick pick : picks) {
      if (assignmentRepository.existsByWeeklySetIdAndSourceProblemIdAndKind(
          set.getId(), pick.sourceProblemId(), pick.kind())) {
        continue;
      }
      Optional<Variant> variant =
          variantRepository
              .findUnassigned(pick.sourceProblemId(), DEEP_LEVEL, VariantStatus.VERIFIED)
              .stream()
              .findFirst();
      if (variant.isEmpty() && (deadline == null || clock.instant().isBefore(deadline))) {
        variant = generate(pick.sourceProblemId(), today);
      }
      if (variant.isEmpty()) {
        continue;
      }
      try {
        assignmentRepository.save(
            Assignment.weekly(
                userId,
                variant.get().getId(),
                pick.reviewItemId(),
                pick.sourceProblemId(),
                pick.kind(),
                set.getId(),
                today,
                set.getDueDate()));
      } catch (DataIntegrityViolationException e) {
        log.info("[변형] 이미 있는 주간 과제라 건너뜁니다 (세트 {})", set.getId());
      }
    }
    return Optional.of(set);
  }

  // (A) 지난주에 맞힌 원본을 유형별로 세어 대표 원본을 고릅니다.
  public List<Pick> pickCorrectTypes(Long userId, LocalDate weekStart) {
    List<SourceProblem> correct =
        sourceProblemRepository.findCorrectBetween(
            userId, SupportStatus.SUPPORTED, startOf(weekStart), startOf(weekStart.plusWeeks(1)));

    // 유형 → 그 유형의 원본들 (푼 순서)
    // 유형은 수학이면 유형 코드, 수학이 아닌 과목이면 성취기준 코드입니다. (docs/VARIANT-SPEC.md 3-4)
    Map<String, List<SourceProblem>> byType = new LinkedHashMap<>();
    for (SourceProblem source : correct) {
      String type = source.isMath() ? source.getTypeCode() : source.getStandardCode();
      byType.computeIfAbsent(type, code -> new ArrayList<>()).add(source);
    }

    // 개수가 많은 순 → 같으면 가장 최근에 푼 순
    List<List<SourceProblem>> groups = new ArrayList<>(byType.values());
    groups.sort(
        Comparator.<List<SourceProblem>>comparingInt(List::size)
            .reversed()
            .thenComparing(group -> latest(group).getSolvedAt(), Comparator.reverseOrder()));

    List<Pick> picks = new ArrayList<>();
    for (List<SourceProblem> group :
        groups.subList(0, Math.min(groups.size(), settings.weeklyCorrectTypes()))) {
      picks.add(new Pick(latest(group).getId(), AssignmentKind.WEEKLY_CORRECT_TYPE, null));
    }
    return picks;
  }

  // (B) 졸업한 항목을 졸업이 오래된 순으로 고릅니다.
  public List<Pick> pickGraduated(Long userId) {
    return reviewItemRepository
        .findByUserIdAndStatusOrderByGraduatedAtAscIdAsc(userId, ReviewItemStatus.GRADUATED)
        .stream()
        .limit(settings.weeklyWrongMax())
        .map(
            item ->
                new Pick(item.getSourceProblemId(), AssignmentKind.WEEKLY_WRONG_DEEP, item.getId()))
        .toList();
  }

  private WeeklySet findOrCreateSet(Long userId, LocalDate weekStart, LocalDate today) {
    Optional<WeeklySet> found = weeklySetRepository.findByUserIdAndWeekStart(userId, weekStart);
    if (found.isPresent()) {
      return found.get();
    }
    try {
      return weeklySetRepository.save(
          new WeeklySet(
              userId, weekStart, today.plusDays(settings.weeklyDueDays()), clock.instant()));
    } catch (DataIntegrityViolationException e) {
      // 다른 호출이 방금 만들었습니다. 그것을 씁니다.
      return weeklySetRepository.findByUserIdAndWeekStart(userId, weekStart).orElseThrow(() -> e);
    }
  }

  private Optional<Variant> generate(Long sourceProblemId, LocalDate today) {
    if (!pipeline.isEnabled() || pipeline.exhaustedToday(sourceProblemId, DEEP_LEVEL, today)) {
      return Optional.empty();
    }
    return sourceProblemRepository
        .findById(sourceProblemId)
        .flatMap(source -> pipeline.generate(source, DEEP_LEVEL));
  }

  // 그날이 시작되는 순간 (새벽 4시 기준)
  private static Instant startOf(LocalDate date) {
    return DayRule.startOf(date);
  }

  // 그룹에서 가장 최근에 푼 원본 (같은 순간이면 번호가 큰 것)
  private static SourceProblem latest(List<SourceProblem> group) {
    return group.stream()
        .max(Comparator.comparing(SourceProblem::getSolvedAt).thenComparing(SourceProblem::getId))
        .orElseThrow();
  }
}
