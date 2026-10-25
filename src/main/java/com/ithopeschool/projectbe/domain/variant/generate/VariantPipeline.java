package com.ithopeschool.projectbe.domain.variant.generate;

import com.ithopeschool.projectbe.domain.curriculum.AchievementStandard;
import com.ithopeschool.projectbe.domain.curriculum.CurriculumLibrary;
import com.ithopeschool.projectbe.domain.curriculum.CurriculumVersion;
import com.ithopeschool.projectbe.domain.variant.SourceProblem;
import com.ithopeschool.projectbe.domain.variant.Variant;
import com.ithopeschool.projectbe.domain.variant.VariantChoice;
import com.ithopeschool.projectbe.domain.variant.VariantFingerprint;
import com.ithopeschool.projectbe.domain.variant.VariantFormat;
import com.ithopeschool.projectbe.domain.variant.VariantRepository;
import com.ithopeschool.projectbe.domain.variant.VariantSettings;
import com.ithopeschool.projectbe.domain.variant.VariantStatus;
import com.ithopeschool.projectbe.domain.variant.VariantTaxonomy;
import com.ithopeschool.projectbe.domain.variant.VariantVerificationMethod;
import com.ithopeschool.projectbe.domain.variant.sandbox.SandboxRequest;
import com.ithopeschool.projectbe.domain.variant.sandbox.SandboxResult;
import com.ithopeschool.projectbe.domain.variant.sandbox.SymjaGuard;
import com.ithopeschool.projectbe.domain.variant.sandbox.VariantSandbox;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

// 변형 문항 하나를 "만들고 → 검사하고 → 저장" 하는 곳입니다. (docs/VARIANT-SPEC.md 6장)
//
//   AI 생성 → G1 모양 → G2 형식 → G3 코드 안전 → G4 샌드박스 실행 → G5 정답 일치 → G6 중복 → (옵션) G7 독립 풀이
//   → 통과: 선지를 섞고 VERIFIED 로 저장
//   → 실패: REJECTED 로 저장하고, 실패 사유를 붙여 다시 생성 (최대 max-generation-attempts 번)
//   → 모두 실패: 빈 값 (검증 안 된 문항으로 대신하지 않습니다)
//
// ⚠️ AI 가 쓴 코드와 선지는 여기서 계산하지 않습니다. 모양 검사(G3)만 하고, 계산은 샌드박스(G4 · G5)가 합니다.
// ⚠️ AI 를 부르는 동안 DB 작업(트랜잭션)을 열어 두지 않습니다. 시도마다 저장만 짧게 합니다.
//
// 수학이 아닌 과목(성취기준으로 분류된 원본)은 계산할 식이 없어서 순서가 다릅니다. (docs/VARIANT-SPEC.md 6-4)
//   AI 생성(글 선지 MC5) → G1 모양 → G2 형식 → G6 중복 → G7 독립 풀이(항상) → 통과: 선지를 섞고 VERIFIED
//   G3 · G4 · G5 는 건너뛰고, 검증 방법은 AI_SOLVE 로 남깁니다.
@Service
public class VariantPipeline {

  private static final Logger log = LoggerFactory.getLogger(VariantPipeline.class);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  // 피해야 할 기존 변형으로 AI 에게 보여 주는 최대 수
  static final int AVOID_LIMIT = 5;
  // MC5 선지 수
  static final int CHOICE_COUNT = 5;
  // 단답형 정답 범위
  static final int SHORT_MIN = 1;
  static final int SHORT_MAX = 999;

  private final VariantAi ai;
  private final VariantPrompts prompts;
  private final VariantSandbox sandbox;
  private final VariantRepository variantRepository;
  private final VariantTaxonomy taxonomy;
  // 수학이 아닌 과목의 성취기준 문장을 프롬프트에 넣으려고 씁니다
  private final CurriculumLibrary curriculum;
  private final VariantSettings settings;
  private final Clock clock;
  // 선지를 섞을 때 쓰는 무작위 수. 테스트에서는 결과가 늘 같도록 정해진 것을 넣습니다
  private final RandomGenerator random;

