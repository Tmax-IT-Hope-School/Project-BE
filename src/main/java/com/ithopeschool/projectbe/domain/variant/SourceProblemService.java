package com.ithopeschool.projectbe.domain.variant;

import com.ithopeschool.projectbe.domain.variant.dto.ClassificationUpdateRequest;
import com.ithopeschool.projectbe.domain.variant.dto.SourceProblemCreateRequest;
import com.ithopeschool.projectbe.domain.variant.dto.SourceProblemResponse;
import com.ithopeschool.projectbe.domain.variant.generate.SourceClassifier;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 원본 문항 등록(입력 계약)과 사람이 분류를 정해 주는 일을 합니다. (docs/VARIANT-SPEC.md 5장)
//
// 등록 규칙
//   1) 그림이 필요하면 UNSUPPORTED_FIGURE 로 저장만 합니다. (학습 항목 없음)
//   2) 단원 · 유형 코드를 받았으면 분류체계에 있는 짝인지 검사합니다. 없으면 400. 있으면 SUPPORTED.
//   3) 코드가 없으면 분류기(AI)로 바로 분류합니다. AI 가 꺼져 있으면 PENDING_CLASSIFICATION.
//   4) 틀린 원본이고 SUPPORTED 면 학습 항목을 만듭니다. (다음 날부터 매일 변형 출제)
//
// 분류기(AI)를 부르는 동안 DB 작업을 열어 두지 않으려고, 이 클래스의 메서드에는 @Transactional 을 붙이지 않았습니다.
// 저장은 Repository 의 save 한 번 한 번이 각각 짧게 끝납니다.
@Service
@RequiredArgsConstructor
public class SourceProblemService {

  private final CurrentUserProvider currentUserProvider;
  private final SourceProblemRepository sourceProblemRepository;
  private final ReviewItemRepository reviewItemRepository;
  private final VariantTaxonomy taxonomy;
  private final SourceClassifier classifier;
  private final Clock clock;

  // 로그인한 학생의 원본을 등록합니다.
  public SourceProblemResponse register(SourceProblemCreateRequest request) {
    return registerFor(currentUserProvider.getCurrentUserId(), request);
  }

  // 정해진 학생의 원본을 등록합니다. (평가 도구가 씁니다)
  public SourceProblemResponse registerFor(Long userId, SourceProblemCreateRequest request) {
    return registerFor(userId, request, SourceProblemOrigin.MANUAL);
  }

  // 정해진 학생의 원본을 어디서 왔는지와 함께 등록합니다. (시드 데이터는 SEED)
  public SourceProblemResponse registerFor(
      Long userId, SourceProblemCreateRequest request, SourceProblemOrigin origin) {
    boolean hasUnit = request.unitCode() != null && !request.unitCode().isBlank();
    boolean hasType = request.typeCode() != null && !request.typeCode().isBlank();
    if (hasUnit != hasType) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "단원 코드와 유형 코드는 둘 다 보내거나 둘 다 빼 주세요");
    }
    if (hasUnit && !taxonomy.isValidPair(request.unitCode(), request.typeCode())) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "분류체계에 없는 단원 · 유형 코드예요");
    }

    SourceProblem source =
        new SourceProblem(
            userId,
            request.problemText().strip(),
            request.choices(),
            request.correctAnswer(),
            request.studentAnswer(),
            request.isCorrect(),
            Boolean.TRUE.equals(request.hasFigure()),
            Instant.ofEpochMilli(request.solvedAt()),
            clock.instant(),
            origin,
            null);

    if (source.isHasFigure()) {
      source.markUnsupportedFigure();
    } else if (hasUnit) {
      source.markSupported(
          request.unitCode(), request.typeCode(), ClassificationSource.UPSTREAM, null);
    } else {
      applyClassification(
          source, classifier.classify(userId, source.getProblemText(), source.getChoices()));
    }

    SourceProblem saved = sourceProblemRepository.save(source);
    ReviewItem item = createReviewItemIfNeeded(saved);
    return toResponse(saved, item);
  }

  // 사람이 단원 · 유형을 정해 줍니다. 분류를 기다리거나 확인이 필요한 원본에만 됩니다.
  // 틀린 원본이면 이때 학습 항목을 만듭니다. (VAR-05)
  @Transactional
  public SourceProblemResponse updateClassification(Long id, ClassificationUpdateRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    SourceProblem source =
        sourceProblemRepository
            .findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "원본 문항을 찾을 수 없어요"));
    if (source.getSupportStatus() != SupportStatus.PENDING_CLASSIFICATION
        && source.getSupportStatus() != SupportStatus.NEEDS_REVIEW) {
      throw new ApiException(ErrorCode.CONFLICT, "분류를 기다리거나 확인이 필요한 원본만 코드를 정할 수 있어요");
    }
    if (!taxonomy.isValidPair(request.unitCode(), request.typeCode())) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "분류체계에 없는 단원 · 유형 코드예요");
    }
    source.markSupported(
        request.unitCode(), request.typeCode(), ClassificationSource.UPSTREAM, null);
    ReviewItem item = createReviewItemIfNeeded(source);
    return toResponse(source, item);
  }

  private static void applyClassification(SourceProblem source, SourceClassifier.Result result) {
    switch (result.outcome()) {
      case SUPPORTED ->
          source.markSupported(
              result.unitCode(), result.typeCode(), ClassificationSource.LLM, result.confidence());
      case NEEDS_REVIEW ->
          source.markNeedsReview(result.unitCode(), result.typeCode(), result.confidence());
      case UNSUPPORTED_FIGURE -> source.markUnsupportedFigure();
      // 분류기를 못 썼으면 처음 상태(PENDING_CLASSIFICATION) 그대로 둡니다.
      case UNAVAILABLE -> {}
    }
  }

  // 틀린 원본이고 출제에 쓸 수 있으면 학습 항목을 만듭니다. 다음 날(새벽 4시 기준)부터 냅니다.
  // 이미 그 원본의 항목이 있으면 만들지 않습니다. (확인 문제 결과 VariantQuizSourceService 도 씁니다)
  ReviewItem createReviewItemIfNeeded(SourceProblem source) {
    if (source.isCorrect() || source.getSupportStatus() != SupportStatus.SUPPORTED) {
      return null;
    }
    if (reviewItemRepository
        .findBySourceProblemIdAndStatus(source.getId(), ReviewItemStatus.ACTIVE)
        .isPresent()) {
      return null;
    }
    ReviewItemState state = ReviewItemState.newDailyWrong(DayRule.dateOf(source.getSolvedAt()));
    return reviewItemRepository.save(
        new ReviewItem(
            source.getUserId(),
            source.getId(),
            ReviewItemOrigin.DAILY_WRONG,
            state,
            clock.instant()));
  }

  private static SourceProblemResponse toResponse(SourceProblem source, ReviewItem item) {
    return new SourceProblemResponse(
        String.valueOf(source.getId()),
        source.getSupportStatus().toJson(),
        source.getUnitCode(),
        source.getTypeCode(),
        item == null ? null : String.valueOf(item.getId()));
  }
}
