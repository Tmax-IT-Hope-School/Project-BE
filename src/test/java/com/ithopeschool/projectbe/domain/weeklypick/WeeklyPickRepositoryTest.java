package com.ithopeschool.projectbe.domain.weeklypick;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

// 고른 것을 실제 DB(H2)에 넣고 꺼내 봅니다. 내 것만 · 그 기간 것만 나오는지, 한 주에 한 줄인지 확인합니다.
@DataJpaTest
class WeeklyPickRepositoryTest {

  private static final LocalDate MONDAY = LocalDate.parse("2026-10-26");
  private static final Instant NOW = Instant.parse("2026-10-24T11:00:00Z");
  private static final Long ME = 1L;
  private static final Long OTHER = 2L;

  @Autowired private WeeklyPickRepository weeklyPickRepository;
  @Autowired private EntityManager entityManager;

  private WeeklyPick savePick(Long userId, LocalDate weekStart, String title) {
    return weeklyPickRepository.saveAndFlush(
        new WeeklyPick(userId, weekStart, title, "설명", false, NOW, NOW));
  }

  @Test
  @DisplayName("그 주의 내 것만 찾는다 (같은 주의 남의 것은 안 나온다)")
  void findsMyPickOfTheWeek() {
    WeeklyPick mine = savePick(ME, MONDAY, "내 것");
    savePick(OTHER, MONDAY, "남의 것");

    assertThat(weeklyPickRepository.findByUserIdAndWeekStart(ME, MONDAY))
        .get()
        .extracting(WeeklyPick::getId)
        .isEqualTo(mine.getId());
    assertThat(weeklyPickRepository.findByUserIdAndWeekStart(ME, MONDAY.plusWeeks(1))).isEmpty();
  }

  @Test
  @DisplayName("기간의 내 것만, 오래된 주부터 나온다 (양 끝 날짜 포함)")
  void findsMyPicksBetweenOldestFirst() {
    LocalDate from = MONDAY.minusWeeks(2);
    // 기간 안 (양 끝 포함), 일부러 순서를 섞어 저장합니다.
    WeeklyPick last = savePick(ME, MONDAY, "끝 주");
    WeeklyPick first = savePick(ME, from, "첫 주");
    WeeklyPick middle = savePick(ME, MONDAY.minusWeeks(1), "가운데 주");
    // 기간 밖 · 남의 것
    savePick(ME, from.minusWeeks(1), "기간 앞");
    savePick(ME, MONDAY.plusWeeks(1), "기간 뒤");
    savePick(OTHER, MONDAY.minusWeeks(1), "남의 것");

    List<WeeklyPick> found =
        weeklyPickRepository.findByUserIdAndWeekStartBetweenOrderByWeekStartAsc(ME, from, MONDAY);

    assertThat(found)
        .extracting(WeeklyPick::getId)
        .containsExactly(first.getId(), middle.getId(), last.getId());
  }

  @Test
  @DisplayName("한 사람의 같은 주에 두 줄은 만들 수 없다 (다른 사람은 같은 주여도 된다)")
  void oneRowPerUserAndWeek() {
    savePick(ME, MONDAY, "처음");
    savePick(OTHER, MONDAY, "다른 사람");

    assertThatThrownBy(() -> savePick(ME, MONDAY, "두 번째"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("덮어쓰면 새 값이 저장되고 처음 만든 시각은 그대로다")
  void changeIsSaved() {
    WeeklyPick pick = savePick(ME, MONDAY, "처음");
    Instant later = NOW.plusSeconds(3600);
    pick.change("카드 1장만 하기", "가장 쉬운 카드 하나만", true, later, later);
    weeklyPickRepository.saveAndFlush(pick);
    entityManager.clear();

    WeeklyPick found = weeklyPickRepository.findById(pick.getId()).orElseThrow();

    assertThat(found.getTitle()).isEqualTo("카드 1장만 하기");
    assertThat(found.getHint()).isEqualTo("가장 쉬운 카드 하나만");
    assertThat(found.isSkip()).isTrue();
    assertThat(found.getDecidedAt()).isEqualTo(later);
    assertThat(found.getUpdatedAt()).isEqualTo(later);
    assertThat(found.getCreatedAt()).isEqualTo(NOW);
  }
}
