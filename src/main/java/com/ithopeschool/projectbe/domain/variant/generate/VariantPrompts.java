package com.ithopeschool.projectbe.domain.variant.generate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

// 변형 문제 기능의 프롬프트들(변형 생성 · 분류 · 독립 풀이, 확인 문제용, 수학 밖 과목용)을 서버가 켜질 때 읽어 두고, 값을 채워 줍니다.
// 프롬프트 글은 코드에 적지 않고 src/main/resources/prompts/variant/*.md 에만 있습니다.
//
// 프롬프트를 바꾸려면 기존 파일을 고치지 말고 새 버전 파일(예: variant_v3.md)을 만든 뒤 아래 버전 이름을 바꿉니다.
// 그리고 품질 평가(VariantGenerationEvalTest)를 다시 돌립니다. (docs/VARIANT-SPEC.md 10장)
@Component
public class VariantPrompts {

  public static final String VARIANT_VERSION = "variant_v2";
  public static final String CLASSIFY_VERSION = "classify_v2";
  public static final String SOLVE_CHECK_VERSION = "solve_check_v2";
  // 확인 문제를 원본으로 쓰기 전의 정답 확인 · 분류 (docs/VARIANT-SPEC.md 5-1)
  public static final String QUIZ_CHECK_VERSION = "quiz_check_v1";
  public static final String QUIZ_CLASSIFY_VERSION = "quiz_classify_v1";
  // 수학이 아닌 과목의 변형 생성 · G7 독립 풀이 (docs/VARIANT-SPEC.md 2-1 · 6-4)
  public static final String TEXT_VARIANT_VERSION = "variant_text_v1";
  public static final String TEXT_SOLVE_CHECK_VERSION = "solve_check_text_v1";

  // 비어 있는 값을 AI 에게 보여 줄 때 쓰는 글
  static final String NONE = "(없음)";

  // AI 에게 보내는 지침(system)과 이번 내용(user)
  public record Prompt(String instructions, String input) {}

  private final PromptTemplate variant;
  private final PromptTemplate classify;
  private final PromptTemplate solveCheck;
  private final PromptTemplate quizCheck;
  private final PromptTemplate quizClassify;
  private final PromptTemplate textVariant;
  private final PromptTemplate textSolveCheck;

  public VariantPrompts() {
    this.variant = PromptTemplate.load(VARIANT_VERSION);
    this.classify = PromptTemplate.load(CLASSIFY_VERSION);
    this.solveCheck = PromptTemplate.load(SOLVE_CHECK_VERSION);
    this.quizCheck = PromptTemplate.load(QUIZ_CHECK_VERSION);
    this.quizClassify = PromptTemplate.load(QUIZ_CLASSIFY_VERSION);
    this.textVariant = PromptTemplate.load(TEXT_VARIANT_VERSION);
    this.textSolveCheck = PromptTemplate.load(TEXT_SOLVE_CHECK_VERSION);
    for (int level = -1; level <= 2; level++) {
      if (!variant.hasSection(levelSection(level))) {
        throw new IllegalStateException(VARIANT_VERSION + " 에 " + levelSection(level) + " 칸이 없어요");
      }
      if (!textVariant.hasSection(levelSection(level))) {
        throw new IllegalStateException(
            TEXT_VARIANT_VERSION + " 에 " + levelSection(level) + " 칸이 없어요");
      }
    }
  }

  // 변형 생성 프롬프트
  public Prompt variant(VariantRequest request) {
    Map<String, String> values = new HashMap<>();
    values.put("unit_code", request.unitCode());
    values.put("unit_name", request.unitName());
    values.put("type_code", request.typeCode());
    values.put("type_name", request.typeName());
    values.put("misconceptions", bullets(request.misconceptions()));
    values.put("source_problem_text", request.sourceProblemText());
    values.put("source_choices", numbered(request.sourceChoices()));
    values.put("source_answer", orNone(request.sourceAnswer()));
    values.put("level", levelLabel(request.level()));
    values.put("level_rule", variant.render(levelSection(request.level()), Map.of()));
    values.put("avoid_variants", numbered(request.avoidVariants()));
    values.put("previous_failure", orNone(request.previousFailure()));
    return new Prompt(variant.render("system", values), variant.render("user", values));
  }

  // 분류 프롬프트
  public Prompt classify(
      String taxonomyList, String problemText, List<String> choices, String previousFailure) {
    Map<String, String> values = new HashMap<>();
    values.put("taxonomy_list", taxonomyList);
    values.put("problem_text", problemText);
    values.put("choices", numbered(choices));
    values.put("previous_failure", orNone(previousFailure));
    return new Prompt(classify.render("system", values), classify.render("user", values));
  }

  // 독립 풀이 프롬프트. 선지는 학생이 보는 것과 같은 LaTeX 로 보냅니다. (정답은 보내지 않음)
  public Prompt solveCheck(String format, String problemText, List<String> choicesLatex) {
    Map<String, String> values = new HashMap<>();
    values.put("format", format);
    values.put("problem_text", problemText);
    values.put("choices_text", numbered(choicesLatex));
    return new Prompt(solveCheck.render("system", values), solveCheck.render("user", values));
  }