  @Autowired
  public VariantPipeline(
      VariantAi ai,
      VariantPrompts prompts,
      VariantSandbox sandbox,
      VariantRepository variantRepository,
      VariantTaxonomy taxonomy,
      CurriculumLibrary curriculum,
      VariantSettings settings,
      Clock clock) {
    this(
        ai,
        prompts,
        sandbox,
        variantRepository,
        taxonomy,
        curriculum,
        settings,
        clock,
        new Random());
  }

  public VariantPipeline(
      VariantAi ai,
      VariantPrompts prompts,
      VariantSandbox sandbox,
      VariantRepository variantRepository,
      VariantTaxonomy taxonomy,
      CurriculumLibrary curriculum,
      VariantSettings settings,
      Clock clock,
      RandomGenerator random) {
    this.ai = ai;
    this.prompts = prompts;
    this.sandbox = sandbox;
    this.variantRepository = variantRepository;
    this.taxonomy = taxonomy;
    this.curriculum = curriculum;
    this.settings = settings;
    this.clock = clock;
    this.random = random;
  }

  // AI 를 부를 수 있는지 (꺼져 있으면 만들지 않습니다)
  public boolean isEnabled() {
    return ai.isEnabled();
  }

  // 오늘(새벽 4시 기준) 이 원본 · 레벨로 이미 max-generation-attempts 번 이상 떨어졌는지.
  // 그렇다면 오늘은 더 만들지 않고 다음 날로 넘깁니다. (docs/VARIANT-SPEC.md 3-3)
  public boolean exhaustedToday(Long sourceProblemId, int level, LocalDate today) {
    long rejected =
        variantRepository.countBySourceProblemIdAndLevelAndStatusAndCreatedAtGreaterThanEqual(
            sourceProblemId, level, VariantStatus.REJECTED, DayRule.startOf(today));
    return rejected >= settings.maxGenerationAttempts();
  }

  // 변형 하나를 만듭니다. 검사를 모두 통과한 변형(VERIFIED)을 돌려주고, 끝내 못 만들면 빈 값입니다.
  public Optional<Variant> generate(SourceProblem source, int level) {
    if (!ai.isEnabled()) {
      return Optional.empty();
    }
    if (!source.isMath()) {
      return generateText(source, level);
    }
    Optional<VariantTaxonomy.Type> type = taxonomy.findType(source.getTypeCode());
    Optional<VariantTaxonomy.Unit> unit = taxonomy.findUnit(source.getUnitCode());
    if (type.isEmpty() || unit.isEmpty()) {
      // 분류가 끝난(SUPPORTED) 원본만 오므로 여기에 올 일은 없습니다.
      log.warn("[변형] 원본 {} 의 단원 · 유형 코드가 분류체계에 없어서 만들지 않아요", source.getId());
      return Optional.empty();
    }

    List<Variant> verified =
        variantRepository.findBySourceProblemIdAndStatusOrderByIdDesc(
            source.getId(), VariantStatus.VERIFIED);
    List<String> avoid = verified.stream().limit(AVOID_LIMIT).map(Variant::getProblemText).toList();

    String previousFailure = null;
    for (int attempt = 1; attempt <= settings.maxGenerationAttempts(); attempt++) {
      VariantPrompts.Prompt prompt =
          prompts.variant(
              new VariantPrompts.VariantRequest(
                  unit.get().code(),
                  unit.get().name(),
                  type.get().code(),
                  type.get().name(),
                  type.get().misconceptions(),
                  source.getProblemText(),
                  source.getChoices(),
                  source.getCorrectAnswer(),
                  level,
                  avoid,
                  previousFailure));
      Optional<VariantDraft> draft =
          ai.generate(source.getUserId(), prompt.instructions(), prompt.input());

      GateLog gates = new GateLog();
      Optional<Checked> checked = check(draft, source, level, verified, gates);
      Instant now = clock.instant();
      if (checked.isPresent()) {
        Variant saved =
            variantRepository.save(toVerified(source, level, checked.get(), gates, attempt, now));
        return Optional.of(saved);
      }

      VariantDraft d = draft.orElse(null);
      variantRepository.save(
          Variant.rejected(
              source.getUserId(),
              source.getId(),
              level,
              d == null ? null : parseFormat(d.format()),
              d == null ? null : d.problemText(),
              d == null ? null : d.solution(),
              d == null ? null : d.verificationCode(),
              gates.toJson(),
              VariantVerificationMethod.SYMJA,
              gates.failure(),
              ai.modelName(),
              VariantPrompts.VARIANT_VERSION,
              attempt,
              now));
      previousFailure = gates.failure();
    }
    log.warn(
        "[변형] 원본 {} 레벨 {} 의 변형을 {}번 시도했지만 검사를 통과하지 못했어요",
        source.getId(),
        level,
        settings.maxGenerationAttempts());
    return Optional.empty();
  }

