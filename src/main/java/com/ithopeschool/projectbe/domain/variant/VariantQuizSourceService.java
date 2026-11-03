package com.ithopeschool.projectbe.domain.variant;

import com.ithopeschool.projectbe.domain.variant.dto.VariantQuizSourceRequest;
import com.ithopeschool.projectbe.domain.variant.dto.VariantQuizSourceResponse;
import com.ithopeschool.projectbe.domain.variant.generate.QuizSourceClassifier;
import com.ithopeschool.projectbe.domain.variant.generate.SolveCheckDraft;
import com.ithopeschool.projectbe.domain.variant.generate.VariantAi;
import com.ithopeschool.projectbe.domain.variant.generate.VariantPrompts;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// 확인 문제 결과를 변형 문제의 원본으로 받습니다. (docs/VARIANT-SPEC.md 5-1, 2026-10-24 결정 #394)
//
// 문제마다 이 순서로 봅니다.
//   1) 맞았는지는 서버가 다시 셉니다 (고른 번호 = 정답 번호). FE 가 보낸 판정을 믿지 않습니다.
//   2) "찍은 것 같다" 고 본 결과의 맞힌 문제는 원본으로 쓰지 않습니다. (찍어서 맞힌 것은 아는 문제가 아님)
//   3) 정답 확인: 정답을 숨긴 채 AI 에게 다시 풀게 해서 확인 문제의 정답과 다르면 원본으로 쓰지 않습니다.
//      (확인 문제도 AI 가 만든 것이라 정답이 틀릴 수 있음. 정답이 틀린 문제로 "오답" 이 쌓이지 않게)
//   4) 분류: 수학 분류체계(단원 · 유형) 또는 2022 성취기준. 범위 밖이면 원본으로 쓰지 않습니다.
//   5) 원본으로 저장하고, 틀린 문제면 학습 항목을 만듭니다 (다음 날부터 매일 검증 문제). 맞은 문제는 주간 심화 (A) 의 재료입니다.
//
// AI 가 꺼져 있거나 답하지 않으면 정답을 확인할 수 없어서 저장하지 않습니다(unsupported). 검증 안 된 원본이 쌓이지 않게 하려는 것입니다.
// AI 를 부르는 동안 DB 작업을 열어 두지 않으려고 @Transactional 을 붙이지 않았습니다. (SourceProblemService 와 같음)
// 한 번에 5문제까지라서 요청 안에서 끝까지 처리합니다. (문제마다 AI 2 ~ 3번)
@Service
@RequiredArgsConstructor
public class VariantQuizSourceService {

  private static final Logger log = LoggerFactory.getLogger(VariantQuizSourceService.class);

  private final CurrentUserProvider currentUserProvider;
  private final SourceProblemRepository sourceProblemRepository;
  private final SourceProblemService sourceProblemService;
  private final VariantAi ai;
  private final VariantPrompts prompts;
  private final QuizSourceClassifier classifier;
  private final Clock clock;

  public VariantQuizSourceResponse submit(VariantQuizSourceRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    Instant solvedAt = Instant.ofEpochMilli(request.solvedAt());
    String topic = request.topic().strip();

    int registered = 0;
    int answerMismatch = 0;
    int guessedCorrect = 0;
    int unsupported = 0;
    for (VariantQuizSourceRequest.Question question : request.questions()) {
      boolean correct = question.chosenIndex().equals(question.answerIndex());
      if (correct && Boolean.TRUE.equals(request.guessed())) {
        guessedCorrect++;
        continue;
      }
      switch (register(userId, topic, question, correct, solvedAt)) {
        case REGISTERED -> registered++;
        case ANSWER_MISMATCH -> answerMismatch++;
        case UNSUPPORTED -> unsupported++;
      }
    }
    // 학생 번호 · 문제 내용은 찍지 않고 숫자만 남깁니다.
    log.info(
        "[변형 확인 문제] 저장 {}개, 정답 불일치 {}개, 찍어서 맞힘 {}개, 범위 밖 · 확인 못 함 {}개",
        registered,
        answerMismatch,
        guessedCorrect,
        unsupported);
    return new VariantQuizSourceResponse(
        registered,
        new VariantQuizSourceResponse.Skipped(answerMismatch, guessedCorrect, unsupported));
  }

  enum Outcome {
    REGISTERED,
    ANSWER_MISMATCH,
    UNSUPPORTED
  }

  private Outcome register(
      Long userId,
      String topic,
      VariantQuizSourceRequest.Question question,
      boolean correct,
      Instant solvedAt) {
    if (!ai.isEnabled()) {
      return Outcome.UNSUPPORTED;
    }
    String text = question.question().strip();
    String passage = question.passage() == null ? null : question.passage().strip();
    List<String> choices = question.choices().stream().map(String::strip).toList();

    // 3) 정답 확인
    VariantPrompts.Prompt checkPrompt = prompts.quizCheck(text, passage, choices);
    Optional<SolveCheckDraft> check =
        ai.solveCheck(userId, checkPrompt.instructions(), checkPrompt.input());
    if (check.isEmpty()) {
      return Outcome.UNSUPPORTED;
    }
    if (!question.answerIndex().equals(check.get().answerIndex())) {
      return Outcome.ANSWER_MISMATCH;
    }

    // 4) 분류
    QuizSourceClassifier.Result result = classifier.classify(userId, topic, text, passage, choices);
    if (result.outcome() == QuizSourceClassifier.Outcome.OUT_OF_SCOPE
        || result.outcome() == QuizSourceClassifier.Outcome.UNAVAILABLE) {
      return Outcome.UNSUPPORTED;
    }

    // 5) 저장
    SourceProblem source =
        new SourceProblem(
            userId,
            text,
            choices,
            choices.get(question.answerIndex()),
            choices.get(question.chosenIndex()),
            correct,
            false,
            solvedAt,
            clock.instant(),
            SourceProblemOrigin.QUIZ,
            passage);
    switch (result.outcome()) {
      case SUPPORTED_MATH ->
          source.markSupported(
              result.unitCode(), result.typeCode(), ClassificationSource.LLM, result.confidence());
      case SUPPORTED_STANDARD ->
          source.markSupportedByStandard(
              result.subject(), result.standardCode(), result.confidence());
      case NEEDS_REVIEW ->
          source.markNeedsReview(result.unitCode(), result.typeCode(), result.confidence());
      case UNSUPPORTED_FIGURE -> source.markUnsupportedFigure();
      default -> throw new IllegalStateException("처리하지 않은 분류 결과: " + result.outcome());
    }
    SourceProblem saved = sourceProblemRepository.save(source);
    sourceProblemService.createReviewItemIfNeeded(saved);
    return Outcome.REGISTERED;
  }
}
