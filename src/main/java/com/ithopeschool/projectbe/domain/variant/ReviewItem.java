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
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 학습 항목 테이블. 한 줄 = 틀린 원본 하나를 다시 익히는 과정. (docs/VARIANT-SPEC.md 3-1)
//
// 상태가 어떻게 바뀌는지는 ReviewItemRules 에만 있습니다. 이 엔티티는 규칙이 돌려준 값(ReviewItemState)을 받아 적기만 합니다.
//
// ■ "같은 원본에 ACTIVE 항목은 하나뿐" 을 DB 가 지키게 하는 방법
//   activeSourceProblemId 는 ACTIVE 일 때만 원본 번호를 담고, 그 밖에는 비워 둡니다. 이 칸에 유니크를 걸면
//   ACTIVE 끼리만 겹치지 않게 막고, 비어 있는(null) 졸업 항목은 몇 개든 괜찮습니다.
//   (원래 설계의 부분 유니크 인덱스 "where status = 'ACTIVE'" 는 로컬 DB(H2)가 지원하지 않아서 이렇게 했습니다)
@Entity
@Table(
    name = "review_items",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_review_items_active_source_problem_id",
          columnNames = "active_source_problem_id")
    },
    indexes = {
      @Index(name = "idx_review_items_user_id_status", columnList = "user_id, status"),
      @Index(name = "idx_review_items_source_problem_id", columnList = "source_problem_id")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewItem {

  // 항목 번호
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 누구의 항목인지 (users.id)
  @Column(nullable = false)
  private Long userId;

  // 어느 원본의 항목인지 (source_problems.id)
  @Column(nullable = false)
  private Long sourceProblemId;

  // ACTIVE 일 때만 원본 번호, 그 밖에는 비움 (위 설명)
  private Long activeSourceProblemId;

  // 왜 생겼는지 (원본 오답 · 주간 (A) 오답)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ReviewItemOrigin origin;

  // ACTIVE · GRADUATED · MASTERED
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private ReviewItemStatus status;

  // 지금 레벨 (-1, 0, 1)
  @Column(nullable = false)
  private int currentLevel;

  // 지금 레벨에서 연속으로 틀린 수
  @Column(nullable = false)
  private int wrongStreak;

  // 비계(L-1)에서도 계속 틀림 (주간 회고에서 "어필" 할 때 씁니다)
  @Column(nullable = false)
  private boolean needsAttention;

  // 다음에 낼 날 (새벽 4시 기준). ACTIVE 일 때만 있음
  private LocalDate nextDueDate;

  // 졸업한 순간
  private Instant graduatedAt;

  // 주간 심화까지 맞힌 순간
  private Instant masteredAt;

  // 만든 순간
  @Column(nullable = false)
  private Instant createdAt;

  // 마지막으로 바뀐 순간
  @Column(nullable = false)
  private Instant updatedAt;

  public ReviewItem(
      Long userId,
      Long sourceProblemId,
      ReviewItemOrigin origin,
      ReviewItemState state,
      Instant now) {
    this.userId = userId;
    this.sourceProblemId = sourceProblemId;
    this.origin = origin;
    this.createdAt = now;
    apply(state, now);
  }

  // 지금 상태를 값으로 꺼냅니다. (ReviewItemRules 에 넘길 때)
  public ReviewItemState state() {
    return new ReviewItemState(
        status, currentLevel, wrongStreak, needsAttention, nextDueDate, graduatedAt, masteredAt);
  }

  // 규칙이 돌려준 새 상태를 그대로 적습니다.
  public void apply(ReviewItemState state, Instant now) {
    this.status = state.status();
    this.currentLevel = state.level();
    this.wrongStreak = state.wrongStreak();
    this.needsAttention = state.needsAttention();
    this.nextDueDate = state.nextDueDate();
    this.graduatedAt = state.graduatedAt();
    this.masteredAt = state.masteredAt();
    this.activeSourceProblemId = status == ReviewItemStatus.ACTIVE ? sourceProblemId : null;
    this.updatedAt = now;
  }
}