  // ---- 검사 (G1 ~ G7) ----

  // 검사를 모두 통과한 초안과 샌드박스 결과
  private record Checked(VariantDraft draft, VariantFormat format, SandboxResult sandbox) {}

  private Optional<Checked> check(
      Optional<VariantDraft> maybeDraft,
      SourceProblem source,
      int level,
      List<Variant> verified,
      GateLog gates) {
    // G1 모양
    if (maybeDraft.isEmpty()) {
      return gates.fail("G1", "AI 의 답을 받지 못했거나 약속한 JSON 모양이 아니에요");
    }
    VariantDraft draft = maybeDraft.get();
    Optional<String> shape = shapeProblem(draft);
    if (shape.isPresent()) {
      return gates.fail("G1", shape.get());
    }
    VariantFormat format = parseFormat(draft.format());
    gates.pass("G1");

    // G2 형식
    Optional<String> formatProblem = formatProblem(draft, format, level);
    if (formatProblem.isPresent()) {
      return gates.fail("G2", formatProblem.get());
    }
    gates.pass("G2");

    // G3 코드 안전 (계산하지 않고 모양만)
    SymjaGuard.Verdict code = SymjaGuard.checkCode(draft.verificationCode());
    if (!code.passed()) {
      return gates.fail("G3", code.reason());
    }
    if (format == VariantFormat.MC5) {
      for (String choice : draft.choices()) {
        SymjaGuard.Verdict verdict = SymjaGuard.checkChoice(choice);
        if (!verdict.passed()) {
          return gates.fail("G3", verdict.reason());
        }
      }
    }
    gates.pass("G3");

    // G4 샌드박스 실행
    SandboxResult result =
        sandbox.run(
            new SandboxRequest(
                draft.verificationCode(),
                format.name(),
                format == VariantFormat.MC5 ? draft.choices() : null,
                draft.answerIndex(),
                draft.shortAnswer()));
    if (!result.ok()) {
      return gates.fail("G4", result.error());
    }
    gates.pass("G4");

    // G5 정답 일치
    Optional<String> mismatch = answerMismatch(draft, format, result);
    if (mismatch.isPresent()) {
      return gates.fail("G5", mismatch.get());
    }
    gates.pass("G5");

    // G6 중복
    String fingerprint = VariantFingerprint.of(draft.problemText());
    if (fingerprint.equals(source.getFingerprint())) {
      return gates.fail("G6", "원본과 같은 문제예요. 수치나 표현을 바꿔 주세요");
    }
    if (verified.stream().anyMatch(v -> v.getFingerprint().equals(fingerprint))) {
      return gates.fail("G6", "이미 만든 변형과 같은 문제예요");
    }
    gates.pass("G6");

    // G7 독립 풀이 (켜져 있을 때만)
    if (settings.enableIndependentSolveCheck()) {
      Optional<String> disagreement = independentSolveProblem(source, draft, format, result);
      if (disagreement.isPresent()) {
        return gates.fail("G7", disagreement.get());
      }
      gates.pass("G7");
    } else {
      gates.skip("G7");
    }
    return Optional.of(new Checked(draft, format, result));
  }

