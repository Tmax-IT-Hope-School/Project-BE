package com.ithopeschool.projectbe.domain.variant;

import com.ithopeschool.projectbe.domain.variant.dto.AttemptRequest;
import com.ithopeschool.projectbe.domain.variant.dto.AttemptResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 답을 받아 채점하고, 학습 항목의 상태를 바꿉니다. (docs/VARIANT-SPEC.md 3-2 · 3-4 · 7장)
//
//   - 과제당 한 번만 답할 수 있습니다. 이미 답했거나 마감이 지났으면 409.
//   - 답의 모양이 틀리면 400 (MC5 는 "0" ~ "4", SHORT 는 정수).
//   - 상태를 어떻게 바꿀지는 ReviewItemRules 가 정합니다. 여기서는 부르기만 합니다.
//       일일          → applyDaily
//       주간 (A) 오답 → 그 원본에 ACTIVE 항목이 없으면 L1 로 새 항목 (원본을 맞혔으므로 L0 을 건너뜀)
//       주간 (B)      → applyWeeklyDeep (정답 MASTERED / 오답 ACTIVE L0 으로 복귀)
//   - 답을 낸 뒤에야 정답 · 해설 · 고른 오답의 설명을 돌려줍니다.
@Service
@RequiredArgsConstructor
public class AttemptService {

  private final CurrentUserProvider currentUserProvider;
  private final AssignmentRepository assignmentRepository;
  private final AttemptRepository attemptRepository;
  private final VariantRepository variantRepository;
  private final ReviewItemRepository reviewItemRepository;
  private final VariantSettings settings;
  private final Clock clock;

  @Transactional
  public AttemptResponse submit(Long assignmentId, AttemptRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    Assignment assignment =
        assignmentRepository
            .findByIdAndUserId(assignmentId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "과제를 찾을 수 없어요"));

    Instant now = clock.instant();
    LocalDate today = DayRule.dateOf(now);
    if (assignment.getStatus() == AssignmentStatus.ANSWERED
        || attemptRepository.existsByAssignmentId(assignment.getId())) {
      throw new ApiException(ErrorCode.CONFLICT, "이미 답한 과제예요");
    }
    if (assignment.getStatus() == AssignmentStatus.EXPIRED || assignment.isOverdue(today)) {
      throw new ApiException(ErrorCode.CONFLICT, "마감이 지난 과제예요");
    }

    Variant variant =
        variantRepository
            .findByIdAndUserId(assignment.getVariantId(), userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "과제의 문제를 찾을 수 없어요"));
    String answer = request.answer().strip();
    boolean correct = grade(variant, answer);

    attemptRepository.save(
        new Attempt(userId, assignment.getId(), answer, correct, request.timeSpentSec(), now));
    assignment.markAnswered();

    ReviewItem item = applyResult(assignment, correct, today, now);
    return toResponse(variant, answer, correct, item);
  }

  // 채점. 답의 모양이 틀리면 400 입니다.
  private static boolean grade(Variant variant, String answer) {
    if (variant.getFormat() == VariantFormat.MC5) {
      if (!answer.matches("[0-4]")) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, "5지선다는 선지 번호 0 ~ 4 중 하나를 보내 주세요");
      }
      return Integer.parseInt(answer) == variant.getAnswerIndex();
    }
    if (!answer.matches("-?\\d{1,9}")) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "단답형은 정수로 답해 주세요");
    }
    return Integer.parseInt(answer) == variant.getShortAnswer();
  }

  // 과제 종류에 맞게 학습 항목을 바꿉니다. 바뀐(또는 새로 만든) 항목을 돌려주고, 없으면 null 입니다.
  private ReviewItem applyResult(
      Assignment assignment, boolean correct, LocalDate today, Instant now) {
    switch (assignment.getKind()) {
      case DAILY -> {
        ReviewItem item = findItem(assignment.getReviewItemId());
        // 그사이 졸업하는 등 ACTIVE 가 아니면 기록만 남기고 상태는 그대로 둡니다.
        if (item != null && item.getStatus() == ReviewItemStatus.ACTIVE) {
          item.apply(ReviewItemRules.applyDaily(item.state(), correct, today, now, settings), now);
        }
        return item;
      }
      case WEEKLY_CORRECT_TYPE -> {
        if (correct) {
          return null;
        }
        Optional<ReviewItem> active =
            reviewItemRepository.findBySourceProblemIdAndStatus(
                assignment.getSourceProblemId(), ReviewItemStatus.ACTIVE);
        if (active.isPresent()) {
          return active.get();
        }
        return reviewItemRepository.save(
            new ReviewItem(
                assignment.getUserId(),
                assignment.getSourceProblemId(),
                ReviewItemOrigin.WEEKLY_CORRECT_FAIL,
                ReviewItemState.newWeeklyCorrectFail(today),
                now));
      }
      case WEEKLY_WRONG_DEEP -> {
        ReviewItem item = findItem(assignment.getReviewItemId());
        if (item != null && item.getStatus() == ReviewItemStatus.GRADUATED) {
          item.apply(ReviewItemRules.applyWeeklyDeep(item.state(), correct, today, now), now);
        }
        return item;
      }
    }
    return null;
  }

  private ReviewItem findItem(Long reviewItemId) {
    return reviewItemId == null ? null : reviewItemRepository.findById(reviewItemId).orElse(null);
  }

  private static AttemptResponse toResponse(
      Variant variant, String answer, boolean correct, ReviewItem item) {
    String correctAnswer;
    String rationale = null;
    if (variant.getFormat() == VariantFormat.MC5) {
      correctAnswer = String.valueOf(variant.getAnswerIndex());
      if (!correct) {
        rationale = variant.getChoices().get(Integer.parseInt(answer)).getRationale();
      }
    } else {
      correctAnswer = String.valueOf(variant.getShortAnswer());
    }
    AttemptResponse.ReviewItemSummary summary =
        item == null
            ? null
            : new AttemptResponse.ReviewItemSummary(
                String.valueOf(item.getId()), item.getStatus().toJson(), item.getCurrentLevel());
    return new AttemptResponse(
        correct, correctAnswer, variant.answerLatex(), variant.getSolution(), rationale, summary);
  }
}
