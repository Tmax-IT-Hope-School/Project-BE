package com.ithopeschool.projectbe.domain.summary;

import com.ithopeschool.projectbe.domain.care.MissReason;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 하루 요약의 "멈춘 이유" 한 줄입니다. 딸린 테이블 daily_summary_stop_reasons 의 한 줄. (이슈 #34)
// 타이머를 멈출 때마다 한 줄이 아니라, 이유마다 한 줄 + 몇 번인지로 적습니다. (담당 학생이 고름: 세기 편해서)
//   피곤함 · 딴짓 · 피곤함  →  (피곤함, 2) / (딴짓, 1)
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailySummaryStopReason {

  // 목표 전에 타이머를 멈춘 이유 (피곤함 · 일정 · 폰 …, 체크인의 "못 채운 이유" 와 같은 종류)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MissReason stopReason;

  // 그날 이 이유로 멈춘 횟수
  @Column(nullable = false)
  private int stopCount;

  public DailySummaryStopReason(MissReason stopReason, int stopCount) {
    this.stopReason = stopReason;
    this.stopCount = stopCount;
  }
}
