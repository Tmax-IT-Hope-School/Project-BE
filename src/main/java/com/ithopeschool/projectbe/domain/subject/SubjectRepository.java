package com.ithopeschool.projectbe.domain.subject;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// subjects 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 과목이 섞이지 않습니다.
public interface SubjectRepository extends JpaRepository<Subject, Long> {
  @Query("select count(r) > 0 from Routine r where r.userId = :userId and r.subjectId = :subjectId")
  boolean hasRoutines(@Param("userId") Long userId, @Param("subjectId") Long subjectId);

  // 내 과목을 만든 순서(번호 순서)대로 모두 찾습니다. 없으면 빈 목록입니다.
  List<Subject> findByUserIdOrderById(Long userId);

  // 내 과목 중에서 번호로 하나 찾습니다. 없는 번호이거나 남의 과목이면 빈 Optional 입니다.
  Optional<Subject> findByIdAndUserId(Long id, Long userId);

  // 내가 이 이름의 과목을 이미 만들었는지 알려줍니다. (과목을 추가·이름 바꿀 때 씁니다)
  boolean existsByUserIdAndName(Long userId, String name);

  // 이 과목으로 만든 계획이 있는지 알려줍니다. (과목을 지울 수 있는지 볼 때 씁니다)
  // 다른 팀원이 만들 Repository 와 이름이 겹치지 않도록, 계획·기록 쪽 파일을 새로 만들지 않고 여기서 직접 묻습니다.
  // 값은 SQL 문자열에 붙이지 않고 :이름 자리에 넘깁니다.
  @Query(
      "select count(p) > 0 from StudyPlan p where p.userId = :userId and p.subjectId = :subjectId")
  boolean hasPlans(@Param("userId") Long userId, @Param("subjectId") Long subjectId);

  // 이 과목으로 공부한 기록이 있는지 알려줍니다. (과목을 지울 수 있는지 볼 때 씁니다)
  @Query(
      "select count(s) > 0 from StudySession s where s.userId = :userId and s.subjectId = :subjectId")
  boolean hasStudySessions(@Param("userId") Long userId, @Param("subjectId") Long subjectId);

  // 이 과목을 붙인 내 카드들의 과목을 비웁니다. ("과목 없음" 으로) 비운 카드 수를 돌려줍니다. (이슈 #404)
  // 과목을 지울 때 씁니다. 카드는 지우지 않고 과목만 비워서, 카드는 그대로 쓸 수 있습니다.
  // 카드를 하나씩 불러와 고치지 않고 update 한 줄로 한 번에 바꿉니다. 값은 :이름 자리에 넘깁니다.
  @Modifying
  @Query(
      "update Card c set c.subjectId = null where c.userId = :userId and c.subjectId = :subjectId")
  int clearCardSubject(@Param("userId") Long userId, @Param("subjectId") Long subjectId);
}
