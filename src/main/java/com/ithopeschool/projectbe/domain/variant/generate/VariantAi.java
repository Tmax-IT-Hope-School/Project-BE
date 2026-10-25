package com.ithopeschool.projectbe.domain.variant.generate;

import java.util.Optional;

// 변형 문제 기능이 AI 를 부르는 창구입니다. 실제로는 GptVariantAi(AiClient 로 GPT 호출)가 이 일을 하고,
// 테스트에서는 정해 둔 답을 돌려주는 가짜를 끼웁니다. (테스트에서 진짜 AI 를 부르지 않는다는 규칙)
//
// 세 가지 모두 못 받으면(꺼져 있음, 시간 초과, 모양이 다름) 빈 값입니다. 에러를 던지지 않습니다.
//   instructions : 프롬프트의 system 칸
//   input        : 프롬프트의 user 칸
//   userId       : 부르게 한 학생 (gpt_usages 에 이 사람으로 남습니다)
public interface VariantAi {

  // AI 를 부를 수 있는 서버인지 (내 컴퓨터에서는 꺼져 있습니다)
  boolean isEnabled();

  // 변형 테이블에 남길 모델 이름
  String modelName();

  Optional<VariantDraft> generate(Long userId, String instructions, String input);

  Optional<ClassificationDraft> classify(Long userId, String instructions, String input);

  Optional<SolveCheckDraft> solveCheck(Long userId, String instructions, String input);

  // 확인 문제를 수학 분류체계 · 성취기준 중 하나로 분류합니다. (docs/VARIANT-SPEC.md 5-1)
  Optional<QuizClassificationDraft> classifyQuiz(Long userId, String instructions, String input);
}
