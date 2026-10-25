package com.ithopeschool.projectbe.domain.quizresult;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// QuizResultRepository 가 진짜 DB(H2)에서 "내 것, 그 기간" 만 날짜 순으로 찾는지,
// 문제마다 걸린 시간이 한 칸에 저장됐다가 숫자 목록으로 돌아오는지 확인합니다.
@DataJpaTest
class QuizResultRepositoryTest {

  @Autowired private QuizResultRepository repository;
  @Autowired private TestEntityManager em;

  private void save(Long userId, LocalDate date, List<Integer> seconds) {
    em.persistAndFlush(new QuizResult(userId, 3L, "함수", date, 1, seconds.size(), seconds));
  }

  @Test
  @DisplayName("기간 안의 내 결과만 날짜 순으로 찾고, 걸린 시간은 목록으로 돌아온다")
  void findInPeriod() {
    save(1L, LocalDate.of(2026, 10, 24), List.of(45, 30, 62));
    save(1L, LocalDate.of(2026, 10, 19), List.of(10));
    save(1L, LocalDate.of(2026, 10, 18), List.of(10));
    save(2L, LocalDate.of(2026, 10, 20), List.of(10));
    em.clear();

    List<QuizResult> found =
        repository.findByUserIdAndQuizDateBetweenOrderByQuizDateAscIdAsc(
            1L, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25));

    assertThat(found)
        .extracting(QuizResult::getQuizDate)
        .containsExactly(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 24));
    assertThat(found.get(1).getQuestionSeconds()).isEqualTo("45,30,62");
    assertThat(found.get(1).questionSecondsList()).containsExactly(45, 30, 62);
    assertThat(found.get(1).totalSeconds()).isEqualTo(137);
  }
}
