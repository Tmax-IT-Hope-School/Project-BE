package com.ithopeschool.projectbe.domain.variant;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 학습 항목 DB 창구
public interface ReviewItemRepository extends JpaRepository<ReviewItem, Long> {

  // 내 항목 하나 (다른 사람의 항목이면 빈 값)
  Optional<ReviewItem> findByIdAndUserId(Long id, Long userId);

  // 그 원본의 ACTIVE 항목 (있으면 하나뿐)
  Optional<ReviewItem> findBySourceProblemIdAndStatus(
      Long sourceProblemId, ReviewItemStatus status);

  // 오답노트 현황: 내 항목 전부 / 그 상태만 (만든 순서)
  List<ReviewItem> findByUserIdOrderByCreatedAtAscIdAsc(Long userId);

  List<ReviewItem> findByUserIdAndStatusOrderByCreatedAtAscIdAsc(
      Long userId, ReviewItemStatus status);

  // 일일 출제 대상: 그날까지 낼 차례가 된 ACTIVE 항목을 우선순위대로 (docs/VARIANT-SPEC.md 3-3)
  //   주의 표시 먼저 → 다음 날짜가 오래된 것 먼저 → 먼저 만든 것 먼저
  @Query(
      "select r from ReviewItem r where r.userId = :userId and r.status = :status"
          + " and r.nextDueDate <= :date"
          + " order by r.needsAttention desc, r.nextDueDate asc, r.createdAt asc, r.id asc")
  List<ReviewItem> findDueInPriorityOrder(
      @Param("userId") Long userId,
      @Param("status") ReviewItemStatus status,
      @Param("date") LocalDate date);

  // 일일 예약 작업: 그날 낼 항목이 있는 학생들
  @Query(
      "select distinct r.userId from ReviewItem r where r.status = :status"
          + " and r.nextDueDate <= :date")
  List<Long> findUserIdsWithDue(
      @Param("status") ReviewItemStatus status, @Param("date") LocalDate date);

  // 주간 (B): 졸업한 항목을 졸업이 오래된 순서로
  List<ReviewItem> findByUserIdAndStatusOrderByGraduatedAtAscIdAsc(
      Long userId, ReviewItemStatus status);

  // 주간 세트를 만들 학생: 졸업한 항목이 있는 사람
  @Query("select distinct r.userId from ReviewItem r where r.status = :status")
  List<Long> findUserIdsWithStatus(@Param("status") ReviewItemStatus status);
}