  // G1: 꼭 있어야 하는 칸과 글자 수 한도
  private static Optional<String> shapeProblem(VariantDraft draft) {
    if (isBlank(draft.problemText())) {
      return Optional.of("문제 글(problemText)이 비어 있어요");
    }
    if (draft.problemText().length() > Variant.MAX_PROBLEM_TEXT_LENGTH) {
      return Optional.of("문제 글이 " + Variant.MAX_PROBLEM_TEXT_LENGTH + "자를 넘어요");
    }
    if (isBlank(draft.solution())) {
      return Optional.of("해설(solution)이 비어 있어요");
    }
    if (draft.solution().length() > Variant.MAX_SOLUTION_LENGTH) {
      return Optional.of("해설이 " + Variant.MAX_SOLUTION_LENGTH + "자를 넘어요");
    }
    if (isBlank(draft.verificationCode())) {
      return Optional.of("검증 코드(verificationCode)가 비어 있어요");
    }
    if (draft.verificationCode().length() > Variant.MAX_CODE_LENGTH) {
      return Optional.of("검증 코드가 " + Variant.MAX_CODE_LENGTH + "자를 넘어요");
    }
    if (parseFormat(draft.format()) == null) {
      return Optional.of("format 은 MC5 · SHORT 중 하나여야 해요");
    }
    if (draft.choices() != null) {
      for (String choice : draft.choices()) {
        if (choice != null && choice.length() > VariantChoice.MAX_VALUE_LENGTH) {
          return Optional.of("선지가 " + VariantChoice.MAX_VALUE_LENGTH + "자를 넘어요");
        }
      }
    }
    if (draft.distractorRationales() != null) {
      for (String rationale : draft.distractorRationales()) {
        if (rationale != null && rationale.length() > VariantChoice.MAX_RATIONALE_LENGTH) {
          return Optional.of("선지 해설이 " + VariantChoice.MAX_RATIONALE_LENGTH + "자를 넘어요");
        }
      }
    }
    return Optional.empty();
  }

  // G2: 형식 규칙 (docs/VARIANT-SPEC.md 6-2 표)
  private static Optional<String> formatProblem(
      VariantDraft draft, VariantFormat format, int level) {
    if (format == VariantFormat.MC5) {
      if (draft.choices() == null || draft.choices().size() != CHOICE_COUNT) {
        return Optional.of("MC5 는 선지가 정확히 5개여야 해요");
      }
      if (draft.choices().stream().anyMatch(VariantPipeline::isBlank)) {
        return Optional.of("빈 선지가 있어요");
      }
      if (draft.answerIndex() == null
          || draft.answerIndex() < 0
          || draft.answerIndex() >= CHOICE_COUNT) {
        return Optional.of("MC5 의 answerIndex 는 0 ~ 4 여야 해요");
      }
      List<String> rationales = draft.distractorRationales();
      if (rationales == null || rationales.size() != CHOICE_COUNT) {
        return Optional.of("선지 해설(distractorRationales)이 정확히 5개여야 해요");
      }
      for (int i = 0; i < CHOICE_COUNT; i++) {
        boolean blank = isBlank(rationales.get(i));
        if (i == draft.answerIndex() && !blank) {
          return Optional.of("정답 선지의 해설 칸은 빈 글자여야 해요");
        }
        if (i != draft.answerIndex() && blank) {
          return Optional.of("오답 선지 " + (i + 1) + " 의 해설이 비어 있어요");
        }
      }
      if (draft.shortAnswer() != null) {
        return Optional.of("MC5 는 shortAnswer 가 null 이어야 해요");
      }
    } else {
      if (draft.shortAnswer() == null
          || draft.shortAnswer() < SHORT_MIN
          || draft.shortAnswer() > SHORT_MAX) {
        return Optional.of("SHORT 의 정답은 1 이상 999 이하 자연수여야 해요");
      }
      if (draft.choices() != null
          || draft.answerIndex() != null
          || draft.distractorRationales() != null) {
        return Optional.of("SHORT 는 choices · answerIndex · distractorRationales 가 null 이어야 해요");
      }
    }
    if (level == -1
        && (!draft.problemText().contains("[1단계]") || !draft.problemText().contains("[2단계]"))) {
      return Optional.of("L-1 문제 글에는 [1단계] 와 [2단계] 표지가 있어야 해요");
    }
    return Optional.empty();
  }

