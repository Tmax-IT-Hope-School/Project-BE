package com.ithopeschool.projectbe.domain.variant;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 변형 문항 DB 창구
public interface VariantRepository extends JpaRepository<Variant, Long> {

  // 아직 아무 과제에도 쓰지 않은, 그 원본 · 그 레벨의 검증된 변형 (먼저 만든 것부터)
  @Query(
      "select v from Variant v where v.sourceProblemId = :sourceProblemId and v.level = :level"
          + " and v.status = :status"
          + " and not exists (select a.id from Assignment a where a.variantId = v.id)"
          + " order by v.id asc")
  List<Variant> findUnassigned(
      @Param("sourceProblemId") Long sourceProblemId,
      @Param("level") int level,
      @Param("status") VariantStatus status);

  // 같은 원본의 그 상태 변형 (최근 것부터). "피해야 할 기존 변형" 과 G6 중복 검사에 씁니다
  List<Variant> findBySourceProblemIdAndStatusOrderByIdDesc(
      Long sourceProblemId, VariantStatus status);

  // 그 순간 이후에 그 원본 · 레벨로 떨어진 시도 수. "오늘 이미 3번 실패했나" 를 볼 때 씁니다
  long countBySourceProblemIdAndLevelAndStatusAndCreatedAtGreaterThanEqual(
      Long sourceProblemId, int level, VariantStatus status, Instant from);

  // 그 원본 · 레벨의 시도 전부 (테스트 · 품질 평가에서 봅니다)
  List<Variant> findBySourceProblemIdAndLevelOrderByIdAsc(Long sourceProblemId, int level);

  Optional<Variant> findByIdAndUserId(Long id, Long userId);
}
