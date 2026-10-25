package com.ithopeschool.projectbe.domain.quizresult;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// quiz_results 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 결과가 섞이지 않습니다.
public interface QuizResultRepository extends JpaRepository<QuizResult, Long> {

  // from ~ to (두 날짜 모두 포함) 의 내 퀴즈 결과를 날짜 · 번호 순서로 찾습니다. 없으면 빈 목록입니다.
  List<QuizResult> findByUserIdAndQuizDateBetweenOrderByQuizDateAscIdAsc(
      Long userId, LocalDate from, LocalDate to);
}
