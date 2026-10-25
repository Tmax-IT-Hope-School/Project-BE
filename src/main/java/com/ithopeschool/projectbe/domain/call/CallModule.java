package com.ithopeschool.projectbe.domain.call;

import java.util.Optional;

// 통화 대본의 "조건부 모듈" 입니다. 통화 한 번에 최대 1개이고, FE 가 골라서 학생 카드의 callModule 로 보냅니다.
// (최종 기획서 「4.5 상담」 통화 4번, FE src/lib/callModules.ts · #414, BE #28)
// 2026-10-25 #434 부터 음성 통화는 이 모듈을 "오늘 주제" 로 씁니다. (CallTopic)
//
// FE 의 우선순위 (FE 가 골라 보내서 서버는 고르지 않습니다)
//   1. safetyBrake        안전 브레이크 — 위기 표현을 감지했을 때        → 위로하는 통화 (관리 강도 0)
//   2. recovery           회복 모드(What if) — 오늘 관리 단계가 회복(0)  → 위로하는 통화 (관리 강도 0)
//   3. review4            4칸 회고 — 지난 통화 이후 재발 신호가 났을 때
//   4. goalCheck          목표 점검 — 2주마다
//   5. distortionFeedback 왜곡 경향 피드백 — 주 1~2회
//   6. predictReveal      예측 후 공개 질문 — 주 1회
//
// ⚠️ FE 에 모듈을 더하면 여기에도 더해야 합니다. 모르는 글자는 null(모듈 없음)로 받습니다. (CallSessionRequest.Card)
public enum CallModule {
  SAFETY_BRAKE("safetyBrake"),
  RECOVERY("recovery"),
  REVIEW4("review4"),
  GOAL_CHECK("goalCheck"),
  DISTORTION_FEEDBACK("distortionFeedback"),
  PREDICT_REVEAL("predictReveal");

  // FE 와 주고받는 글자
  private final String code;

  CallModule(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  // FE 글자 → 모듈. 없거나 모르는 글자면 빈 값입니다.
  //   "review4" → REVIEW4   /   "lazy" → 빈 값   /   null → 빈 값
  public static Optional<CallModule> from(String code) {
    for (CallModule module : values()) {
      if (module.code.equals(code)) {
        return Optional.of(module);
      }
    }
    return Optional.empty();
  }

  // 위로하는 통화로 이어지는 모듈인지 (안전 브레이크 · 회복 모드). 안전이 어떤 설정보다 먼저입니다. (CLAUDE.md 4장)
  public boolean isComfort() {
    return this == SAFETY_BRAKE || this == RECOVERY;
  }
}
