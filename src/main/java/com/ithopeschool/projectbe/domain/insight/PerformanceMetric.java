package com.ithopeschool.projectbe.domain.insight;

import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;

// 조건-성과 분석(#38)에서 "성과" 를 무엇으로 볼지입니다.
// FE 와 주고받을 때는 value 글자("studyMinutes")를 씁니다.
public enum PerformanceMetric {
  // 그날 순공시간(분)
  STUDY_MINUTES("studyMinutes"),
  // 그날 계획 달성률(0 ~ 100). 계획이 없던 날은 계산할 수 없어서 빠집니다.
  ACHIEVEMENT_RATE("achievementRate");

  // FE 와 주고받는 글자
  private final String value;

  PerformanceMetric(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  // 요청에 온 글자를 enum 으로 바꿉니다. 모르는 글자면 400 입니다.
  public static PerformanceMetric from(String value) {
    for (PerformanceMetric metric : values()) {
      if (metric.value.equals(value)) {
        return metric;
      }
    }
    throw new ApiException(
        ErrorCode.VALIDATION_ERROR, "metric 은 studyMinutes 또는 achievementRate 로 보내 주세요");
  }
}