  // G5: 계산값과 AI 가 말한 정답이 맞는지
  private static Optional<String> answerMismatch(
      VariantDraft draft, VariantFormat format, SandboxResult result) {
    if (format == VariantFormat.SHORT) {
      if (result.integerValue() == null) {
        return Optional.of("계산값(" + result.computed() + ")이 정수가 아니에요");
      }
      if (!result.integerValue().equals(draft.shortAnswer())) {
        return Optional.of(
            "계산값 " + result.integerValue() + " 이(가) 정답 " + draft.shortAnswer() + " 과(와) 달라요");
      }
      return Optional.empty();
    }
    if (!result.choicesDistinct()) {
      return Optional.of("선지 중에 값이 같은 것이 있어요 (예: 1/2 과 0.5)");
    }
    List<Integer> matched = result.matchedIndices();
    if (!matched.contains(draft.answerIndex())) {
      return Optional.of(
          "계산값("
              + result.computed()
              + ")이 정답 선지 "
              + (draft.answerIndex() + 1)
              + " 과(와) 달라요"
              + (matched.isEmpty() ? "" : ". 같은 선지 번호(0부터): " + matched));
    }
    if (matched.size() > 1) {
      return Optional.of("계산값과 같은 선지가 여러 개예요: " + matched);
    }
    return Optional.empty();
  }

  // G7: 정답을 숨긴 채 AI 에게 따로 풀게 해서 비교합니다.
  private Optional<String> independentSolveProblem(
      SourceProblem source, VariantDraft draft, VariantFormat format, SandboxResult result) {
    VariantPrompts.Prompt prompt =
        prompts.solveCheck(format.name(), draft.problemText(), result.choicesLatex());
    Optional<SolveCheckDraft> solved =
        ai.solveCheck(source.getUserId(), prompt.instructions(), prompt.input());
    if (solved.isEmpty()) {
      return Optional.of("독립 풀이 답을 받지 못했어요");
    }
    boolean agrees =
        format == VariantFormat.MC5
            ? draft.answerIndex().equals(solved.get().answerIndex())
            : draft.shortAnswer().equals(solved.get().shortAnswer());
    return agrees ? Optional.empty() : Optional.of("따로 푼 답이 정답과 달라요. 문제 조건이 모호할 수 있어요");
  }

  // ---- 통과한 변형 만들기 ----

