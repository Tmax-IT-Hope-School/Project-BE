package com.ithopeschool.projectbe.domain.variant;

import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.util.Locale;

// 문제 형식: 5지선다 또는 단답형(1~999 자연수)
public enum VariantFormat {
  // 5지선다
  MC5,
  // 단답형 (정답은 1 이상 999 이하 자연수)
  SHORT;

  // JSON 으로 주고받을 때는 소문자로 씁니다. (docs/API.md, DB 에는 대문자)
  public String toJson() {
    return name().toLowerCase(Locale.ROOT);
  }

  // 받은 소문자 글자를 값으로 바꿉니다. 모르는 글자면 400 입니다.
  public static VariantFormat fromJson(String value) {
    for (VariantFormat candidate : values()) {
      if (candidate.toJson().equals(value)) {
        return candidate;
      }
    }
    throw new ApiException(ErrorCode.VALIDATION_ERROR, "format 값을 다시 확인해 주세요");
  }
}
