package com.ithopeschool.projectbe.domain.subject;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.ChecklistItem;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.session.StudySession;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// SubjectRepository 가 진짜 DB(H2)에서 "내 것만" 정확히 찾는지 확인합니다.
// 두 사람(1번, 2번)의 과목을 섞어 넣어 두고, 남의 것이 딸려 나오지 않는지 봅니다.
@DataJpaTest
class SubjectRepositoryTest {

  @Autowired private SubjectRepository subjectRepository;
  @Autowired private TestEntityManager em;

  private static final Instant START = Instant.parse("2026-10-12T01:00:00Z");

  private Subject save(Long userId, String name, SubjectColor color) {
    return em.persistAndFlush(new Subject(userId, name, color));
  }

  // ---- 목록 ----

  @Test
  @DisplayName("내 과목만, 만든 순서대로 나온다")
  void findByUserIdOrderById() {
    save(1L, "수학", SubjectColor.RED);
    save(2L, "국어", SubjectColor.BLUE);
    save(1L, "영어", SubjectColor.GREEN);
    save(1L, "과학", SubjectColor.ORANGE);
    em.clear();

    assertThat(subjectRepository.findByUserIdOrderById(1L))
        .extracting(Subject::getName)
        .containsExactly("수학", "영어", "과학");
    assertThat(subjectRepository.findByUserIdOrderById(2L))
        .extracting(Subject::getName)
        .containsExactly("국어");
  }

  @Test
  @DisplayName("과목이 없는 사람은 빈 목록")
  void findByUserIdEmpty() {
    save(1L, "수학", SubjectColor.RED);

    assertThat(subjectRepository.findByUserIdOrderById(999L)).isEmpty();
  }

  // ---- 번호로 하나 찾기 ----

  @Test
  @DisplayName("내 과목은 번호로 찾아지고, 남의 과목 번호나 없는 번호는 빈 Optional")
  void findByIdAndUserId() {
    Subject mine = save(1L, "수학", SubjectColor.RED);
    Subject others = save(2L, "국어", SubjectColor.BLUE);

    assertThat(subjectRepository.findByIdAndUserId(mine.getId(), 1L)).contains(mine);
    // 번호는 실제로 있어도, 내 것이 아니면 없는 것과 똑같이 취급됩니다.
    assertThat(subjectRepository.findByIdAndUserId(others.getId(), 1L)).isEmpty();
    assertThat(subjectRepository.findByIdAndUserId(99999L, 1L)).isEmpty();
  }

  // ---- 이름 겹침 ----

  @Test
  @DisplayName("같은 사람의 같은 이름만 겹침이고, 다른 사람의 같은 이름이나 다른 이름은 아니다")
  void existsByUserIdAndName() {
    save(1L, "수학", SubjectColor.RED);
    save(2L, "영어", SubjectColor.RED);

    assertThat(subjectRepository.existsByUserIdAndName(1L, "수학")).isTrue();
    assertThat(subjectRepository.existsByUserIdAndName(2L, "수학")).isFalse();
    assertThat(subjectRepository.existsByUserIdAndName(1L, "영어")).isFalse();
    assertThat(subjectRepository.existsByUserIdAndName(1L, "수학 ")).isFalse();
  }

  // ---- 계획·기록이 있는 과목인지 ----

  @Test
  @DisplayName("이 과목으로 만든 내 계획이 있으면 true")
  void hasPlans() {
    Subject math = save(1L, "수학", SubjectColor.RED);
    Subject english = save(1L, "영어", SubjectColor.BLUE);
    em.persistAndFlush(new StudyPlan(1L, math.getId(), LocalDate.of(2026, 10, 12), "문제집", 60));

    assertThat(subjectRepository.hasPlans(1L, math.getId())).isTrue();
    // 계획이 다른 과목에 붙어 있으면 이 과목은 false 입니다.
    assertThat(subjectRepository.hasPlans(1L, english.getId())).isFalse();
  }

  @Test
  @DisplayName("이 과목으로 공부한 내 기록이 있으면 true")
  void hasStudySessions() {
    Subject math = save(1L, "수학", SubjectColor.RED);
    Subject english = save(1L, "영어", SubjectColor.BLUE);
    em.persistAndFlush(new StudySession(1L, math.getId(), START, START.plusSeconds(3600), 0));

    assertThat(subjectRepository.hasStudySessions(1L, math.getId())).isTrue();
    assertThat(subjectRepository.hasStudySessions(1L, english.getId())).isFalse();
  }

