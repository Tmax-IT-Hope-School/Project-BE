package com.ithopeschool.projectbe.domain.variant;

import com.ithopeschool.projectbe.domain.variant.generate.VariantPipeline;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 일일 과제를 채우는 곳입니다. (docs/VARIANT-SPEC.md 3-3)
// 조회 API(준비된 변형만 배정)와 예약 작업 variant-daily(없으면 새로 만들어 배정)가 같은 규칙을 씁니다.
//
//   1) 그날 낼 차례가 된 ACTIVE 항목을 우선순위대로 줄 세웁니다. (주의 표시 → 오래 밀린 것 → 먼저 만든 것)
//   2) 하루 상한(daily-max-items)에서 오늘 이미 낸 수를 뺀 만큼만 위에서부터 고릅니다. 나머지는 다음 날로 이월.
//   3) 고른 항목마다 그 원본 · 그 레벨의 "아직 안 쓴 VERIFIED 변형" 을 하나 배정합니다.
//      없으면 generate=true 일 때만 파이프라인으로 새로 만듭니다. 오늘 이미 3번 실패한 항목은 만들지 않습니다.
//
// 멱등: 여러 번 불러도 한 항목에 하루 한 과제뿐입니다. (DB 유니크 + 미리 확인)
// AI 를 부르는 동안 DB 작업을 열어 두지 않으려고, fill 에는 @Transactional 을 붙이지 않았습니다.
@Service
@RequiredArgsConstructor
public class DailyAssignmentService {

  private static final Logger log = LoggerFactory.getLogger(DailyAssignmentService.class);

  private final ReviewItemRepository reviewItemRepository;
  private final SourceProblemRepository sourceProblemRepository;
  private final VariantRepository variantRepository;
  private final AssignmentRepository assignmentRepository;
  private final VariantPipeline pipeline;
  private final VariantSettings settings;
  private final Clock clock;

  // 채운 결과
  //   assigned  : 이번에 새로 낸 과제 수
  //   preparing : 낼 차례인데 아직 변형이 없어 못 낸 수
  public record FillResult(int assigned, int preparing) {}

  // 그날의 일일 과제를 채웁니다.
  //   generate : true 면 준비된 변형이 없을 때 AI 로 새로 만듭니다 (예약 작업)
  //   deadline : 이 순간이 지나면 새로 만들기를 시작하지 않습니다 (예약 작업의 시간 제한). 조회는 null
  public FillResult fill(Long userId, LocalDate date, boolean generate, Instant deadline) {
    int already =
        assignmentRepository
            .findByUserIdAndKindAndAssignedDateOrderByIdAsc(userId, AssignmentKind.DAILY, date)
            .size();
    int capacity = settings.dailyMaxItems() - already;
    if (capacity <= 0) {
      return new FillResult(0, 0);
    }

    List<ReviewItem> due =
        reviewItemRepository.findDueInPriorityOrder(userId, ReviewItemStatus.ACTIVE, date).stream()
            .filter(
                item ->
                    !assignmentRepository.existsByDailyReviewItemIdAndAssignedDate(
                        item.getId(), date))
            .limit(capacity)
            .toList();

    int assigned = 0;
    int preparing = 0;
    for (ReviewItem item : due) {
      Optional<Variant> variant = readyVariant(item.getSourceProblemId(), item.getCurrentLevel());
      if (variant.isEmpty() && generate && canStillGenerate(deadline)) {
        variant = generateFor(item, date);
      }
      if (variant.isEmpty()) {
        preparing++;
        continue;
      }
      if (saveQuietly(
          Assignment.daily(
              userId, variant.get().getId(), item.getId(), item.getSourceProblemId(), date))) {
        assigned++;
      }
    }
    return new FillResult(assigned, preparing);
  }

  // 마감이 지난 PENDING 과제를 EXPIRED 로 바꿉니다. (예약 작업 variant-expire)
  // 학습 항목은 바꾸지 않습니다. 그래서 그 항목은 다음 날 다시 출제 대상이 됩니다. (벌점 없음)
  @Transactional
  public int expireOverdue(LocalDate today) {
    return assignmentRepository.expireOverdue(
        AssignmentStatus.PENDING, AssignmentStatus.EXPIRED, today);
  }

  // 그 원본 · 레벨의, 아직 아무 과제에도 안 쓴 검증된 변형 (먼저 만든 것)
  Optional<Variant> readyVariant(Long sourceProblemId, int level) {
    return variantRepository.findUnassigned(sourceProblemId, level, VariantStatus.VERIFIED).stream()
        .findFirst();
  }

  private Optional<Variant> generateFor(ReviewItem item, LocalDate date) {
    if (!pipeline.isEnabled()
        || pipeline.exhaustedToday(item.getSourceProblemId(), item.getCurrentLevel(), date)) {
      return Optional.empty();
    }
    return sourceProblemRepository
        .findById(item.getSourceProblemId())
        .flatMap(source -> pipeline.generate(source, item.getCurrentLevel()));
  }

  private boolean canStillGenerate(Instant deadline) {
    return deadline == null || clock.instant().isBefore(deadline);
  }

  // 과제를 저장합니다. 조회와 예약 작업이 동시에 같은 과제를 만들려 하면 DB 유니크가 한쪽을 막는데,
  // 그건 이미 과제가 있다는 뜻이라 실패로 보지 않고 건너뜁니다.
  private boolean saveQuietly(Assignment assignment) {
    try {
      assignmentRepository.save(assignment);
      return true;
    } catch (DataIntegrityViolationException e) {
      log.info("[변형] 이미 있는 과제라 건너뜁니다 (항목 {})", assignment.getReviewItemId());
      return false;
    }
  }
}
