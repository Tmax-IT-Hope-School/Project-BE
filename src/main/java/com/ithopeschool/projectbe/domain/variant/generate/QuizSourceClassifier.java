package com.ithopeschool.projectbe.domain.variant.generate;

import com.ithopeschool.projectbe.domain.curriculum.AchievementStandard;
import com.ithopeschool.projectbe.domain.curriculum.CurriculumLibrary;
import com.ithopeschool.projectbe.domain.curriculum.CurriculumVersion;
import com.ithopeschool.projectbe.domain.variant.VariantSettings;
import com.ithopeschool.projectbe.domain.variant.VariantTaxonomy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 확인 문제 하나를 "수학 분류체계(고1 공통수학1 단원 · 유형)" 또는 "2022 개정 성취기준" 으로 분류합니다.
// (docs/VARIANT-SPEC.md 5-1)
//
//   AI 가 꺼져 있거나 답이 없음                     → UNAVAILABLE (원본으로 쓰지 않음)
//   그림 · 그래프 · 표가 필요하다고 함               → UNSUPPORTED_FIGURE (저장만, 출제 안 함 — 5장 1번과 같음)
//   MATH  : 분류체계에 있는 짝 → 자신감이 낮으면 NEEDS_REVIEW, 아니면 SUPPORTED_MATH
//   STANDARD : 후보에 있는 성취기준 코드 → 자신감이 낮으면 OUT_OF_SCOPE, 아니면 SUPPORTED_STANDARD
//   NONE 이거나 목록에 없는 코드를 두 번 고름        → OUT_OF_SCOPE (수학이면 NEEDS_REVIEW, 5장 4번과 같음)
//
// 수학이 아닌 과목은 사람이 코드를 정해 줄 화면 · API 가 없어서, 자신 없는 분류는 저장하지 않고 OUT_OF_SCOPE 로 둡니다.
// (검증 안 된 원본이 쌓이지 않게 하는 보수적인 선택)
@Component
@RequiredArgsConstructor
public class QuizSourceClassifier {

  // AI 에게 묻는 최대 횟수 (처음 + 다시 한 번)
  static final int MAX_ATTEMPTS = 2;
  // AI 에게 보여 줄 성취기준 후보 수
  static final int STANDARD_CANDIDATES = 12;

  public enum Outcome {
    SUPPORTED_MATH,
    SUPPORTED_STANDARD,
    NEEDS_REVIEW,
    UNSUPPORTED_FIGURE,
    OUT_OF_SCOPE,
    UNAVAILABLE
  }

  // 분류 결과. SUPPORTED_MATH · NEEDS_REVIEW 는 단원 · 유형, SUPPORTED_STANDARD 는 과목 · 성취기준 코드를 담습니다.
  public record Result(
      Outcome outcome,
      String unitCode,
      String typeCode,
      String subject,
      String standardCode,
      Double confidence) {
    static Result of(Outcome outcome) {
      return new Result(outcome, null, null, null, null, null);
    }
  }

  private final VariantAi ai;
  private final VariantPrompts prompts;
  private final VariantTaxonomy taxonomy;
  private final CurriculumLibrary curriculum;
  private final VariantSettings settings;

  public Result classify(
      Long userId, String topic, String question, String passage, List<String> choices) {
    if (!ai.isEnabled()) {
      return Result.of(Outcome.UNAVAILABLE);
    }
    // 문항 글로 성취기준 후보를 골라 둡니다. (AI 가 목록 밖 코드를 지어내지 않게)
    List<AchievementStandard> candidates =
        curriculum.search(
            CurriculumVersion.V2022, List.of(), topic + " " + question, STANDARD_CANDIDATES);
    Map<String, AchievementStandard> byCode =
        candidates.stream()
            .collect(
                Collectors.toMap(
                    AchievementStandard::code, Function.identity(), (first, second) -> first));

    String previousFailure = null;
    boolean lastWasMath = false;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      VariantPrompts.Prompt prompt =
          prompts.quizClassify(
              taxonomy.toPromptList(),
              standardList(candidates),
              topic,
              passage,
              question,
              choices,
              previousFailure);
      Optional<QuizClassificationDraft> answer =
          ai.classifyQuiz(userId, prompt.instructions(), prompt.input());
      if (answer.isEmpty()) {
        return Result.of(Outcome.UNAVAILABLE);
      }
      QuizClassificationDraft draft = answer.get();
      if (Boolean.TRUE.equals(draft.figureRequired())) {
        return Result.of(Outcome.UNSUPPORTED_FIGURE);
      }
      double confidence = draft.confidence() == null ? 0 : draft.confidence();
      boolean confident = confidence >= settings.classifyMinConfidence();
      String kind = draft.kind() == null ? "" : draft.kind();
      switch (kind) {
        case "MATH" -> {
          lastWasMath = true;
          if (taxonomy.isValidPair(draft.unitCode(), draft.typeCode())) {
            return new Result(
                confident ? Outcome.SUPPORTED_MATH : Outcome.NEEDS_REVIEW,
                draft.unitCode(),
                draft.typeCode(),
                null,
                null,
                confidence);
          }
          previousFailure =
              "고른 코드("
                  + draft.unitCode()
                  + " / "
                  + draft.typeCode()
                  + ")가 수학 분류 목록에 없거나, 유형이 그 단원에 속하지 않아요. 목록에 있는 짝만 고르세요.";
        }
        case "STANDARD" -> {
          lastWasMath = false;
          AchievementStandard standard = byCode.get(draft.standardCode());
          if (standard != null) {
            if (!confident) {
              return Result.of(Outcome.OUT_OF_SCOPE);
            }
            return new Result(
                Outcome.SUPPORTED_STANDARD,
                null,
                null,
                standard.subject(),
                standard.code(),
                confidence);
          }
          previousFailure =
              "고른 성취기준 코드(" + draft.standardCode() + ")가 후보에 없어요. 후보에 있는 코드만 그대로 고르세요.";
        }
        case "NONE" -> {
          return Result.of(Outcome.OUT_OF_SCOPE);
        }
        default -> {
          lastWasMath = false;
          previousFailure = "kind 는 MATH · STANDARD · NONE 중 하나만 쓰세요.";
        }
      }
    }
    // 두 번 다 목록 밖이면: 수학은 사람이 확인(PATCH 로 정할 수 있음), 그 밖은 원본으로 쓰지 않습니다.
    return Result.of(lastWasMath ? Outcome.NEEDS_REVIEW : Outcome.OUT_OF_SCOPE);
  }

  // 후보를 "코드 | 과목 | 영역 | 문장" 줄로
  static String standardList(List<AchievementStandard> candidates) {
    if (candidates.isEmpty()) {
      return "";
    }
    StringBuilder text = new StringBuilder();
    for (AchievementStandard standard : candidates) {
      text.append("- ")
          .append(standard.code())
          .append(" | ")
          .append(standard.subject())
          .append(standard.area().isBlank() ? "" : " | " + standard.area())
          .append(" | ")
          .append(standard.text())
          .append('\n');
    }
    return text.toString().strip();
  }
}
