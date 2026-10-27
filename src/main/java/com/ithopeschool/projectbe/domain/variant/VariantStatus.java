package com.ithopeschool.projectbe.domain.variant;

// 변형 생성 시도의 결과. VERIFIED 만 학생에게 나갑니다.
public enum VariantStatus {
  // 모든 검사를 통과함
  VERIFIED,
  // 검사 하나라도 실패함 (실패 사유는 rejectionReason)
  REJECTED
}