  // 선지와 선지 해설을 같은 순서로 섞고, 정답 번호를 다시 계산합니다. (AI 가 정한 정답 위치는 쓰지 않음)
  private Variant toVerified(
      SourceProblem source, int level, Checked checked, GateLog gates, int attempt, Instant now) {
    VariantDraft draft = checked.draft();
    List<VariantChoice> choices = new ArrayList<>();
    Integer answerIndex = null;
    if (checked.format() == VariantFormat.MC5) {
      List<Integer> order = shuffledOrder();
      for (int position = 0; position < CHOICE_COUNT; position++) {
        int original = order.get(position);
        choices.add(
            new VariantChoice(
                draft.choices().get(original),
                checked.sandbox().choicesLatex().get(original),
                draft.distractorRationales().get(original)));
        if (original == draft.answerIndex()) {
          answerIndex = position;
        }
      }
    }
    return Variant.verified(
        source.getUserId(),
        source.getId(),
        level,
        checked.format(),
        draft.problemText(),
        choices,
        answerIndex,
        checked.format() == VariantFormat.SHORT ? draft.shortAnswer() : null,
        checked.sandbox().computedLatex(),
        draft.solution(),
        draft.verificationCode(),
        gates.toJson(),
        VariantVerificationMethod.SYMJA,
        ai.modelName(),
        VariantPrompts.VARIANT_VERSION,
        attempt,
        now);
  }

  // 선지 5개를 섞은 순서. order.get(새 위치) = 원래 위치
  private List<Integer> shuffledOrder() {
    List<Integer> order = new ArrayList<>();
    for (int i = 0; i < CHOICE_COUNT; i++) {
      order.add(i);
    }
    // 피셔-예이츠 섞기: 뒤에서부터 앞쪽의 아무 칸과 바꿉니다
    for (int i = order.size() - 1; i > 0; i--) {
      int j = random.nextInt(i + 1);
      Integer swap = order.get(i);
      order.set(i, order.get(j));
      order.set(j, swap);
    }
    return order;
  }

  // ---- 수학이 아닌 과목 (docs/VARIANT-SPEC.md 2-1 · 6-4) ----

  // 수학이 아닌 과목의 변형 하나를 만듭니다. 지문이 있으면 같은 지문에 다른 문제를 내게 합니다.
  // 검사를 모두 통과한 변형(VERIFIED)을 돌려주고, 끝내 못 만들면 빈 값입니다.
  private Optional<Variant> generateText(SourceProblem source, int level) {
    List<Variant> verified =
        variantRepository.findBySourceProblemIdAndStatusOrderByIdDesc(
            source.getId(), VariantStatus.VERIFIED);
    List<String> avoid = verified.stream().limit(AVOID_LIMIT).map(Variant::getProblemText).toList();
    String standardText = standardText(source.getStandardCode());

    String previousFailure = null;
    for (int attempt = 1; attempt <= settings.maxGenerationAttempts(); attempt++) {
      VariantPrompts.Prompt prompt =
          prompts.textVariant(
              new VariantPrompts.TextVariantRequest(
                  source.getSubject(),
                  source.getStandardCode(),
                  standardText,
                  source.getPassageText(),
                  source.getProblemText(),
                  source.getChoices(),
                  source.getCorrectAnswer(),
                  level,
                  avoid,
                  previousFailure));
      Optional<TextVariantDraft> draft =
          ai.generateText(source.getUserId(), prompt.instructions(), prompt.input());

      GateLog gates = new GateLog();
      boolean passed = checkText(draft, source, level, verified, gates);
      Instant now = clock.instant();
      if (passed) {
        Variant saved =
            variantRepository.save(toVerifiedText(source, level, draft.get(), gates, attempt, now));
        return Optional.of(saved);
      }

      TextVariantDraft d = draft.orElse(null);
      variantRepository.save(
          Variant.rejected(
              source.getUserId(),
              source.getId(),
              level,
              d == null ? null : VariantFormat.MC5,
              d == null ? null : d.problemText(),
              d == null ? null : d.solution(),
              null,
              gates.toJson(),
              VariantVerificationMethod.AI_SOLVE,
              gates.failure(),
              ai.modelName(),
              VariantPrompts.TEXT_VARIANT_VERSION,
              attempt,
              now));
      previousFailure = gates.failure();
    }
    log.warn(
        "[변형] 원본 {} 레벨 {} 의 변형(수학 밖 과목)을 {}번 시도했지만 검사를 통과하지 못했어요",
        source.getId(),
        level,
        settings.maxGenerationAttempts());
    return Optional.empty();
  }

