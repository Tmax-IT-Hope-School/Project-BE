package com.ithopeschool.projectbe.domain.variant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 답 테이블. 한 줄 = 과제 하나에 낸 답. (docs/VARIANT-SPEC.md 4장)
// 지금은 과제 하나에 한 번만 답할 수 있어서 assignment_id 가 겹치지 않게 막았습니다.
@Entity
@Table(
    name = "attempts",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_attempts_assignment_id", columnNames = "assignment_id")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attempt {

  // 답 번호
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 누구의 답인지 (users.id)
  @Column(nullable = false)
  private Long userId;

  // 어느 과제의 답인지 (assignments.id)
  @Column(nullable = false)
  private Long assignmentId;

  // 낸 답. MC5 는 선지 번호("0" ~ "4"), SHORT 는 정수 글자
  @Column(nullable = false, length = 20)
  private String submittedAnswer;

  // 맞았는지
  @Column(nullable = false)
  private boolean isCorrect;

  // 푸는 데 걸린 시간(초). 안 보내면 비어 있음
  private Integer timeSpentSec;

  // 답한 순간
  @Column(nullable = false)
  private Instant answeredAt;

  public Attempt(
      Long userId,
      Long assignmentId,
      String submittedAnswer,
      boolean isCorrect,
      Integer timeSpentSec,
      Instant answeredAt) {
    this.userId = userId;
    this.assignmentId = assignmentId;
    this.submittedAnswer = submittedAnswer;
    this.isCorrect = isCorrect;
    this.timeSpentSec = timeSpentSec;
    this.answeredAt = answeredAt;
  }
}
