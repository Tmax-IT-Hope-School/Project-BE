package com.ithopeschool.projectbe.domain.variant;

import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.util.Locale;

// 과제의 종류 (docs/VARIANT-SPEC.md 3장)
public enum AssignmentKind {
  // 틀린 문제의 일일 변형
  DAILY,
  // 주간 (A) 맞은 문제의 대표 유형 심화
  WEEKLY_CORRECT_TYPE,
  // 주간 (B) 극복한 오답의 심화
  WEEKLY_WRONG_DEEP;

  // JSON 으로 주고받을 때는 소문자로 씁니다. (docs/API.md, DB 에는 대문자)
  public String toJson() {
    return name().toLowerCase(Locale.ROOT);
  }

  // 받은 소문자 글자를 값으로 바꿉니다. 모르는 글자면 400 입니다.
  public static AssignmentKind fromJson(String value) {
    for (AssignmentKind candidate : values()) {
      if (candidate.toJson().equals(value)) {
        return candidate;
      }
    }
    throw new ApiException(ErrorCode.VALIDATION_ERROR, "kind 값을 다시 확인해 주세요");
  }
}
