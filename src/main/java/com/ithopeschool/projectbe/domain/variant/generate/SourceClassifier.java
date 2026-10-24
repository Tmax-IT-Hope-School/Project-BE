package com.ithopeschool.projectbe.domain.variant.generate;

import com.ithopeschool.projectbe.domain.variant.VariantSettings;
import com.ithopeschool.projectbe.domain.variant.VariantTaxonomy;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 원본 문항의 단원 · 유형을 AI 로 분류합니다. (VAR-05, docs/VARIANT-SPEC.md 5장)
// 앞단계(OCR)가 코드를 주지 않을 때 씁니다. 코드는 분류체계(taxonomy.json)에 있는 것만 받습니다.
//
//   AI 가 꺼져 있거나 답이 없음            → UNAVAILABLE (원본은 PENDING_CLASSIFICATION)
//   그림이 필요하다고 함                   → UNSUPPORTED_FIGURE
//   분류체계에 없는 코드 · 단원과 안 맞는 유형 → 이유를 붙여 한 번 더 묻고, 또 틀리면 NEEDS_REVIEW
//   자신감이 classify-min-confidence 미만  → NEEDS_REVIEW (사람이 확인)
//   그 밖                                → SUPPORTED
@Component
@RequiredArgsConstructor
public class SourceClassifier {

  // AI 에게 묻는 최대 횟수 (처음 + 다시 한 번)
  static final int MAX_ATTEMPTS = 2;

  public enum Outcome {
    SUPPORTED,
    NEEDS_REVIEW,
    UNSUPPORTED_FIGURE,
    UNAVAILABLE
  }

  // 분류 결과. NEEDS_REVIEW 라도 코드가 분류체계에 있으면 참고용으로 담깁니다.
  public record Result(Outcome outcome, String unitCode, String typeCode, Double confidence) {
    static Result of(Outcome outcome) {
      return new Result(outcome, null, null, null);
    }
  }

  private final VariantAi ai;
  private final VariantPrompts prompts;
  private final VariantTaxonomy taxonomy;
  private final VariantSettings settings;

  public Result classify(Long userId, String problemText, List<String> choices) {
    if (!ai.isEnabled()) {
      return Result.of(Outcome.UNAVAILABLE);
    }
    String previousFailure = null;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      VariantPrompts.Prompt prompt =
          prompts.classify(taxonomy.toPromptList(), problemText, choices, previousFailure);
      Optional<ClassificationDraft> answer =
          ai.classify(userId, prompt.instructions(), prompt.input());
      if (answer.isEmpty()) {
        return Result.of(Outcome.UNAVAILABLE);
      }
      ClassificationDraft draft = answer.get();
      if (Boolean.TRUE.equals(draft.figureRequired())) {
        return Result.of(Outcome.UNSUPPORTED_FIGURE);
      }
      if (!taxonomy.isValidPair(draft.unitCode(), draft.typeCode())) {
        previousFailure =
            "고른 코드("
                + draft.unitCode()
                + " / "
                + draft.typeCode()
                + ")가 분류 목록에 없거나, 유형이 그 단원에 속하지 않아요. 목록에 있는 짝만 고르세요.";
        continue;
      }
      double confidence = draft.confidence() == null ? 0 : draft.confidence();
      Outcome outcome =
          confidence < settings.classifyMinConfidence() ? Outcome.NEEDS_REVIEW : Outcome.SUPPORTED;
      return new Result(outcome, draft.unitCode(), draft.typeCode(), confidence);
    }
    return Result.of(Outcome.NEEDS_REVIEW);
  }
}
