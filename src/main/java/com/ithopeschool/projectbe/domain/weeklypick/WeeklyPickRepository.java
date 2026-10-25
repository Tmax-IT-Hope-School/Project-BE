package com.ithopeschool.projectbe.domain.weeklypick;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// weekly_picks 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 것은 찾지 못합니다. (이슈 #406)
public interface WeeklyPickRepository extends JpaRepository<WeeklyPick, Long> {

  // 내 그 주(월요일)에 고른 것. 아직 안 골랐으면 빈 Optional 입니다. (덮어쓸지 새로 만들지 정할 때 씁니다)
  Optional<WeeklyPick> findByUserIdAndWeekStart(Long userId, LocalDate weekStart);

  // 내 from ~ to (둘 다 포함) 사이에 시작한 주에 고른 것들, 오래된 주부터.
  // Between 은 양 끝을 포함합니다. (week_start >= from and week_start <= to)
  List<WeeklyPick> findByUserIdAndWeekStartBetweenOrderByWeekStartAsc(
      Long userId, LocalDate from, LocalDate to);
}
