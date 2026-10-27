package com.ithopeschool.projectbe.domain.variant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 과제 테이블. 한 줄 = 학생에게 낸 변형 문항 하나. (docs/VARIANT-SPEC.md 4장)
//
// 과제는 반드시 검사를 통과한(VERIFIED) 변형만 가리킵니다. (만드는 곳: VariantAssigner)
// 변형 하나는 과제 하나에만 씁니다. 그래서 variant_id 가 겹치지 않게 막았습니다.
//
// ■ 겹치면 안 되는 것들을 DB 가 지키게 하는 방법
//   - 일일 과제는 "한 항목에 하루 한 문항" 입니다. dailyReviewItemId 는 일일 과제일 때만 항목 번호를 담고(주간은 비움),
//     (daily_review_item_id, assigned_date) 에 유니크를 겁니다. 비어 있는(null) 주간 과제끼리는 겹친다고 보지 않습니다.
//   - 주간 과제는 "한 세트에 같은 원본 · 같은 종류 하나" 입니다. (weekly_set_id, source_problem_id, kind) 유니크.
//     일일 과제는 weekly_set_id 가 비어 있어서 이 검사에 걸리지 않습니다.
@Entity
@Table(
    name = "assignments",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_assignments_variant_id", columnNames = "variant_id"),
      @UniqueConstraint(
          name = "uk_assignments_daily_review_item_id_assigned_date",
          columnNames = {"daily_review_item_id", "assigned_date"}),
      @UniqueConstraint(
          name = "uk_assignments_weekly_set_id_source_problem_id_kind",
          columnNames = {"weekly_set_id", "source_problem_id", "kind"})
    },
    indexes = {
      @Index(name = "idx_assignments_user_id_assigned_date", columnList = "user_id, assigned_date"),
      @Index(name = "idx_assignments_status_due_date", columnList = "status, due_date")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Assignment {

  // 과제 번호
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 누구의 과제인지 (users.id)
  @Column(nullable = false)
  private Long userId;

  // 낸 변형 (variants.id, 반드시 VERIFIED)
  @Column(nullable = false)
  private Long variantId;

  // 이어진 학습 항목 (review_items.id). 일일 과제와 주간 (B) 에 있고, 주간 (A) 는 비어 있음
  private Long reviewItemId;

  // 일일 과제일 때만 reviewItemId 와 같은 값 (위 설명). 주간은 비어 있음
  private Long dailyReviewItemId;

  // 원본 (source_problems.id)
  @Column(nullable = false)
  private Long sourceProblemId;

  // 일일 · 주간 (A) · 주간 (B)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private AssignmentKind kind;

  // 주간 과제의 세트 (weekly_sets.id). 일일 과제는 비어 있음
  private Long weeklySetId;

  // 낸 날 (새벽 4시 기준)
  @Column(nullable = false)
  private LocalDate assignedDate;

  // 마감 날. 일일 과제는 낸 날과 같고, 주간 과제는 세트의 마감 날
  @Column(nullable = false)
  private LocalDate dueDate;

  // PENDING · ANSWERED · EXPIRED
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private AssignmentStatus status;

  private Assignment(
      Long userId,
      Long variantId,
      Long reviewItemId,
      Long sourceProblemId,
      AssignmentKind kind,
      Long weeklySetId,
      LocalDate assignedDate,
      LocalDate dueDate) {
    this.userId = userId;
    this.variantId = variantId;
    this.reviewItemId = reviewItemId;
    this.dailyReviewItemId = kind == AssignmentKind.DAILY ? reviewItemId : null;
    this.sourceProblemId = sourceProblemId;
    this.kind = kind;
    this.weeklySetId = weeklySetId;
    this.assignedDate = assignedDate;
    this.dueDate = dueDate;
    this.status = AssignmentStatus.PENDING;
  }

  // 일일 과제: 그날 하루가 마감입니다.
  public static Assignment daily(
      Long userId, Long variantId, Long reviewItemId, Long sourceProblemId, LocalDate date) {
    return new Assignment(
        userId, variantId, reviewItemId, sourceProblemId, AssignmentKind.DAILY, null, date, date);
  }

  // 주간 과제: 세트의 마감 날까지입니다. (A) 는 reviewItemId 가 비어 있습니다.
  public static Assignment weekly(
      Long userId,
      Long variantId,
      Long reviewItemId,
      Long sourceProblemId,
      AssignmentKind kind,
      Long weeklySetId,
      LocalDate assignedDate,
      LocalDate dueDate) {
    if (kind == AssignmentKind.DAILY) {
      throw new IllegalArgumentException("주간 과제의 종류가 아니에요");
    }
    return new Assignment(
        userId, variantId, reviewItemId, sourceProblemId, kind, weeklySetId, assignedDate, dueDate);
  }

  // 마감이 지났는지 (오늘이 마감 날보다 뒤면 지난 것)
  public boolean isOverdue(LocalDate today) {
    return today.isAfter(dueDate);
  }

  public void markAnswered() {
    this.status = AssignmentStatus.ANSWERED;
  }

  public void markExpired() {
    this.status = AssignmentStatus.EXPIRED;
  }
}
