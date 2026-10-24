package com.ithopeschool.projectbe.domain.summary;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 하루 요약의 "과목별 순공" 한 줄입니다. 딸린 테이블 daily_summary_subjects 의 한 줄. (이슈 #34)
// 엔티티가 아니라 하루 요약(DailySummary)에 매달린 값이라서 @Embeddable 입니다. 그래서 고유 번호(id)가 없습니다.
//
// 과목 없이 켠 공부 기록(카드로 켠 타이머)은 과목 번호를 비운 줄 하나로 모읍니다.
// 그래서 그날 줄들의 분을 모두 더하면 항상 요약의 순공(studyMinutes)과 같습니다. (담당 학생이 고름)
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailySummarySubject {

  // 어느 과목인지(subjects.id). 과목 없이 공부한 시간이면 비어 있습니다.
  private Long subjectId;

  // 그 과목의 순공시간(분). 일시정지한 시간은 뺀 값입니다.
  @Column(nullable = false)
  private int studyMinutes;

  public DailySummarySubject(Long subjectId, int studyMinutes) {
    this.subjectId = subjectId;
    this.studyMinutes = studyMinutes;
  }
}
