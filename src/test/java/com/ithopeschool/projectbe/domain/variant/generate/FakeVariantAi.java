package com.ithopeschool.projectbe.domain.variant.generate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

// 테스트용 가짜 AI 입니다. 진짜 AI 를 부르지 않고, 미리 넣어 둔 답을 차례로 돌려줍니다.
// 받은 프롬프트(input)도 모아 두어서 "두 번째 프롬프트에 실패 사유가 들어갔는지" 같은 것을 볼 수 있습니다.
public class FakeVariantAi implements VariantAi {

  private boolean enabled = true;
  private final Deque<Optional<VariantDraft>> drafts = new ArrayDeque<>();
  private final Deque<Optional<ClassificationDraft>> classifications = new ArrayDeque<>();
  private final Deque<Optional<SolveCheckDraft>> solveChecks = new ArrayDeque<>();
  private final Deque<Optional<QuizClassificationDraft>> quizClassifications = new ArrayDeque<>();
  // 독립 풀이 · 확인 문제 분류 프롬프트로 받은 user 칸들 (부른 순서)
  public final List<String> solveInputs = new ArrayList<>();
  public final List<String> quizClassifyInputs = new ArrayList<>();
  // 변형 생성 프롬프트로 받은 user 칸들 (부른 순서)
  public final List<String> variantInputs = new ArrayList<>();
  public final List<String> classifyInputs = new ArrayList<>();

  public FakeVariantAi disable() {
    enabled = false;
    return this;
  }

  // 통합 테스트에서 테스트마다 처음 상태로 돌립니다.
  public void reset() {
    enabled = true;
    drafts.clear();
    classifications.clear();
    solveChecks.clear();
    quizClassifications.clear();
    variantInputs.clear();
    classifyInputs.clear();
    solveInputs.clear();
    quizClassifyInputs.clear();
    fallback = null;
  }

  public FakeVariantAi willGenerate(VariantDraft draft) {
    drafts.add(Optional.of(draft));
    return this;
  }

  // AI 가 답하지 않은 경우 (시간 초과 등)
  public FakeVariantAi willNotAnswer() {
    drafts.add(Optional.empty());
    return this;
  }

  public FakeVariantAi willClassify(ClassificationDraft draft) {
    classifications.add(Optional.ofNullable(draft));
    return this;
  }

  public FakeVariantAi willSolve(SolveCheckDraft draft) {
    solveChecks.add(Optional.ofNullable(draft));
    return this;
  }

  public FakeVariantAi willClassifyQuiz(QuizClassificationDraft draft) {
    quizClassifications.add(Optional.ofNullable(draft));
    return this;
  }

  // 넣어 둔 변형 답이 남아 있으면 그것을, 없으면 이 답을 계속 돌려줍니다.
  private VariantDraft fallback;

  public FakeVariantAi alwaysGenerate(VariantDraft draft) {
    fallback = draft;
    return this;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public String modelName() {
    return "fake-model";
  }

  @Override
  public Optional<VariantDraft> generate(Long userId, String instructions, String input) {
    variantInputs.add(input);
    if (!drafts.isEmpty()) {
      return drafts.poll();
    }
    return Optional.ofNullable(fallback);
  }

  @Override
  public Optional<ClassificationDraft> classify(Long userId, String instructions, String input) {
    classifyInputs.add(input);
    return classifications.isEmpty() ? Optional.empty() : classifications.poll();
  }

  @Override
  public Optional<SolveCheckDraft> solveCheck(Long userId, String instructions, String input) {
    solveInputs.add(input);
    return solveChecks.isEmpty() ? Optional.empty() : solveChecks.poll();
  }

  @Override
  public Optional<QuizClassificationDraft> classifyQuiz(
      Long userId, String instructions, String input) {
    quizClassifyInputs.add(input);
    return quizClassifications.isEmpty() ? Optional.empty() : quizClassifications.poll();
  }
}
