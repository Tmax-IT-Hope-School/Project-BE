package com.ithopeschool.projectbe.domain.settings;

// 설정 값을 누가(무엇을 근거로) 정했는지입니다. (이슈 #227, 기획서 4.8 「근거」)
// FE 의 source('test' | 'data' | 'user') 와 같은 뜻입니다.
public enum ChangeSource {
  // 온보딩 심리검사 결과로 AI 가 정함 (첫 2주)
  TEST,
  // 최근 데이터로 AI 가 정함 (2주 뒤)
  DATA,
  // 사용자가 직접 정함. 이 칸은 잠기고, AI 는 다시 바꾸지 않습니다.
  USER
}
