package com.ithopeschool.projectbe.domain.variant;

import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.util.Locale;

// 원본 문항을 변형 출제에 쓸 수 있는지 (docs/VARIANT-SPEC.md 5장)
public enum SupportStatus {
  // 분류가 끝나서 쓸 수 있음
  SUPPORTED,
  // 분류기가 아직 답하지 않음 (AI 가 꺼져 있는 등). 출제하지 않습니다
  PENDING_CLASSIFICATION,
  // 분류기 자신감이 낮거나 코드가 틀림. 사람이 코드를 정해 줘야 합니다
  NEEDS_REVIEW,
  // 그림 · 그래프가 있어야 풀 수 있음. 저장만 하고 출제하지 않습니다
  UNSUPPORTED_FIGURE;

  // JSON 으로 주고받을 때는 소문자로 씁니다. (docs/API.md, DB 에는 대문자)
  public String toJson() {
    return name().toLowerCase(Locale.ROOT);
  }

  // 받은 소문자 글자를 값으로 바꿉니다. 모르는 글자면 400 입니다.
  public static SupportStatus fromJson(String value) {
    for (SupportStatus candidate : values()) {
      if (candidate.toJson().equals(value)) {
        return candidate;
      }
    }
    throw new ApiException(ErrorCode.VALIDATION_ERROR, "supportStatus 값을 다시 확인해 주세요");
  }
}