  // 확인 문제 정답 확인 프롬프트. 정답은 보내지 않습니다.
  public Prompt quizCheck(String question, String passage, List<String> choices) {
    Map<String, String> values = new HashMap<>();
    values.put("question", question);
    values.put("passage", orNone(passage));
    values.put("choices_text", numberedFromZero(choices));
    return new Prompt(quizCheck.render("system", values), quizCheck.render("user", values));
  }

  // 확인 문제 분류 프롬프트 (수학 분류체계 또는 성취기준 후보)
  public Prompt quizClassify(
      String taxonomyList,
      String standardList,
      String topic,
      String passage,
      String question,
      List<String> choices,
      String previousFailure) {
    Map<String, String> values = new HashMap<>();
    values.put("taxonomy_list", taxonomyList);
    values.put("standard_list", orNone(standardList));
    values.put("topic", orNone(topic));
    values.put("passage", orNone(passage));
    values.put("question", question);
    values.put("choices", numbered(choices));
    values.put("previous_failure", orNone(previousFailure));
    return new Prompt(quizClassify.render("system", values), quizClassify.render("user", values));
  }

  // 수학이 아닌 과목의 변형 생성 프롬프트. 지문이 있으면 같은 지문에 다른 문제를 내게 합니다.
  public Prompt textVariant(TextVariantRequest request) {
    Map<String, String> values = new HashMap<>();
    values.put("subject", orNone(request.subject()));
    values.put("standard_code", request.standardCode());
    values.put("standard_text", orNone(request.standardText()));
    values.put("passage", orNone(request.passage()));
    values.put("source_problem_text", request.sourceProblemText());
    values.put("source_choices", numbered(request.sourceChoices()));
    values.put("source_answer", orNone(request.sourceAnswer()));
    values.put("level", levelLabel(request.level()));
    values.put("level_rule", textVariant.render(levelSection(request.level()), Map.of()));
    values.put("avoid_variants", numbered(request.avoidVariants()));
    values.put("previous_failure", orNone(request.previousFailure()));
    return new Prompt(textVariant.render("system", values), textVariant.render("user", values));
  }

  // 수학이 아닌 과목의 독립 풀이 프롬프트 (G7). 정답 · 해설은 보내지 않습니다.
  public Prompt textSolveCheck(String passage, String problemText, List<String> choices) {
    Map<String, String> values = new HashMap<>();
    values.put("passage", orNone(passage));
    values.put("problem_text", problemText);
    values.put("choices_text", numberedFromZero(choices));
    return new Prompt(
        textSolveCheck.render("system", values), textSolveCheck.render("user", values));
  }

  // 수학이 아닌 과목의 변형 생성에 필요한 값 묶음
  public record TextVariantRequest(
      String subject,
      String standardCode,
      String standardText,
      String passage,
      String sourceProblemText,
      List<String> sourceChoices,
      String sourceAnswer,
      int level,
      List<String> avoidVariants,
      String previousFailure) {}

  // 변형 생성에 필요한 값 묶음
  public record VariantRequest(
      String unitCode,
      String unitName,
      String typeCode,
      String typeName,
      List<String> misconceptions,
      String sourceProblemText,
      List<String> sourceChoices,
      String sourceAnswer,
      int level,
      List<String> avoidVariants,
      String previousFailure) {}

  static String levelSection(int level) {
    return "level:" + level;
  }

  // 레벨을 사람이 읽는 이름으로 (프롬프트용)
  static String levelLabel(int level) {
    return switch (level) {
      case -1 -> "L-1 비계";
      case 0 -> "L0 재인출";
      case 1 -> "L1 변형";
      case 2 -> "L2 심화";
      default -> throw new IllegalArgumentException("레벨은 -1 ~ 2 예요: " + level);
    };
  }

  private static String orNone(String text) {
    return text == null || text.isBlank() ? NONE : text;
  }

  // 목록을 "1) ...", "2) ..." 줄로. 비어 있으면 (없음)
  private static String numbered(List<String> items) {
    if (items == null || items.isEmpty()) {
      return NONE;
    }
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < items.size(); i++) {
      text.append(i + 1).append(") ").append(items.get(i)).append('\n');
    }
    return text.toString().strip();
  }

  // 목록을 "0) ...", "1) ..." 줄로 (답의 번호가 0 부터라서 보여 주는 번호도 0 부터). 비어 있으면 (없음)
  private static String numberedFromZero(List<String> items) {
    if (items == null || items.isEmpty()) {
      return NONE;
    }
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < items.size(); i++) {
      text.append(i).append(") ").append(items.get(i)).append('\n');
    }
    return text.toString().strip();
  }

  // 목록을 "- ..." 줄로. 비어 있으면 (없음)
  private static String bullets(List<String> items) {
    if (items == null || items.isEmpty()) {
      return NONE;
    }
    StringBuilder text = new StringBuilder();
    for (String item : items) {
      text.append("- ").append(item).append('\n');
    }
    return text.toString().strip();
  }
}
