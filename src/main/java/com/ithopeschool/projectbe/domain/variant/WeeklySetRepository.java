package com.ithopeschool.projectbe.domain.variant;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 주간 세트 DB 창구
public interface WeeklySetRepository extends JpaRepository<WeeklySet, Long> {

  // 그 학생의 그 주 세트 (없으면 빈 값). weekStart 는 돌아보는 주의 월요일
  Optional<WeeklySet> findByUserIdAndWeekStart(Long userId, LocalDate weekStart);

  // 그 학생의 가장 최근 세트 (주를 정하지 않고 볼 때)
  Optional<WeeklySet> findFirstByUserIdOrderByWeekStartDesc(Long userId);
}
