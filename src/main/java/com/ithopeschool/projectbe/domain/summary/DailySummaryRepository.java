package com.ithopeschool.projectbe.domain.summary;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// daily_summaries 테이블의 창구입니다. 모든 메서드에 userId 조건이 붙어 있습니다.
public interface DailySummaryRepository extends JpaRepository<DailySummary, Long> {

  // 그날의 내 요약. 아직 만들지 않았으면 빈 Optional 입니다.
  Optional<DailySummary> findByUserIdAndSummaryDate(Long userId, LocalDate summaryDate);

  // 기간 안의 내 요약들, 날짜 순서대로 (분석 · 리포트가 씁니다)
  List<DailySummary> findByUserIdAndSummaryDateBetweenOrderBySummaryDateAsc(
      Long userId, LocalDate from, LocalDate to);
}
