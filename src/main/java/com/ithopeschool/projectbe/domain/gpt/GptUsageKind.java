package com.ithopeschool.projectbe.domain.gpt;

// GPT 를 부른 종류입니다. (이슈 #33) 지금 GPT 를 실제로 부르는 곳만 있습니다.
// 인사이트 문장 · 개선안 · 일기 분석처럼 GPT 를 부르는 기능이 새로 생기면 여기에 한 줄을 더합니다.
public enum GptUsageKind {
  // AI 와 음성 통화 (분당 과금. 통화 시간을 따로 기록합니다)
  CALL,
  // 통화가 끝난 뒤 대화를 요약 (CallReviewClient)
  CALL_REVIEW,
  // 주간·월간·어제 한 줄 코치 문단과 입력 안전 판단 (CoachAiClient)
  COACH,
  // 말로 계획 세우기: 문장 → 계획 초안 (PlanParseClient, 이슈 #86)
  PLAN_PARSE,
  // 상담 채팅에서 AI 가 한 번 답한 것 (ChatAiClient, 이슈 #238). 채팅을 열 때 첫 인사도 한 번입니다.
  CHAT,
  // 작은 AI 기능: 카드의 체크리스트 추천 · 첫 준비 행동 (AiClient, 이슈 #236)
  //   (하루 한도가 있던 때는 위의 글 호출과 한도를 따로 셌습니다. 한도는 2026-10-24 에 없앴습니다)
  CHECKLIST,
  // 작은 AI 기능 묶음: 예상 실패 원인 선지 · 개선안 문장 다듬기 등 (AiClient, 이슈 #237 · #51)
  //   기능마다 종류를 따로 만들면 그때마다 운영 DB 의 허용값을 사람이 고쳐야 해서, #237 의 기능들은 이 하나를 같이 씁니다.
  ASSIST,
  // 변형 문제: 변형 생성 · 원본 분류 · 독립 풀이 검사 (GptVariantAi, docs/VARIANT-SPEC.md)
  //   변형 하나에 AI 호출이 여러 번(최대 3번 + 검사)이라 비용을 따로 보려고 종류를 나눴습니다.
  //   운영 DB 의 허용값은 db/migrations/22-variant-problems.sql 이 늘립니다.
  VARIANT
}
