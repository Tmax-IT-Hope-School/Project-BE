package com.ithopeschool.projectbe.domain.variant;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 원본 문항 DB 창구
public interface SourceProblemRepository extends JpaRepository<SourceProblem, Long> {

  // 내 원본 하나 (다른 사람의 원본이면 빈 값)
  Optional<SourceProblem> findByIdAndUserId(Long id, Long userId);

  // 주간 (A): 그 기간에 맞힌, 출제에 쓸 수 있는 원본 (푼 순간 순서)
  @Query(
      "select s from SourceProblem s where s.userId = :userId and s.isCorrect = true"
          + " and s.supportStatus = :status and s.solvedAt >= :from and s.solvedAt < :to"
          + " order by s.solvedAt asc, s.id asc")
  List<SourceProblem> findCorrectBetween(
      @Param("userId") Long userId,
      @Param("status") SupportStatus status,
      @Param("from") Instant from,
      @Param("to") Instant to);

  // 주간 세트를 만들 학생: 그 기간에 맞힌 원본이 있는 사람
  @Query(
      "select distinct s.userId from SourceProblem s where s.isCorrect = true"
          + " and s.supportStatus = :status and s.solvedAt >= :from and s.solvedAt < :to")
  List<Long> findUserIdsWithCorrectBetween(
      @Param("status") SupportStatus status, @Param("from") Instant from, @Param("to") Instant to);
}