  // 수학이 아닌 과목의 검사 (G1 · G2 · G6 · G7). 통과하면 true 입니다.
  private boolean checkText(
      Optional<TextVariantDraft> maybeDraft,
      SourceProblem source,
      int level,
      List<Variant> verified,
      GateLog gates) {
    // G1 모양
    if (maybeDraft.isEmpty()) {
      gates.fail("G1", "AI 의 답을 받지 못했거나 약속한 JSON 모양이 아니에요");
      return false;
    }
    TextVariantDraft draft = maybeDraft.get();
    Optional<String> shape = textShapeProblem(draft);
    if (shape.isPresent()) {
      gates.fail("G1", shape.get());
      return false;
    }
    gates.pass("G1");

    // G2 형식: 수학 MC5 와 같은 규칙 + 선지끼리 같은 글이면 안 됨
    VariantDraft asMc5 =
        new VariantDraft(
            draft.problemText(),
            VariantFormat.MC5.name(),
            draft.choices(),
            draft.answerIndex(),
            null,
            draft.distractorRationales(),
            draft.solution(),
            null,
            draft.levelJustification());
    Optional<String> formatProblem = formatProblem(asMc5, VariantFormat.MC5, level);
    if (formatProblem.isEmpty()) {
      formatProblem = duplicateChoiceProblem(draft.choices());
    }
    if (formatProblem.isPresent()) {
      gates.fail("G2", formatProblem.get());
      return false;
    }
    gates.pass("G2");

    // G3 · G4 · G5 는 계산할 식이 없어서 하지 않습니다.
    gates.skip("G3");
    gates.skip("G4");
    gates.skip("G5");

    // G6 중복
    String fingerprint = VariantFingerprint.of(draft.problemText());
    if (fingerprint.equals(source.getFingerprint())) {
      gates.fail("G6", "원본과 같은 문제예요. 묻는 대상이나 관점을 바꿔 주세요");
      return false;
    }
    if (verified.stream().anyMatch(v -> v.getFingerprint().equals(fingerprint))) {
      gates.fail("G6", "이미 만든 변형과 같은 문제예요");
      return false;
    }
    gates.pass("G6");

    // G7 독립 풀이 (수학이 아닌 과목은 설정과 상관없이 항상)
    VariantPrompts.Prompt prompt =
        prompts.textSolveCheck(source.getPassageText(), draft.problemText(), draft.choices());
    Optional<SolveCheckDraft> solved =
        ai.solveCheck(source.getUserId(), prompt.instructions(), prompt.input());
    if (solved.isEmpty()) {
      gates.fail("G7", "독립 풀이 답을 받지 못했어요");
      return false;
    }
    if (!draft.answerIndex().equals(solved.get().answerIndex())) {
      gates.fail("G7", "따로 푼 답이 정답과 달라요. 정답이 하나로 정해지지 않거나 지문에 근거가 부족할 수 있어요");
      return false;
    }
    gates.pass("G7");
    return true;
  }

  // 수학이 아닌 과목의 G1: 꼭 있어야 하는 칸과 글자 수 한도 (검증 코드는 없음)
  private static Optional<String> textShapeProblem(TextVariantDraft draft) {
    if (isBlank(draft.problemText())) {
      return Optional.of("문제 글(problemText)이 비어 있어요");
    }
    if (draft.problemText().length() > Variant.MAX_PROBLEM_TEXT_LENGTH) {
      return Optional.of("문제 글이 " + Variant.MAX_PROBLEM_TEXT_LENGTH + "자를 넘어요");
    }
    if (isBlank(draft.solution())) {
      return Optional.of("해설(solution)이 비어 있어요");
    }
    if (draft.solution().length() > Variant.MAX_SOLUTION_LENGTH) {
      return Optional.of("해설이 " + Variant.MAX_SOLUTION_LENGTH + "자를 넘어요");
    }
    if (draft.choices() != null) {
      for (String choice : draft.choices()) {
        if (choice != null && choice.length() > VariantChoice.MAX_VALUE_LENGTH) {
          return Optional.of("선지가 " + VariantChoice.MAX_VALUE_LENGTH + "자를 넘어요");
        }
      }
    }
    if (draft.distractorRationales() != null) {
      for (String rationale : draft.distractorRationales()) {
        if (rationale != null && rationale.length() > VariantChoice.MAX_RATIONALE_LENGTH) {
          return Optional.of("선지 해설이 " + VariantChoice.MAX_RATIONALE_LENGTH + "자를 넘어요");
        }
      }
    }
    return Optional.empty();
  }

