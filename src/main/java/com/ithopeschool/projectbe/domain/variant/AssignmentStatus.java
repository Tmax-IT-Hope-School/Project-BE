package com.ithopeschool.projectbe.domain.variant;

import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.util.Locale;

// 과제의 상태
public enum AssignmentStatus {
  // 아직 답하지 않음
  PENDING,
  // 답함 (과제당 한 번)
  ANSWERED,
  // 답하지 않고 날 · 마감이 지남
  EXPIRED;

  // JSON 으로 주고받을 때는 소문자로 씁니다. (docs/API.md, DB 에는 대문자)
  public String toJson() {
    return name().toLowerCase(Locale.ROOT);
  }

  // 받은 소문자 글자를 값으로 바꿉니다. 모르는 글자면 400 입니다.
  public static AssignmentStatus fromJson(String value) {
    for (AssignmentStatus candidate : values()) {
      if (candidate.toJson().equals(value)) {
        return candidate;
      }
    }
    throw new ApiException(ErrorCode.VALIDATION_ERROR, "status 값을 다시 확인해 주세요");
  }
}
