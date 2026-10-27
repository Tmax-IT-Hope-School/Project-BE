package com.ithopeschool.projectbe.domain.variant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 주간 심화 세트 테이블. 한 줄 = 한 학생의 한 주 세트. (docs/VARIANT-SPEC.md 3-4)
// 세트에 든 문항은 과제(assignments)의 weeklySetId 로 이어집니다.
// 같은 주 세트는 하나뿐이라서 (user_id, week_start) 가 겹치지 않게 막았습니다. 예약 작업이 두 번 돌아도 세트는 하나입니다.
@Entity
@Table(
    name = "weekly_sets",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_weekly_sets_user_id_week_start",
          columnNames = {"user_id", "week_start"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklySet {

  // 세트 번호
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 누구의 세트인지 (users.id)
  @Column(nullable = false)
  private Long userId;

  // 어느 주의 세트인지: 돌아보는 주의 월요일 (새벽 4시 기준 날짜). 만든 날(다음 주 월요일)이 아닙니다
  @Column(nullable = false)
  private LocalDate weekStart;

  // 마감 날 (새벽 4시 기준). 이날까지 답하지 않으면 EXPIRED
  @Column(nullable = false)
  private LocalDate dueDate;

  // 만든 순간
  @Column(nullable = false)
  private Instant createdAt;

  public WeeklySet(Long userId, LocalDate weekStart, LocalDate dueDate, Instant createdAt) {
    this.userId = userId;
    this.weekStart = weekStart;
    this.dueDate = dueDate;
    this.createdAt = createdAt;
  }
}