  // 수학이 아닌 과목의 G2 추가 규칙: 띄어쓰기만 다른 선지도 같은 선지로 봅니다.
  private static Optional<String> duplicateChoiceProblem(List<String> choices) {
    List<String> seen = new ArrayList<>();
    for (String choice : choices) {
      String withoutSpaces = choice.replaceAll("\\s+", "");
      if (seen.contains(withoutSpaces)) {
        return Optional.of("같은 글의 선지가 있어요: " + choice);
      }
      seen.add(withoutSpaces);
    }
    return Optional.empty();
  }

  // 수학이 아닌 과목의 통과한 변형. 선지는 글이라 값과 화면용 글자가 같습니다.
  private Variant toVerifiedText(
      SourceProblem source,
      int level,
      TextVariantDraft draft,
      GateLog gates,
      int attempt,
      Instant now) {
    List<Integer> order = shuffledOrder();
    List<VariantChoice> choices = new ArrayList<>();
    Integer answerIndex = null;
    for (int position = 0; position < CHOICE_COUNT; position++) {
      int original = order.get(position);
      String text = draft.choices().get(original);
      choices.add(new VariantChoice(text, text, draft.distractorRationales().get(original)));
      if (original == draft.answerIndex()) {
        answerIndex = position;
      }
    }
    return Variant.verified(
        source.getUserId(),
        source.getId(),
        level,
        VariantFormat.MC5,
        draft.problemText(),
        choices,
        answerIndex,
        null,
        draft.choices().get(draft.answerIndex()),
        draft.solution(),
        null,
        gates.toJson(),
        VariantVerificationMethod.AI_SOLVE,
        ai.modelName(),
        VariantPrompts.TEXT_VARIANT_VERSION,
        attempt,
        now);
  }

  // 성취기준 코드의 문장 (2022 개정). 파일에 없으면 빈 값
  private String standardText(String code) {
    return curriculum.standards(CurriculumVersion.V2022).stream()
        .filter(standard -> standard.code().equals(code))
        .map(AchievementStandard::text)
        .findFirst()
        .orElse(null);
  }

  static VariantFormat parseFormat(String format) {
    if ("MC5".equals(format)) {
      return VariantFormat.MC5;
    }
    if ("SHORT".equals(format)) {
      return VariantFormat.SHORT;
    }
    return null;
  }

  private static boolean isBlank(String text) {
    return text == null || text.isBlank();
  }

  // 검사마다 통과 · 실패 · 건너뜀과 사유를 적어 두는 기록 (variants.verification_log)
  static final class GateLog {
    private final Map<String, String> results = new LinkedHashMap<>();
    private String failure;

    void pass(String gate) {
      results.put(gate, "pass");
    }

    void skip(String gate) {
      results.put(gate, "skip");
    }

    // 실패를 적고, check 가 바로 돌려줄 수 있게 빈 값을 돌려줍니다.
    <T> Optional<T> fail(String gate, String reason) {
      results.put(gate, "fail: " + reason);
      failure = gate + " " + reason;
      return Optional.empty();
    }

    String failure() {
      return failure;
    }

    String toJson() {
      return JSON.writeValueAsString(results);
    }
  }
}
