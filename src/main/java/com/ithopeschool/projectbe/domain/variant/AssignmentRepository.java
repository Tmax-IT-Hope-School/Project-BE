package com.ithopeschool.projectbe.domain.variant;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 과제 DB 창구
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

  // 내 과제 하나 (다른 사람의 과제면 빈 값)
  Optional<Assignment> findByIdAndUserId(Long id, Long userId);

  // 그날 낸 그 종류의 내 과제 (낸 순서)
  List<Assignment> findByUserIdAndKindAndAssignedDateOrderByIdAsc(
      Long userId, AssignmentKind kind, LocalDate assignedDate);

  // 그 항목에 그날 일일 과제를 이미 냈는지
  boolean existsByDailyReviewItemIdAndAssignedDate(Long dailyReviewItemId, LocalDate date);

  // 주간 세트의 과제 (낸 순서)
  List<Assignment> findByWeeklySetIdOrderByIdAsc(Long weeklySetId);

  // 주간 세트에 그 원본 · 종류의 과제를 이미 냈는지
  boolean existsByWeeklySetIdAndSourceProblemIdAndKind(
      Long weeklySetId, Long sourceProblemId, AssignmentKind kind);

  // 마감이 지난 PENDING 과제를 한꺼번에 EXPIRED 로 바꿉니다. 바꾼 줄 수를 돌려줍니다.
  // 일일 과제는 마감 날 = 낸 날이라서, 날이 바뀌면 여기에 걸립니다.
  @Modifying
  @Query(
      "update Assignment a set a.status = :expired"
          + " where a.status = :pending and a.dueDate < :today")
  int expireOverdue(
      @Param("pending") AssignmentStatus pending,
      @Param("expired") AssignmentStatus expired,
      @Param("today") LocalDate today);
}
