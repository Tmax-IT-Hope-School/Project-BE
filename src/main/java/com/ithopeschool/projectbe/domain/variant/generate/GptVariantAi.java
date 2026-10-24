package com.ithopeschool.projectbe.domain.variant.generate;

import com.ithopeschool.projectbe.domain.ai.AiClient;
import com.ithopeschool.projectbe.domain.ai.AiQuestion;
import com.ithopeschool.projectbe.domain.ai.AiSchema;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 변형 문제 기능의 AI 호출을 이미 있는 AiClient(OpenAI)로 하는 구현입니다.
// 호출 규칙(키는 서버에만, 부를 때마다 gpt_usages 에 기록, 실패하면 빈 값)은 AiClient 가 지킵니다.
// 여기서는 "무엇을 묻고 어떤 JSON 모양으로 받을지" 만 정합니다. 기록 종류는 모두 VARIANT 입니다.
//
// 변형 생성은 답이 길어서 25초까지 기다리는 askLong 을 씁니다.
// ⚠️ 보내는 글은 수학 문제 글뿐입니다. 이름 · 학교 같은 개인정보는 넣지 않습니다.
@Component
public class GptVariantAi implements VariantAi {

  private final AiClient aiClient;
  // 공용 AI 모델 이름 (app.ai.model). 변형 테이블에 남깁니다
  private final String model;

  public GptVariantAi(AiClient aiClient, @Value("${app.ai.model:}") String model) {
    this.aiClient = aiClient;
    this.model = model == null ? "" : model.trim();
  }

  @Override
  public boolean isEnabled() {
    return aiClient.isEnabled();
  }

  @Override
  public String modelName() {
    return model.isEmpty() ? "unknown" : model;
  }

  @Override
  public Optional<VariantDraft> generate(Long userId, String instructions, String input) {
    AiQuestion question = new AiQuestion("variant", instructions, input, variantSchema());
    return aiClient.askLong(userId, GptUsageKind.VARIANT, question, VariantDraft.class);
  }

  @Override
  public Optional<ClassificationDraft> classify(Long userId, String instructions, String input) {
    AiQuestion question =
        new AiQuestion("variant_classification", instructions, input, classificationSchema());
    return aiClient.ask(userId, GptUsageKind.VARIANT, question, ClassificationDraft.class);
  }

  @Override
  public Optional<SolveCheckDraft> solveCheck(Long userId, String instructions, String input) {
    AiQuestion question =
        new AiQuestion("variant_solve_check", instructions, input, solveCheckSchema());
    return aiClient.askLong(userId, GptUsageKind.VARIANT, question, SolveCheckDraft.class);
  }

  // 변형 문항의 답 모양 (VariantDraft 와 같은 칸)
  static Map<String, Object> variantSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("problemText", AiSchema.string());
    properties.put("format", AiSchema.enumOf(List.of("MC5", "SHORT")));
    properties.put("choices", AiSchema.nullableList(AiSchema.string()));
    properties.put("answerIndex", AiSchema.nullableInteger());
    properties.put("shortAnswer", AiSchema.nullableInteger());
    properties.put("distractorRationales", AiSchema.nullableList(AiSchema.string()));
    properties.put("solution", AiSchema.string());
    properties.put("verificationCode", AiSchema.string());
    properties.put("levelJustification", AiSchema.string());
    return AiSchema.object(properties);
  }

  // 분류 답 모양 (ClassificationDraft 와 같은 칸)
  static Map<String, Object> classificationSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("unitCode", AiSchema.string());
    properties.put("typeCode", AiSchema.string());
    properties.put("confidence", Map.of("type", "number"));
    properties.put("figureRequired", Map.of("type", "boolean"));
    properties.put("reason", AiSchema.string());
    return AiSchema.object(properties);
  }

  // 독립 풀이 답 모양 (SolveCheckDraft 와 같은 칸)
  static Map<String, Object> solveCheckSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("answerIndex", AiSchema.nullableInteger());
    properties.put("shortAnswer", AiSchema.nullableInteger());
    properties.put("reasoning", AiSchema.string());
    return AiSchema.object(properties);
  }
}
