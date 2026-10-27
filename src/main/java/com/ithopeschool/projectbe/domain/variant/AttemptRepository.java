package com.ithopeschool.projectbe.domain.variant;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// 답 DB 창구
public interface AttemptRepository extends JpaRepository<Attempt, Long> {

  boolean existsByAssignmentId(Long assignmentId);

  List<Attempt> findByAssignmentIdIn(Collection<Long> assignmentIds);
}