  @Test
  @DisplayName("계획도 기록도 없는 새 과목은 둘 다 false")
  void unusedSubject() {
    Subject math = save(1L, "수학", SubjectColor.RED);

    assertThat(subjectRepository.hasPlans(1L, math.getId())).isFalse();
    assertThat(subjectRepository.hasStudySessions(1L, math.getId())).isFalse();
  }

  @Test
  @DisplayName("다른 사람의 계획·기록은 세지 않는다 (user_id 조건)")
  void otherUsersRecordsAreNotCounted() {
    Subject math = save(1L, "수학", SubjectColor.RED);
    // 2번 사람의 계획과 기록이 같은 과목 번호를 가리키고 있어도, 1번 사람 기준으로는 없는 것입니다.
    em.persistAndFlush(new StudyPlan(2L, math.getId(), LocalDate.of(2026, 10, 12), "문제집", 60));
    em.persistAndFlush(new StudySession(2L, math.getId(), START, START.plusSeconds(3600), 0));

    assertThat(subjectRepository.hasPlans(1L, math.getId())).isFalse();
    assertThat(subjectRepository.hasStudySessions(1L, math.getId())).isFalse();
    assertThat(subjectRepository.hasPlans(2L, math.getId())).isTrue();
    assertThat(subjectRepository.hasStudySessions(2L, math.getId())).isTrue();
  }

  // ---- 바꾸기 · 지우기 ----

  @Test
  @DisplayName("이름과 색을 바꾸고 반영하면 DB 에서 다시 꺼내도 바뀌어 있다")
  void changeIsSaved() {
    Subject math = save(1L, "수학", SubjectColor.RED);

    math.changeName("국어");
    math.changeColor(SubjectColor.PURPLE);
    em.flush();
    em.clear();

    Subject found = subjectRepository.findByIdAndUserId(math.getId(), 1L).orElseThrow();
    assertThat(found.getName()).isEqualTo("국어");
    assertThat(found.getColor()).isEqualTo(SubjectColor.PURPLE);
  }

  @Test
  @DisplayName("지운 과목은 다시 찾아지지 않고, 같은 이름으로 새로 만들 수 있다")
  void deletedNameCanBeReused() {
    Subject math = save(1L, "수학", SubjectColor.RED);

    subjectRepository.delete(math);
    subjectRepository.flush();

    assertThat(subjectRepository.findByIdAndUserId(math.getId(), 1L)).isEmpty();
    assertThat(subjectRepository.existsByUserIdAndName(1L, "수학")).isFalse();
    save(1L, "수학", SubjectColor.BLUE);
    assertThat(subjectRepository.existsByUserIdAndName(1L, "수학")).isTrue();
  }

  // 과목을 붙인 카드 한 장을 저장합니다. (이슈 #404)
  private Card saveCard(Long userId, Long subjectId) {
    Card card =
        new Card(
            userId,
            "문제집",
            LocalDate.of(2026, 10, 12),
            60,
            List.of(new ChecklistItem("i1", "42쪽")),
            START);
    card.changeSubjectId(subjectId);
    return em.persistAndFlush(card);
  }

  @Test
  @DisplayName("과목을 지울 때 쓰는 clearCardSubject 는 그 과목을 붙인 내 카드만 과목을 비운다 (카드는 그대로)")
  void clearCardSubjectOnlyMine() {
    Subject math = save(1L, "수학", SubjectColor.RED);
    Subject english = save(1L, "영어", SubjectColor.BLUE);
    Card mathCard = saveCard(1L, math.getId());
    Card englishCard = saveCard(1L, english.getId());
    // 2번 사람의 카드가 같은 과목 번호를 가리켜도 건드리지 않습니다.
    Card othersCard = saveCard(2L, math.getId());

    int cleared = subjectRepository.clearCardSubject(1L, math.getId());
    em.clear();

    assertThat(cleared).isEqualTo(1);
    assertThat(em.find(Card.class, mathCard.getId()).getSubjectId()).isNull();
    assertThat(em.find(Card.class, englishCard.getId()).getSubjectId()).isEqualTo(english.getId());
    assertThat(em.find(Card.class, othersCard.getId()).getSubjectId()).isEqualTo(math.getId());
  }
}
